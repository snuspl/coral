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
package edu.snu.vortex.runtime.executor.data;

import edu.snu.vortex.client.JobConf;
import edu.snu.vortex.common.coder.Coder;
import edu.snu.vortex.compiler.ir.Element;
import edu.snu.vortex.compiler.ir.attribute.Attribute;
import edu.snu.vortex.runtime.common.RuntimeIdGenerator;
import edu.snu.vortex.runtime.common.comm.ControlMessage;
import edu.snu.vortex.runtime.exception.PartitionFetchException;
import edu.snu.vortex.runtime.exception.PartitionWriteException;
import edu.snu.vortex.runtime.exception.UnsupportedPartitionStoreException;
import edu.snu.vortex.runtime.executor.PersistentConnectionToMaster;
import edu.snu.vortex.runtime.executor.data.partition.Partition;
import edu.snu.vortex.runtime.master.RuntimeMaster;
import org.apache.reef.tang.annotations.Parameter;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executor-side partition manager.
 */
@ThreadSafe
public final class PartitionManagerWorker {
  private static final Logger LOG = LoggerFactory.getLogger(PartitionManagerWorker.class.getName());

  private final String executorId;

  private final MemoryStore memoryStore;

  private final LocalFileStore localFileStore;

  private final RemoteFileStore remoteFileStore;

  private final PersistentConnectionToMaster persistentConnectionToMaster;

  private final ConcurrentMap<String, Coder> runtimeEdgeIdToCoder;

  private final PartitionTransferPeer partitionTransferPeer;

  @Inject
  private PartitionManagerWorker(@Parameter(JobConf.ExecutorId.class) final String executorId,
                                 final MemoryStore memoryStore,
                                 final LocalFileStore localFileStore,
                                 final RemoteFileStore remoteFileStore,
                                 final PersistentConnectionToMaster persistentConnectionToMaster,
                                 final PartitionTransferPeer partitionTransferPeer) {
    this.executorId = executorId;
    this.memoryStore = memoryStore;
    this.localFileStore = localFileStore;
    this.remoteFileStore = remoteFileStore;
    this.persistentConnectionToMaster = persistentConnectionToMaster;
    this.runtimeEdgeIdToCoder = new ConcurrentHashMap<>();
    this.partitionTransferPeer = partitionTransferPeer;
  }

  /**
   * Return the coder for the specified runtime edge.
   *
   * @param runtimeEdgeId id of the runtime edge.
   * @return the corresponding coder.
   */
  public Coder getCoder(final String runtimeEdgeId) {
    final Coder coder = runtimeEdgeIdToCoder.get(runtimeEdgeId);
    if (coder == null) {
      throw new RuntimeException("No coder is registered for " + runtimeEdgeId);
    }
    return coder;
  }

  /**
   * Register a coder for runtime edge.
   *
   * @param runtimeEdgeId id of the runtime edge.
   * @param coder         the corresponding coder.
   */
  public void registerCoder(final String runtimeEdgeId, final Coder coder) {
    runtimeEdgeIdToCoder.putIfAbsent(runtimeEdgeId, coder);
  }

  /**
   * Remove the partition from store.
   *
   * @param partitionId    of the partition to remove.
   * @param partitionStore tha the partition is stored.
   * @return whether the partition is removed or not.
   */
  public boolean removePartition(final String partitionId,
                                 final Attribute partitionStore) {
    LOG.info("RemovePartition: {}", partitionId);
    final PartitionStore store = getPartitionStore(partitionStore);
    final boolean exist;
    try {
      exist = store.removePartition(partitionId).get();
    } catch (final InterruptedException | ExecutionException e) {
      throw new PartitionFetchException(e);
    }

    if (exist) {
      persistentConnectionToMaster.getMessageSender().send(
          ControlMessage.Message.newBuilder()
              .setId(RuntimeIdGenerator.generateMessageId())
              .setType(ControlMessage.MessageType.PartitionStateChanged)
              .setPartitionStateChangedMsg(
                  ControlMessage.PartitionStateChangedMsg.newBuilder()
                      .setExecutorId(executorId)
                      .setPartitionId(partitionId)
                      .setState(ControlMessage.PartitionStateFromExecutor.REMOVED)
                      .build())
              .build());
    }

    return exist;
  }

