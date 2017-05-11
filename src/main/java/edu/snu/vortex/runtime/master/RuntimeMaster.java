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
package edu.snu.vortex.runtime.master;

import edu.snu.vortex.runtime.common.RuntimeAttribute;
import edu.snu.vortex.runtime.common.comm.ControlMessage;
import edu.snu.vortex.runtime.common.message.MessageContext;
import edu.snu.vortex.runtime.common.message.MessageEnvironment;
import edu.snu.vortex.runtime.common.message.MessageListener;
import edu.snu.vortex.runtime.common.message.local.LocalMessageDispatcher;
import edu.snu.vortex.runtime.common.message.local.LocalMessageEnvironment;
import edu.snu.vortex.runtime.common.state.TaskGroupState;
import edu.snu.vortex.runtime.exception.IllegalMessageException;
import edu.snu.vortex.runtime.exception.UnknownExecutionStateException;
import edu.snu.vortex.runtime.master.resourcemanager.LocalResourceManager;
import edu.snu.vortex.runtime.master.resourcemanager.ResourceManager;
import edu.snu.vortex.runtime.common.plan.logical.ExecutionPlan;
import edu.snu.vortex.runtime.common.plan.logical.Stage;
import edu.snu.vortex.runtime.common.plan.logical.StageEdge;
import edu.snu.vortex.runtime.common.plan.physical.PhysicalDAGGenerator;
import edu.snu.vortex.runtime.common.plan.physical.PhysicalPlan;
import edu.snu.vortex.runtime.master.scheduler.BatchScheduler;
import edu.snu.vortex.runtime.master.scheduler.Scheduler;
import edu.snu.vortex.utils.dag.DAG;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import static edu.snu.vortex.runtime.common.RuntimeAttribute.*;

/**
 * Runtime Master is the central controller of Runtime.
 * Compiler submits an {@link ExecutionPlan} to Runtime Master to execute a job.
 * Runtime Master handles:
 *    a) Physical conversion of a job's DAG into a physical plan.
 *    b) Scheduling the job with {@link BatchScheduler}.
 *    c) (Please list others done by Runtime Master as features are added).
 */
public final class RuntimeMaster {
  private static final Logger LOG = Logger.getLogger(RuntimeMaster.class.getName());

  // We should have a new way of (ex. configuration) these settings.
  private static final int DEFAULT_NUM_EXECUTOR = 2;
  private static final int DEFAULT_EXECUTOR_CAPACITY = 4;

  private final Scheduler scheduler;
  private final ResourceManager resourceManager;
  private final LocalMessageDispatcher localMessageDispatcher;
  private final MessageEnvironment masterMessageEnvironment;
  private final BlockManagerMaster blockManagerMaster;
  private ExecutionStateManager executionStateManager;

  public RuntimeMaster(final RuntimeAttribute schedulerType) {
    switch (schedulerType) {
    case Batch:
      this.scheduler = new BatchScheduler(RuntimeAttribute.RoundRobin, 2000);
      break;
    default:
      throw new RuntimeException("Unknown scheduler type");
    }
    this.localMessageDispatcher = new LocalMessageDispatcher();
    this.masterMessageEnvironment =
        new LocalMessageEnvironment(MessageEnvironment.MASTER_COMMUNICATION_ID, localMessageDispatcher);
    masterMessageEnvironment.setupListener(MessageEnvironment.MASTER_MESSAGE_RECEIVER, new MasterMessageReceiver());
    this.blockManagerMaster = new BlockManagerMaster();
    this.resourceManager =
        new LocalResourceManager(scheduler, masterMessageEnvironment, localMessageDispatcher, blockManagerMaster);
    initializeResources();
  }

  /**
   * Initialize a default amount of resources by requesting to the Resource Manager.
   */
  private void initializeResources() {
    final Set<RuntimeAttribute> completeSetOfResourceType =
        new HashSet<>(Arrays.asList(Transient, Reserved, Compute, Storage));
    completeSetOfResourceType.forEach(resourceType -> {
      for (int i = 0; i < DEFAULT_NUM_EXECUTOR; i++) {
        resourceManager.requestExecutor(resourceType, DEFAULT_EXECUTOR_CAPACITY);
      }
    });
  }

  /**
   * Submits the {@link ExecutionPlan} to Runtime.
   * @param executionPlan to execute.
   * @param dagDirectory the directory to which JSON representation of the plan is saved
   */
  public void execute(final ExecutionPlan executionPlan, final String dagDirectory) {
    final PhysicalPlan physicalPlan = generatePhysicalPlan(executionPlan, dagDirectory);
    try {
      executionStateManager = scheduler.scheduleJob(physicalPlan, blockManagerMaster);
      while (!executionStateManager.checkJobCompletion()) {
        // Check every 3 seconds for job completion.
        Thread.sleep(3000);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Generates the {@link PhysicalPlan} to be executed.
   * @param executionPlan that should be converted to a physical plan
   * @param dagDirectory the directory to which JSON representation of the plan is saved
   * @return {@link PhysicalPlan} to execute.
   */
  private PhysicalPlan generatePhysicalPlan(final ExecutionPlan executionPlan, final String dagDirectory) {
    final DAG<Stage, StageEdge> logicalDAG = executionPlan.getRuntimeStageDAG();
    logicalDAG.storeJSON(dagDirectory, "plan-logical", "logical execution plan");

    final PhysicalPlan physicalPlan = new PhysicalPlan(executionPlan.getId(),
        logicalDAG.convert(new PhysicalDAGGenerator()));
    physicalPlan.getStageDAG().storeJSON(dagDirectory, "plan-physical", "physical execution plan");
    return physicalPlan;
  }

  /**
   * Handler for messages received by Master.
   */
  private final class MasterMessageReceiver implements MessageListener<ControlMessage.Message> {

    @Override
    public void onSendMessage(final ControlMessage.Message message) {
      switch (message.getType()) {
      case TaskGroupStateChanged:
        final ControlMessage.TaskGroupStateChangedMsg taskGroupStateChangedMsg = message.getTaskStateChangedMsg();
        scheduler.onTaskGroupStateChanged(taskGroupStateChangedMsg.getExecutorId(),
            taskGroupStateChangedMsg.getTaskGroupId(),
            convertState(taskGroupStateChangedMsg.getState()),
            taskGroupStateChangedMsg.getFailedTaskIdsList());
        break;
      case BlockStateChanged:
        break;
      case RequestBlock:
        break;
      default:
        throw new IllegalMessageException(
            new Exception("This message should not be received by Master :" + message.getType()));
      }
    }

    @Override
    public void onRequestMessage(final ControlMessage.Message message, final MessageContext messageContext) {
    }
  }

  // TODO #164: Cleanup Protobuf Usage
  private TaskGroupState.State convertState(final ControlMessage.TaskGroupStateFromExecutor state) {
    switch (state) {
    case READY:
      return TaskGroupState.State.READY;
    case EXECUTING:
      return TaskGroupState.State.EXECUTING;
    case COMPLETE:
      return TaskGroupState.State.COMPLETE;
    case FAILED_RECOVERABLE:
      return TaskGroupState.State.FAILED_RECOVERABLE;
    case FAILED_UNRECOVERABLE:
      return TaskGroupState.State.FAILED_UNRECOVERABLE;
    default:
      throw new UnknownExecutionStateException(new Exception("This TaskGroupState is unknown: " + state));
    }
  }
}
