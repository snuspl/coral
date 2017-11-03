/*
 * Copyright (C) 2017 Seoul National University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.snu.onyx.runtime.executor.data.partition;

import edu.snu.onyx.common.coder.Coder;
import edu.snu.onyx.runtime.executor.data.Block;
import edu.snu.onyx.runtime.executor.data.DataSerializationUtil;
import edu.snu.onyx.runtime.executor.data.HashRange;
import edu.snu.onyx.runtime.executor.data.metadata.BlockMetadata;
import edu.snu.onyx.runtime.executor.data.metadata.FileMetadata;
import edu.snu.onyx.runtime.executor.data.FileArea;

import javax.annotation.concurrent.ThreadSafe;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This class represents a partition which is stored in (local or remote) file.
 */
@ThreadSafe
public final class FilePartition implements Partition {

  private final Coder coder;
  private final String filePath;
  private final FileMetadata metadata;
  private final Queue<BlockMetadata> blockMetadataToCommit;
  private final boolean commitPerBlock;

  public FilePartition(final Coder coder,
                       final String filePath,
                       final FileMetadata metadata) {
    this.coder = coder;
    this.filePath = filePath;
    this.metadata = metadata;
    this.blockMetadataToCommit = new ConcurrentLinkedQueue<>();
    this.commitPerBlock = metadata.isBlockCommitPerWrite();
  }

  /**
   * Writes the serialized data of this partition having a specific hash value as a block to the file
   * where this partition resides.
   *
   * @param serializedData  the serialized data which will become a block.
   * @param elementsInBlock the number of elements in the serialized data.
   * @param hashVal         the hash value of this block.
   * @throws IOException if fail to write.
   */
  private void writeSerializedBlock(final byte[] serializedData,
                                    final long elementsInBlock,
                                    final int hashVal) throws IOException {
    // Reserve a block write and get the metadata.
    final BlockMetadata blockMetadata = metadata.reserveBlock(hashVal, serializedData.length, elementsInBlock);

    try (
        final FileOutputStream fileOutputStream = new FileOutputStream(filePath, true);
        final FileChannel fileChannel = fileOutputStream.getChannel()
    ) {
      // Wrap the given serialized data (but not copy it) and write.
      fileChannel.position(blockMetadata.getOffset());
      final ByteBuffer buf = ByteBuffer.wrap(serializedData);
      fileChannel.write(buf);
    }

    // Commit if needed.
    if (commitPerBlock) {
      metadata.commitBlocks(Collections.singleton(blockMetadata));
    } else {
      blockMetadataToCommit.add(blockMetadata);
    }
  }

  /**
   * Writes {@link Block}s to this partition.
   *
   * @param blocks the {@link Block}s to write.
   * @throws IOException if fail to write.
   */
  @Override
  public List<Long> writeBlocks(final Iterable<Block> blocks) throws IOException {
    final List<Long> blockSizeList = new ArrayList<>();
    // Serialize the given blocks
    try (final ByteArrayOutputStream bytesOutputStream = new ByteArrayOutputStream()) {
      for (final Block block : blocks) {
        final long elementsTotal = DataSerializationUtil.serializeBlock(coder, block, bytesOutputStream);

        // Write the serialized block.
        final byte[] serialized = bytesOutputStream.toByteArray();
        writeSerializedBlock(serialized, elementsTotal, block.getKey());
        blockSizeList.add((long) serialized.length);
        bytesOutputStream.reset();
      }
      commitRemainderMetadata();

      return blockSizeList;
    }
  }

  /**
   * Commits the un-committed block metadata.
   */
  public void commitRemainderMetadata() {
    final List<BlockMetadata> metadataToCommit = new ArrayList<>();
    while (!blockMetadataToCommit.isEmpty()) {
      final BlockMetadata blockMetadata = blockMetadataToCommit.poll();
      if (blockMetadata != null) {
        metadataToCommit.add(blockMetadata);
      }
    }
    metadata.commitBlocks(metadataToCommit);
  }

  /**
   * Retrieves the elements of this partition from the file in a specific hash range and deserializes it.
   *
   * @param hashRange the hash range
   * @return an iterable of deserialized elements.
   * @throws IOException if failed to deserialize.
   */
  @Override
  public Iterable retrieve(final HashRange hashRange) throws IOException {
    // Deserialize the data
    final ArrayList deserializedData = new ArrayList<>();
    try (final FileInputStream fileStream = new FileInputStream(filePath)) {
      for (final BlockMetadata blockMetadata : metadata.getBlockMetadataIterable()) {
        // TODO #463: Support incremental read.
        final int hashVal = blockMetadata.getHashValue();
        if (hashRange.includes(hashVal)) {
          // The hash value of this block is in the range.
          DataSerializationUtil.deserializeBlock(
              blockMetadata.getBlockSize(), blockMetadata.getElementsTotal(),
              coder, fileStream, deserializedData);
        } else {
          // Have to skip this block.
          final long bytesToSkip = blockMetadata.getBlockSize();
          final long skippedBytes = fileStream.skip(bytesToSkip);
          if (skippedBytes != bytesToSkip) {
            throw new IOException("The file stream failed to skip to the next block.");
          }
        }
      }
    }

    return deserializedData;
  }

  /**
   * Retrieves the list of {@link FileArea}s for the specified {@link HashRange}.
   *
   * @param hashRange     the hash range
   * @return list of the file areas
   * @throws IOException if failed to open a file channel
   */
  public List<FileArea> asFileAreas(final HashRange hashRange) throws IOException {
    final List<FileArea> fileAreas = new ArrayList<>();
    for (final BlockMetadata blockMetadata : metadata.getBlockMetadataIterable()) {
      if (hashRange.includes(blockMetadata.getHashValue())) {
        fileAreas.add(new FileArea(filePath, blockMetadata.getOffset(), blockMetadata.getBlockSize()));
      }
    }
    return fileAreas;
  }

  /**
   * Deletes the file that contains this partition data.
   * This method have to be called after all read is completed (or failed).
   *
   * @throws IOException if failed to delete.
   */
  public void deleteFile() throws IOException {
    metadata.deleteMetadata();
    Files.delete(Paths.get(filePath));
  }

  /**
   * Commits this partition to prevent further write.
   */
  @Override
  public void commit() {
    commitRemainderMetadata();
    metadata.commitPartition();
  }
}