  /**
   * Store partition to the target {@code PartitionStore}.
   * Invariant: This should be invoked only once per partitionId.
   *
   * @param partitionId    of the partition.
   * @param data           of the partition.
   * @param partitionStore to store the partition.
   */
  public void putPartition(final String partitionId,
                           final Iterable<Element> data,
                           final Attribute partitionStore) {
    LOG.info("PutPartition: {}", partitionId);
    final PartitionStore store = getPartitionStore(partitionStore);

    try {
      store.putDataAsPartition(partitionId, data).get();
    } catch (final Exception e) {
      throw new PartitionWriteException(e);
    }

    final ControlMessage.PartitionStateChangedMsg.Builder partitionStateChangedMsgBuilder =
        ControlMessage.PartitionStateChangedMsg.newBuilder().setExecutorId(executorId)
            .setPartitionId(partitionId)
            .setState(ControlMessage.PartitionStateFromExecutor.COMMITTED);

    persistentConnectionToMaster.getMessageSender().send(
        ControlMessage.Message.newBuilder()
            .setId(RuntimeIdGenerator.generateMessageId())
            .setType(ControlMessage.MessageType.PartitionStateChanged)
            .setPartitionStateChangedMsg(partitionStateChangedMsgBuilder.build())
            .build());
  }

  /**
   * Store a hashed partition to the target {@code PartitionStore}.
   * Each block (an {@link Iterable} of elements} has a single hash value.
   * Invariant: This should be invoked only once per partitionId.
   *
   * @param partitionId    of the partition.
   * @param srcIRVertexId  IRVertex gof the source task.
   * @param hashedData     of the partition.
   * @param partitionStore to store the partition.
   */
  public void putHashedPartition(final String partitionId,
                                 final String srcIRVertexId,
                                 final Iterable<Iterable<Element>> hashedData,
                                 final Attribute partitionStore) {
    LOG.info("PutSortedPartition: {}", partitionId);
    final PartitionStore store = getPartitionStore(partitionStore);
    final Iterable<Long> blockSizeInfo;

    try {
      blockSizeInfo = store.putHashedDataAsPartition(partitionId, hashedData).get().orElse(Collections.emptyList());
    } catch (final Exception e) {
      throw new PartitionWriteException(e);
    }

    final ControlMessage.PartitionStateChangedMsg.Builder partitionStateChangedMsgBuilder =
        ControlMessage.PartitionStateChangedMsg.newBuilder().setExecutorId(executorId)
            .setPartitionId(partitionId)
            .setState(ControlMessage.PartitionStateFromExecutor.COMMITTED);

    // TODO #355 Support I-file write: send block size information only when it is requested.
    partitionStateChangedMsgBuilder.addAllBlockSizeInfo(blockSizeInfo);
    partitionStateChangedMsgBuilder.setSrcVertexId(srcIRVertexId);

    persistentConnectionToMaster.getMessageSender().send(
        ControlMessage.Message.newBuilder()
            .setId(RuntimeIdGenerator.generateMessageId())
            .setType(ControlMessage.MessageType.PartitionStateChanged)
            .setPartitionStateChangedMsg(partitionStateChangedMsgBuilder.build())
            .build());
  }

  /**
   * Retrieves whole data from the stored partition.
   * Unlike putPartition, this can be invoked multiple times per partitionId (maybe due to failures).
   * Here, we first check if we have the partition here, and then try to fetch the partition from a remote worker.
   *
   * @param partitionId    of the partition.
   * @param runtimeEdgeId  id of the runtime edge that corresponds to the partition.
   * @param partitionStore for the data storage.
   * @return a {@link CompletableFuture} for the partition.
   */
  public CompletableFuture<Iterable<Element>> retrieveDataFromPartition(final String partitionId,
                                                                        final String runtimeEdgeId,
                                                                        final Attribute partitionStore) {
    LOG.info("retrieveDataFromPartition: {}", partitionId);
    final CompletableFuture<Iterable<Element>> future = new CompletableFuture<>();

    final PartitionStore store = getPartitionStore(partitionStore);

    // First, try to fetch the partition from local PartitionStore.
    // If it doesn't have the partition, this future will be completed to Optional.empty()
    final CompletableFuture<Optional<Partition>> localPartition = store.retrieveDataFromPartition(partitionId);

    localPartition.thenAccept(optionalPartition -> {
      if (optionalPartition.isPresent()) {
        // Partition resides in this evaluator!
        try {
          future.complete(optionalPartition.get().asIterable());
        } catch (final IOException e) {
          future.completeExceptionally(new PartitionFetchException(e));
        }
      } else {
        // We don't have the partition here...
        requestPartitionInRemoteWorker(partitionId, runtimeEdgeId, partitionStore, 0, Integer.MAX_VALUE)
            .thenAccept(partition -> future.complete(partition));
      }
    });

    return future;
  }

