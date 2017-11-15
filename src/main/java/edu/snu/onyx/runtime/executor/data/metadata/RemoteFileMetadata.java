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
package edu.snu.onyx.runtime.executor.data.metadata;

import edu.snu.onyx.runtime.common.grpc.CommonMessage;
import edu.snu.onyx.runtime.executor.RpcToMaster;
import edu.snu.onyx.runtime.master.RuntimeMaster;
import edu.snu.onyx.runtime.master.grpc.MasterRemoteBlockMessage;

import javax.annotation.concurrent.ThreadSafe;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a metadata for a remote file partition.
 * Because the data is stored in a remote file and globally accessed by multiple nodes,
 * each access (create - write - close, read, or deletion) for a partition needs one instance of this metadata.
 * Concurrent write for a single file is supported, but each writer in different executor
 * has to have separate instance of this class.
 * It supports concurrent write for a single partition, but each writer has to have separate instance of this class.
 * These accesses are judiciously synchronized by the metadata server in master.
 */
@ThreadSafe
public final class RemoteFileMetadata extends FileMetadata {

  private final String partitionId;
  private final String executorId;
  private final RpcToMaster rpcToMaster;
  private volatile Iterable<BlockMetadata> blockMetadataIterable;

  /**
   * Opens a partition metadata.
   * TODO #410: Implement metadata caching for the RemoteFileMetadata.
   *
   * @param commitPerBlock     whether commit every block write or not.
   * @param partitionId        the id of the partition.
   * @param executorId         the id of the executor.
   * @param rpcToMaster the connection for sending messages to master.
   */
  public RemoteFileMetadata(final boolean commitPerBlock,
                            final String partitionId,
                            final String executorId,
                            final RpcToMaster rpcToMaster) {
    super(commitPerBlock);
    this.partitionId = partitionId;
    this.executorId = executorId;
    this.rpcToMaster = rpcToMaster;
  }

  /**
   * Reserves the region for a block and get the metadata for the block.
   *
   * @see FileMetadata#reserveBlock(int, int, long).
   */
  @Override
  public synchronized BlockMetadata reserveBlock(final int hashValue,
                                                 final int blockSize,
                                                 final long elementsTotal) throws IOException {
    // Convert the block metadata to a block metadata message (without offset).
    final CommonMessage.BlockMetadata blockMetadata = CommonMessage.BlockMetadata.newBuilder()
        .setHashValue(hashValue)
        .setBlockSize(blockSize)
        .setNumElements(elementsTotal)
        .build();

    // Send the block metadata to the metadata server in the master and ask where to store the block.
    final MasterRemoteBlockMessage.RemoteBlockReservationResponse response =
        rpcToMaster.newRemoteBlockSyncStub().reserveRemoteBlock(
            MasterRemoteBlockMessage.RemoteBlockReservationRequest.newBuilder()
                .setExecutorId(executorId)
                .setPartitionId(partitionId)
                .setBlockMetadata(blockMetadata)
                .build()
        );

    if (!response.hasPositionToWrite()) {
      // TODO #463: Support incremental read. Check whether this partition is committed in the metadata server side.
      throw new IOException("Cannot append the block metadata.");
    }
    final int blockIndex = response.getBlockIdx();
    final long positionToWrite = response.getPositionToWrite();
    return new BlockMetadata(blockIndex, hashValue, blockSize, positionToWrite, elementsTotal);
  }

  /**
   * Notifies that some blocks are written.
   *
   * @see FileMetadata#commitBlocks(Iterable).
   */
  @Override
  public synchronized void commitBlocks(final Iterable<BlockMetadata> blockMetadataToCommit) {
    final List<Integer> blockIndices = new ArrayList<>();
    blockMetadataToCommit.forEach(blockMetadata -> {
      blockMetadata.setCommitted();
      blockIndices.add(blockMetadata.getBlockIdx());
    });

    // Notify that these blocks are committed to the metadata server.
    rpcToMaster.newRemoteBlockSyncStub().commitRemoteBlock(
        MasterRemoteBlockMessage.RemoteBlockCommitRequest.newBuilder()
            .setPartitionId(partitionId)
            .addAllBlockIdx(blockIndices)
            .build()
    );
  }

  /**
   * Gets a iterable containing the block metadata of corresponding partition.
   *
   * @see FileMetadata#getBlockMetadataIterable().
   */
  @Override
  public synchronized Iterable<BlockMetadata> getBlockMetadataIterable() throws IOException {
    if (blockMetadataIterable == null) {
      blockMetadataIterable = getBlockMetadataFromServer();
    }
    return blockMetadataIterable;
  }

  /**
   * @see FileMetadata#deleteMetadata().
   */
  @Override
  public void deleteMetadata() throws IOException {
    rpcToMaster.newRemoteBlockSyncStub().removeRemoteBlock(
        MasterRemoteBlockMessage.RemoteBlockRemovalRequest.newBuilder()
            .setPartitionId(partitionId)
            .build()
    );
  }

  /**
   * Notifies that all writes are finished for the partition corresponding to this metadata.
   * Subscribers waiting for the data of the target partition are notified when the partition is committed.
   * Also, further subscription about a committed partition will not blocked but get the data in it and finished.
   */
  @Override
  public synchronized void commitPartition() {
    // TODO #463: Support incremental write. Close the "ClosableBlockingIterable".
  }

  /**
   * Gets the iterable of block metadata from the metadata server.
   * If write for this partition is not ended, the metadata server will publish the committed blocks to this iterable.
   *
   * @return the received file metadata.
   * @throws IOException if fail to get the metadata.
   */
  private Iterable<BlockMetadata> getBlockMetadataFromServer() throws IOException {
    final List<BlockMetadata> blockMetadataList = new ArrayList<>();

    // Ask the metadata server in the master for the metadata
    final MasterRemoteBlockMessage.RemoteBlockMetadataResponse response =
        rpcToMaster.newRemoteBlockSyncStub().askRemoteBlockMetadata(
            MasterRemoteBlockMessage.RemoteBlockMetadataRequest.newBuilder()
                .setExecutorId(executorId)
                .setPartitionId(partitionId)
                .build()
        );

    if (response.hasState()) {
      // Response has an exception state.
      throw new IOException(new Throwable(
          "Cannot get the metadata of partition " + partitionId + " from the metadata server: "
              + "The partition state is " + RuntimeMaster.convertPartitionState(response.getState())));
    }

    // Construct the metadata from the response.
    final List<CommonMessage.BlockMetadata> blockMetadataMsgList = response.getBlockMetadataList();
    for (int blockIdx = 0; blockIdx < blockMetadataMsgList.size(); blockIdx++) {
      final CommonMessage.BlockMetadata blockMetadataMsg = blockMetadataMsgList.get(blockIdx);
      if (!blockMetadataMsg.hasOffset()) {
        throw new IOException(new Throwable(
            "The metadata of a block in the " + partitionId + " does not have offset value."));
      }
      blockMetadataList.add(new BlockMetadata(
          blockIdx,
          blockMetadataMsg.getHashValue(),
          blockMetadataMsg.getBlockSize(),
          blockMetadataMsg.getOffset(),
          blockMetadataMsg.getNumElements()
      ));
    }

    // TODO #463: Support incremental read. Return a "ClosableBlockingIterable".
    return blockMetadataList;
  }
}
