package edu.snu.onyx.runtime.executor.data;

import edu.snu.onyx.common.DirectByteArrayOutputStream;
import edu.snu.onyx.common.coder.Coder;
import edu.snu.onyx.common.ir.vertex.executionproperty.CompressionProperty.Compressor;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility methods for data handling (e.g., (de)serialization).
 */
public final class DataUtil {
  /**
   * Empty constructor.
   */
  private DataUtil() {
    // Private constructor.
  }

  /**
   * Serializes the elements in a non-serialized partition into an output stream.
   *
   * @param coder                  the coder to encode the elements.
   * @param nonSerializedPartition the non-serialized partition to serialize.
   * @param bytesOutputStream      the output stream to write.
   * @return total number of elements in the partition.
   * @throws IOException if fail to serialize.
   */
  public static long serializePartition(final Coder coder,
                                        final NonSerializedPartition nonSerializedPartition,
                                        final ByteArrayOutputStream bytesOutputStream) throws IOException {
    long elementsCount = 0;
    for (final Object element : nonSerializedPartition.getData()) {
      coder.encode(element, bytesOutputStream);
      elementsCount++;
    }

    return elementsCount;
  }

  /**
   * Reads the data of a partition from an input stream and deserializes it.
   *
   * @param elementsInPartition the number of elements in this partition.
   * @param coder               the coder to decode the bytes.
   * @param key                 the key value of the result partition.
   * @param inputStream         the input stream which will return the data in the partition as bytes.
   * @param <K>                 the key type of the partitions.
   * @return the list of deserialized elements.
   * @throws IOException if fail to deserialize.
   */
  public static <K extends Serializable> NonSerializedPartition deserializePartition(final long elementsInPartition,
                                                            final Coder coder,
                                                            final K key,
                                                            final InputStream inputStream) throws IOException {
    final List deserializedData = new ArrayList();
    for (int i = 0; i < elementsInPartition; i++) {
      deserializedData.add(coder.decode(inputStream));
    }
    return new NonSerializedPartition<>(key, deserializedData);
  }

  /**
   * Converts the non-serialized {@link Partition}s in an iterable to serialized {@link Partition}s.
   *
   * @param coder               the coder for serialization.
   * @param partitionsToConvert the partitions to convert.
   * @param <K>                 the key type of the partitions.
   * @return the converted {@link SerializedPartition}s.
   * @throws IOException if fail to convert.
   */
  public static <K extends Serializable> Iterable<SerializedPartition<K>> convertToSerPartitions(
      final Coder coder,
      final Iterable<NonSerializedPartition<K>> partitionsToConvert) throws IOException {
    final List<SerializedPartition<K>> serializedPartitions = new ArrayList<>();
    for (final NonSerializedPartition<K> partitionToConvert : partitionsToConvert) {
      try (final DirectByteArrayOutputStream bytesOutputStream = new DirectByteArrayOutputStream()) {
        final long elementsTotal = serializePartition(coder, partitionToConvert, bytesOutputStream);
        final byte[] serializedBytes = bytesOutputStream.getBufDirectly();
        final int actualLength = bytesOutputStream.getCount();
        serializedPartitions.add(
            new SerializedPartition<>(partitionToConvert.getKey(), elementsTotal, serializedBytes, actualLength));
      }
    }
    return serializedPartitions;
  }

  /**
   * Converts the serialized {@link Partition}s in an iterable to non-serialized {@link Partition}s.
   *
   * @param coder               the coder for deserialization.
   * @param partitionsToConvert the partitions to convert.
   * @param <K>                 the key type of the partitions.
   * @return the converted {@link NonSerializedPartition}s.
   * @throws IOException if fail to convert.
   */
  public static <K extends Serializable> Iterable<NonSerializedPartition<K>> convertToNonSerPartitions(
      final Coder coder,
      final Iterable<SerializedPartition<K>> partitionsToConvert) throws IOException {
    final List<NonSerializedPartition<K>> nonSerializedPartitions = new ArrayList<>();
    for (final SerializedPartition<K> partitionToConvert : partitionsToConvert) {
      final K key = partitionToConvert.getKey();
      try (final ByteArrayInputStream byteArrayInputStream =
               new ByteArrayInputStream(partitionToConvert.getData())) {
        final NonSerializedPartition<K> deserializePartition = deserializePartition(
            partitionToConvert.getElementsTotal(), coder, key, byteArrayInputStream);
        nonSerializedPartitions.add(deserializePartition);
      }
    }
    return nonSerializedPartitions;
  }

  /**
   * Converts a block id to the corresponding file path.
   *
   * @param blockId       the ID of the block.
   * @param fileDirectory the directory of the target block file.
   * @return the file path of the partition.
   */
  public static String blockIdToFilePath(final String blockId,
                                         final String fileDirectory) {
    return fileDirectory + "/" + blockId;
  }

  /**
   * Converts a block id to the corresponding metadata file path.
   *
   * @param blockId       the ID of the block.
   * @param fileDirectory the directory of the target block file.
   * @return the metadata file path of the partition.
   */
  public static String blockIdToMetaFilePath(final String blockId,
                                             final String fileDirectory) {
    return fileDirectory + "/" + blockId + "_meta";
  }

  /**
   * Concatenates an iterable of non-serialized {@link Partition}s into a single iterable of elements.
   *
   * @param partitionsToConcat the partitions to concatenate.
   * @return the concatenated iterable of all elements.
   * @throws IOException if fail to concatenate.
   */
  public static Iterable concatNonSerPartitions(final Iterable<NonSerializedPartition> partitionsToConcat)
      throws IOException {
    final List concatStreamBase = new ArrayList<>();
    Stream<Object> concatStream = concatStreamBase.stream();
    for (final NonSerializedPartition nonSerializedPartition : partitionsToConcat) {
      final Iterable elementsInPartition = nonSerializedPartition.getData();
      concatStream = Stream.concat(concatStream, StreamSupport.stream(elementsInPartition.spliterator(), false));
    }
    return concatStream.collect(Collectors.toList());
  }

  OutputStream createOutputStream(final OutputStream out, final Compressor compressor) throws IOException {
    switch (compressor) {
      case Raw:
        return new BufferedOutputStream(out);
      case Gzip:
        return new GZIPOutputStream(out);
      case LZ4:
        return null; // TODO: add later (maybe adding dependency?)
      default:
        return null;
    }
  }

  InputStream createInputStream(final InputStream in, final Compressor compressor) throws IOException {
    switch (compressor) {
      case Raw:
        return new BufferedInputStream(in);
      case Gzip:
        return new GZIPInputStream(in);
      case LZ4:
        return null; // TODO: add later (maybe adding dependency?)
      default:
        return null;
    }
  }
}