  /**
   * Retrieves data in a specific hash value range from the stored partition.
   * Unlike putPartition, this can be invoked multiple times per partitionId (maybe due to failures).
   * Here, we first check if we have the partition here, and then try to fetch the data from a remote worker.
   *
   * @param partitionId       of the partition.
   * @param runtimeEdgeId     id of the runtime edge that corresponds to the partition.
   * @param partitionStore    for the data storage.
   * @param hashRangeStartVal of the hash range (included in the range).
   * @param hashRangeEndVal   of the hash range (excluded from the range).
   * @return a {@link CompletableFuture} for the partition.
   */
  public CompletableFuture<Iterable<Element>> retrieveDataFromPartition(final String partitionId,
                                                                        final String runtimeEdgeId,
                                                                        final Attribute partitionStore,
                                                                        final int hashRangeStartVal,
                                                                        final int hashRangeEndVal) {
    LOG.info("retrieveDataFromPartition: {}", partitionId);
    final CompletableFuture<Iterable<Element>> future = new CompletableFuture<>();

    final PartitionStore store = getPartitionStore(partitionStore);
    final CompletableFuture<Optional<Partition>> localPartition =
        store.retrieveDataFromPartition(partitionId, hashRangeStartVal, hashRangeEndVal);
    localPartition.thenAccept(optionalPartition -> {
      if (optionalPartition.isPresent()) {
        // Partition resides in this evaluator!
        try {
          future.complete(optionalPartition.get().asIterable());
        } catch (final IOException e) {
          future.completeExceptionally(new PartitionFetchException(e));
        }
      } else {
        // We don't have the partition here...
        requestPartitionInRemoteWorker(partitionId, runtimeEdgeId, partitionStore, hashRangeStartVal, hashRangeEndVal)
            .thenAccept(partition -> future.complete(partition));
      }
    });

    return future;
  }

  /**
   * Requests data in a specific hash value range from a partition which resides in a remote worker asynchronously.
   * If the hash value range is [0, int.max), it will retrieve the whole data from the partition.
   *
   * @param partitionId       of the partition.
   * @param runtimeEdgeId     id of the runtime edge that corresponds to the partition.
   * @param partitionStore    for the data storage.
   * @param hashRangeStartVal of the hash range (included in the range).
   * @param hashRangeEndVal   of the hash range (excluded from the range).
   * @return the {@link CompletableFuture} of the partition.
   */
  private CompletableFuture<Iterable<Element>> requestPartitionInRemoteWorker(final String partitionId,
                                                                              final String runtimeEdgeId,
                                                                              final Attribute partitionStore,
                                                                              final int hashRangeStartVal,
                                                                              final int hashRangeEndVal) {
    // We don't have the partition here...
    if (partitionStore == Attribute.RemoteFile) {
      LOG.warn("The target partition {} is not found in the remote storage. "
          + "Maybe the storage is not mounted or linked properly.", partitionId);
    }
    // Let's see if a remote worker has it
    // Ask Master for the location
    final CompletableFuture<ControlMessage.Message> responseFromMasterFuture =
        persistentConnectionToMaster.getMessageSender().request(
            ControlMessage.Message.newBuilder()
                .setId(RuntimeIdGenerator.generateMessageId())
                .setType(ControlMessage.MessageType.RequestPartitionLocation)
                .setRequestPartitionLocationMsg(
                    ControlMessage.RequestPartitionLocationMsg.newBuilder()
                        .setExecutorId(executorId)
                        .setPartitionId(partitionId)
                        .build())
                .build());
    // responseFromMasterFuture is a CompletableFuture, and PartitionTransferPeer#fetch returns a CompletableFuture.
    // Using thenCompose so that fetching partition data starts after getting response from master.
    return responseFromMasterFuture.thenCompose(responseFromMaster -> {
      assert (responseFromMaster.getType() == ControlMessage.MessageType.PartitionLocationInfo);
      final ControlMessage.PartitionLocationInfoMsg partitionLocationInfoMsg =
          responseFromMaster.getPartitionLocationInfoMsg();
      if (!partitionLocationInfoMsg.hasOwnerExecutorId()) {
        throw new PartitionFetchException(new Throwable(
            "Partition " + partitionId + " not found both in the local storage and the remote storage: The"
                + "partition state is " + RuntimeMaster.convertPartitionState(partitionLocationInfoMsg.getState())));
      }
      // This is the executor id that we wanted to know
      final String remoteWorkerId = partitionLocationInfoMsg.getOwnerExecutorId();
      return partitionTransferPeer.fetch(
          remoteWorkerId, partitionId, runtimeEdgeId, partitionStore, hashRangeStartVal, hashRangeEndVal);
    });
  }

  private PartitionStore getPartitionStore(final Attribute partitionStore) {
    switch (partitionStore) {
      case Memory:
        return memoryStore;
      case LocalFile:
        return localFileStore;
      case RemoteFile:
        return remoteFileStore;
      default:
        throw new UnsupportedPartitionStoreException(new Exception(partitionStore + " is not supported."));
    }
  }
}
