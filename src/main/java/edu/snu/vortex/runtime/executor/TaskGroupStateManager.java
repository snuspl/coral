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
package edu.snu.vortex.runtime.executor;

import edu.snu.vortex.runtime.common.comm.ExecutorMessage;
import edu.snu.vortex.runtime.common.plan.physical.TaskGroup;
import edu.snu.vortex.runtime.common.state.TaskGroupState;
import edu.snu.vortex.runtime.common.state.TaskState;
import edu.snu.vortex.runtime.exception.UnknownExecutionStateException;
import edu.snu.vortex.utils.StateMachine;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the states related to a task group.
 * The methods of this class are synchronized.
 */
// TODO #83: Introduce Task Group Executor
public final class TaskGroupStateManager {
  private static final Logger LOG = Logger.getLogger(TaskGroupStateManager.class.getName());

  private String taskGroupId;

  /**
   * Used to track all task states of this task group, by keeping a map of task ids to their states.
   */
  private final Map<String, TaskState> idToTaskStates;

  /**
   * Used to track task group completion status.
   * All task ids are added to the set when the this task group begins executing.
   * Each task id is removed upon completion,
   * therefore indicating the task group's completion when this set becomes empty.
   */
  private Set<String> currentTaskGroupTaskIds;

  public TaskGroupStateManager() {
    idToTaskStates = new HashMap<>();
    currentTaskGroupTaskIds = new HashSet<>();
  }

  /**
   * Receives a new task group to manage.
   * @param taskGroup to manage.
   */
  public synchronized void manageNewTaskGroup(final TaskGroup taskGroup) {
    idToTaskStates.clear();

    this.taskGroupId = taskGroup.getTaskGroupId();
    onTaskGroupStateChanged(TaskGroupState.State.EXECUTING, null);

    taskGroup.getTaskDAG().getVertices().forEach(task -> {
      currentTaskGroupTaskIds.add(task.getId());
      idToTaskStates.put(task.getId(), new TaskState());
    });
  }

  /**
   * Updates the state of the task group.
   * @param newState of the task group.
   * @param failedTaskIds the ID of the task on which this task group failed if failed, empty otherwise.
   */
  public synchronized void onTaskGroupStateChanged(final TaskGroupState.State newState,
                                                   final Optional<List<String>> failedTaskIds) {
    if (newState == TaskGroupState.State.EXECUTING) {
      LOG.log(Level.FINE, "Executing TaskGroup ID {0}...", taskGroupId);
    } else if (newState == TaskGroupState.State.COMPLETE) {
      LOG.log(Level.FINE, "TaskGroup ID {0} complete!", taskGroupId);
      notifyTaskGroupStateToMaster(taskGroupId, newState, failedTaskIds);
    } else if (newState == TaskGroupState.State.FAILED_RECOVERABLE) {
      LOG.log(Level.FINE, "TaskGroup ID {0} failed (recoverable).", taskGroupId);
      notifyTaskGroupStateToMaster(taskGroupId, newState, failedTaskIds);
    } else if (newState == TaskGroupState.State.FAILED_UNRECOVERABLE) {
      LOG.log(Level.FINE, "TaskGroup ID {0} failed (unrecoverable).", taskGroupId);
      notifyTaskGroupStateToMaster(taskGroupId, newState, failedTaskIds);
    } else {
      throw new IllegalStateException("Illegal state at this point");
    }
  }

  /**
   * Updates the state of a task.
   * Task state changes only occur in executor.
   * @param taskId of the task.
   * @param newState of the task.
   * @return true if this task change results in the current task group completion, false otherwise.
   */
  public synchronized boolean onTaskStateChanged(final String taskId, final TaskState.State newState) {
    final StateMachine taskStateChanged = idToTaskStates.get(taskId).getStateMachine();
    LOG.log(Level.FINE, "Task State Transition: id {0} from {1} to {2}",
        new Object[]{taskGroupId, taskStateChanged.getCurrentState(), newState});
    taskStateChanged.setState(newState);
    if (newState == TaskState.State.COMPLETE) {
      currentTaskGroupTaskIds.remove(taskId);
    }
    return currentTaskGroupTaskIds.isEmpty();
  }

  /**
   * Notifies the change in task group state to master.
   * @param id of the task group.
   * @param newState of the task group.
   * @param failedTaskIds the id of the task that caused this task group to fail, empty otherwise.
   */
  private void notifyTaskGroupStateToMaster(final String id,
                                            final TaskGroupState.State newState,
                                            final Optional<List<String>> failedTaskIds) {
    final ExecutorMessage.TaskGroupStateChangedMsg.Builder taskGroupStateChangedMsg =
        ExecutorMessage.TaskGroupStateChangedMsg.newBuilder();
    taskGroupStateChangedMsg.setTaskGroupId(id);
    taskGroupStateChangedMsg.setState(convertState(newState));

    if (failedTaskIds.isPresent()) {
      taskGroupStateChangedMsg.addAllFailedTaskIds(failedTaskIds.get());
    }

    // TODO #94: Implement Distributed Communicator

    // Send taskGroupStateChangedMsg to master!
  }

  // TODO #164: Cleanup Protobuf Usage
  private ExecutorMessage.TaskGroupStateFromExecutor convertState(final TaskGroupState.State state) {
    switch (state) {
    case READY:
      return ExecutorMessage.TaskGroupStateFromExecutor.READY;
    case EXECUTING:
      return ExecutorMessage.TaskGroupStateFromExecutor.EXECUTING;
    case COMPLETE:
      return ExecutorMessage.TaskGroupStateFromExecutor.COMPLETE;
    case FAILED_RECOVERABLE:
      return ExecutorMessage.TaskGroupStateFromExecutor.FAILED_RECOVERABLE;
    case FAILED_UNRECOVERABLE:
      return ExecutorMessage.TaskGroupStateFromExecutor.FAILED_UNRECOVERABLE;
    default:
      throw new UnknownExecutionStateException(new Exception("This TaskGroupState is unknown: " + state));
    }
  }

  // Tentative
  public void getCurrentTaskGroupExecutionState() {

  }
}
