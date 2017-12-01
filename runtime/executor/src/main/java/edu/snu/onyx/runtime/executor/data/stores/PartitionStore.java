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
package edu.snu.onyx.runtime.executor.data.stores;

import edu.snu.onyx.common.exception.PartitionFetchException;
import edu.snu.onyx.common.exception.PartitionWriteException;
import edu.snu.onyx.runtime.common.data.HashRange;
import edu.snu.onyx.runtime.executor.data.NonSerializedBlock;

import java.util.List;
import java.util.Optional;

/**
 * Interface for partition placement.
 */
public interface PartitionStore {
  /**
   * Creates a new partition.
   * A stale data created by previous failed task should be handled during the creation of new partition.
   *
   * @param partitionId the ID of the partition to create.
   * @throws PartitionWriteException for any error occurred while trying to create a partition.
   *         (This exception will be thrown to the scheduler
   *          through {@link edu.snu.onyx.runtime.executor.Executor} and
   *          have to be handled by the scheduler with fault tolerance mechanism.)
   */
  void createPartition(String partitionId) throws PartitionWriteException;

  /**
   * Retrieves elements in a specific {@link HashRange} from a partition.
   * If the target partition is not committed yet, the requester may "subscribe" the further data until it is committed.
   *
   * @param partitionId of the target partition.
   * @param hashRange   the hash range.
   * @return the result elements from the target partition (if the target partition exists).
   * @throws PartitionFetchException for any error occurred while trying to fetch a partition.
   *         (This exception will be thrown to the scheduler
   *          through {@link edu.snu.onyx.runtime.executor.Executor} and
   *          have to be handled by the scheduler with fault tolerance mechanism.)
   */
  Optional<Iterable> getElements(String partitionId,
                                 HashRange hashRange) throws PartitionFetchException;

  /**
   * Saves an iterable of data blocks to a partition.
   * If the partition exists already, appends the data to it.
   * This method supports concurrent write.
   * If the data is needed to be "incrementally" written (and read),
   * each block can be committed right after being written by using {@code commitPerBlock}.
   * Invariant: This method may not support concurrent write for a single partition.
   *            Only one thread have to write at once.
   *
   * @param partitionId    of the partition.
   * @param blocks         to save to a partition.
   * @param commitPerBlock whether commit every block write or not.
   * @return the size of the data per block (only when the data is serialized).
   * @throws PartitionWriteException for any error occurred while trying to write a partition.
   *         (This exception will be thrown to the scheduler
   *          through {@link edu.snu.onyx.runtime.executor.Executor} and
   *          have to be handled by the scheduler with fault tolerance mechanism.)
   */
  Optional<List<Long>> putBlocks(String partitionId,
                                 Iterable<NonSerializedBlock> blocks,
                                 boolean commitPerBlock) throws PartitionWriteException;

  /**
   * Notifies that all writes for a partition is end.
   * Subscribers waiting for the data of the target partition are notified when the partition is committed.
   * Also, further subscription about a committed partition will not blocked but get the data in it and finished.
   *
   * @param partitionId of the partition.
   * @throws PartitionWriteException if fail to commit.
   *         (This exception will be thrown to the scheduler
   *          through {@link edu.snu.onyx.runtime.executor.Executor} and
   *          have to be handled by the scheduler with fault tolerance mechanism.)
   */
  void commitPartition(String partitionId) throws PartitionWriteException;

  /**
   * Removes a partition of data.
   *
   * @param partitionId of the partition.
   * @return whether the partition exists or not.
   * @throws PartitionFetchException for any error occurred while trying to remove a partition.
   *         (This exception will be thrown to the scheduler
   *          through {@link edu.snu.onyx.runtime.executor.Executor} and
   *          have to be handled by the scheduler with fault tolerance mechanism.)
   */
  Boolean removePartition(String partitionId);
}
