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

import edu.snu.vortex.common.dag.DAG;
import edu.snu.vortex.common.dag.DAGBuilder;
import edu.snu.vortex.compiler.frontend.Coder;
import edu.snu.vortex.compiler.ir.IREdge;
import edu.snu.vortex.compiler.ir.IRVertex;
import edu.snu.vortex.compiler.ir.OperatorVertex;
import edu.snu.vortex.compiler.ir.Transform;
import edu.snu.vortex.compiler.ir.attribute.Attribute;
import edu.snu.vortex.runtime.TestUtil;
import edu.snu.vortex.runtime.common.RuntimeAttribute;
import edu.snu.vortex.runtime.common.comm.ControlMessage;
import edu.snu.vortex.runtime.common.message.MessageSender;
import edu.snu.vortex.runtime.common.plan.logical.LogicalDAGGenerator;
import edu.snu.vortex.runtime.common.plan.logical.Stage;
import edu.snu.vortex.runtime.common.plan.logical.StageEdge;
import edu.snu.vortex.runtime.common.plan.physical.PhysicalDAGGenerator;
import edu.snu.vortex.runtime.common.plan.physical.PhysicalPlan;
import edu.snu.vortex.runtime.common.plan.physical.PhysicalStage;
import edu.snu.vortex.runtime.common.plan.physical.PhysicalStageEdge;
import edu.snu.vortex.runtime.common.state.TaskGroupState;
import edu.snu.vortex.runtime.master.resource.ContainerManager;
import edu.snu.vortex.runtime.master.resource.ExecutorRepresenter;
import edu.snu.vortex.runtime.master.resource.ResourceSpecification;
import edu.snu.vortex.runtime.master.scheduler.*;
import org.apache.reef.driver.context.ActiveContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.*;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Tests the fault tolerance mechanism implemented in {@link BatchScheduler}.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(ContainerManager.class)
public final class FaultToleranceTest {
  private static final int TEST_TIMEOUT_MS = 1000;
  private static final int MAX_SCHEDULE_ATTEMPT = 5;

  private DAGBuilder<IRVertex, IREdge> irDAGBuilder;
  private SchedulingPolicy schedulingPolicy;
  private Scheduler scheduler;
  private PartitionManagerMaster partitionManagerMaster;
  private PendingTaskGroupQueue pendingTaskGroupQueue;
  private final Map<String, ExecutorRepresenter> executorRepresenterMap = new HashMap<>();
  private final Map<String, ExecutorRepresenter> failedExecutorRepresenterMap = new HashMap<>();
  private ContainerManager containerManager = mock(ContainerManager.class);
  private final MessageSender<ControlMessage.Message> mockMsgSender = mock(MessageSender.class);

  @Before
  public void setUp() {
    executorRepresenterMap.clear();
    failedExecutorRepresenterMap.clear();
    when(containerManager.getExecutorRepresenterMap()).thenReturn(executorRepresenterMap);
    when(containerManager.getFailedExecutorRepresenterMap()).thenReturn(failedExecutorRepresenterMap);

    irDAGBuilder = new DAGBuilder<>();
    partitionManagerMaster = new PartitionManagerMaster();
    pendingTaskGroupQueue = new PendingTaskGroupQueue();
    schedulingPolicy = new RoundRobinSchedulingPolicy(containerManager, TEST_TIMEOUT_MS);
    scheduler = new BatchScheduler(partitionManagerMaster, schedulingPolicy, pendingTaskGroupQueue);

    final ActiveContext activeContext = mock(ActiveContext.class);
    Mockito.doThrow(new RuntimeException()).when(activeContext).close();

    final ResourceSpecification computeSpec = new ResourceSpecification(RuntimeAttribute.Compute, 1, 0);
    final ExecutorRepresenter a3 = new ExecutorRepresenter("a3", computeSpec, mockMsgSender, activeContext);
    final ExecutorRepresenter a2 = new ExecutorRepresenter("a2", computeSpec, mockMsgSender, activeContext);
    final ExecutorRepresenter a1 = new ExecutorRepresenter("a1", computeSpec, mockMsgSender, activeContext);

    final ResourceSpecification storageSpec = new ResourceSpecification(RuntimeAttribute.Storage, 1, 0);
    final ExecutorRepresenter b2 = new ExecutorRepresenter("b2", storageSpec, mockMsgSender, activeContext);
    final ExecutorRepresenter b1 = new ExecutorRepresenter("b1", storageSpec, mockMsgSender, activeContext);

    executorRepresenterMap.put(a1.getExecutorId(), a1);
    executorRepresenterMap.put(a2.getExecutorId(), a2);
    executorRepresenterMap.put(a3.getExecutorId(), a3);
    executorRepresenterMap.put(b1.getExecutorId(), b1);
    executorRepresenterMap.put(b2.getExecutorId(), b2);

    failedExecutorRepresenterMap.put(a1.getExecutorId(), a1);
    failedExecutorRepresenterMap.put(a2.getExecutorId(), a2);
    failedExecutorRepresenterMap.put(a3.getExecutorId(), a3);
    failedExecutorRepresenterMap.put(b1.getExecutorId(), b1);
    failedExecutorRepresenterMap.put(b2.getExecutorId(), b2);

    // Add compute nodes
    scheduler.onExecutorAdded(a3.getExecutorId());
    scheduler.onExecutorAdded(a2.getExecutorId());
    scheduler.onExecutorAdded(a1.getExecutorId());

    // Add storage nodes
    scheduler.onExecutorAdded(b2.getExecutorId());
    scheduler.onExecutorAdded(b1.getExecutorId());
  }

  @Test
  public void testSimpleJob() {
    final JobStateManager jobStateManager;
    final Transform t = mock(Transform.class);
    final IRVertex v1 = new OperatorVertex(t);
    v1.setAttr(Attribute.IntegerKey.Parallelism, 3);
    v1.setAttr(Attribute.Key.Placement, Attribute.Compute);
    irDAGBuilder.addVertex(v1);

    final IRVertex v2 = new OperatorVertex(t);
    v2.setAttr(Attribute.IntegerKey.Parallelism, 2);
    v2.setAttr(Attribute.Key.Placement, Attribute.Compute);
    irDAGBuilder.addVertex(v2);

    final IRVertex v3 = new OperatorVertex(t);
    v3.setAttr(Attribute.IntegerKey.Parallelism, 4);
    v3.setAttr(Attribute.Key.Placement, Attribute.Compute);
    irDAGBuilder.addVertex(v3);

    final IREdge e1 = new IREdge(IREdge.Type.ScatterGather, v1, v2, Coder.DUMMY_CODER);
    e1.setAttr(Attribute.Key.ChannelDataPlacement, Attribute.Local);
    e1.setAttr(Attribute.Key.CommunicationPattern, Attribute.ScatterGather);
    irDAGBuilder.connectVertices(e1);

    final IREdge e2 = new IREdge(IREdge.Type.ScatterGather, v2, v3, Coder.DUMMY_CODER);
    e2.setAttr(Attribute.Key.ChannelDataPlacement, Attribute.File);
    e2.setAttr(Attribute.Key.CommunicationPattern, Attribute.ScatterGather);
    irDAGBuilder.connectVertices(e2);

    final DAG<IRVertex, IREdge> irDAG = irDAGBuilder.buildWithoutSourceSinkCheck();
    final DAG<Stage, StageEdge> logicalDAG = irDAG.convert(new LogicalDAGGenerator());
    final DAG<PhysicalStage, PhysicalStageEdge> physicalDAG = logicalDAG.convert(new PhysicalDAGGenerator());

    jobStateManager = scheduler.scheduleJob(
        new PhysicalPlan("SimpleJob", physicalDAG), partitionManagerMaster, MAX_SCHEDULE_ATTEMPT);

    // Wait upto 2 seconds for task groups to be scheduled.
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    final Set<String> taskGroupIdsForFailingExecutor = new HashSet<>();
    final Set<String> otherTaskGroupIds = new HashSet<>();
    executorRepresenterMap.forEach((id, executor) -> {
      if (id.equals("a1")) {
        taskGroupIdsForFailingExecutor.addAll(executor.getRunningTaskGroups());
      } else {
        otherTaskGroupIds.addAll(executor.getRunningTaskGroups());
      }
    });

    scheduler.onExecutorRemoved("a1");

    // There are 2 executors, a2 and a3 left.
    taskGroupIdsForFailingExecutor.forEach(failedTaskGroupId -> {
      final Enum state =
          jobStateManager.getTaskGroupState(failedTaskGroupId).getStateMachine().getCurrentState();
      assertTrue(state == TaskGroupState.State.READY || state == TaskGroupState.State.FAILED_RECOVERABLE);
    });

    otherTaskGroupIds.forEach(taskGroupId ->
        TestUtil.sendTaskGroupStateEventToScheduler(scheduler, containerManager,
            taskGroupId, TaskGroupState.State.COMPLETE, null));

    taskGroupIdsForFailingExecutor.forEach(failedTaskGroupId -> {
      final Enum state =
          jobStateManager.getTaskGroupState(failedTaskGroupId).getStateMachine().getCurrentState();
      assertTrue(state == TaskGroupState.State.EXECUTING);
      TestUtil.sendTaskGroupStateEventToScheduler(scheduler, containerManager,
          failedTaskGroupId, TaskGroupState.State.COMPLETE, null);
    });

    // Wait upto 2 seconds for the 2nd stage's task groups to be scheduled.
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    taskGroupIdsForFailingExecutor.clear();
    otherTaskGroupIds.clear();

    // The 2nd stage will complete without trouble.
    executorRepresenterMap.forEach((id, executor) -> otherTaskGroupIds.addAll(executor.getRunningTaskGroups()));
    otherTaskGroupIds.forEach(taskGroupId ->
        TestUtil.sendTaskGroupStateEventToScheduler(scheduler, containerManager,
            taskGroupId, TaskGroupState.State.COMPLETE, null));

    // Wait upto 2 seconds for the 3rd stage's task groups to be scheduled.
//    try {
//      Thread.sleep(2000);
//    } catch (InterruptedException e) {
//      e.printStackTrace();
//    }
//
//    executorRepresenterMap.forEach((id, executor) -> {
//      if (id.equals("a2")) {
//        taskGroupIdsForFailingExecutor.addAll(executor.getRunningTaskGroups());
//      } else {
//        otherTaskGroupIds.addAll(executor.getRunningTaskGroups());
//      }
//    });
//
//    otherTaskGroupIds.forEach(taskGroupId -> {
//      final Enum state =
//          jobStateManager.getTaskGroupState(taskGroupId).getStateMachine().getCurrentState();
//      assertTrue(state == TaskGroupState.State.READY || state == TaskGroupState.State.FAILED_RECOVERABLE);
//    });
//
//    // There is 1 executor, a3 left.
//    taskGroupIdsForFailingExecutor.forEach(failedTaskGroupId -> {
//      TestUtil.sendTaskGroupStateEventToScheduler(scheduler, containerManager,
//          failedTaskGroupId, TaskGroupState.State.FAILED_RECOVERABLE,
//          TaskGroupState.RecoverableFailureCause.INPUT_READ_FAILURE);
//      final Enum state =
//          jobStateManager.getTaskGroupState(failedTaskGroupId).getStateMachine().getCurrentState();
//      assertTrue(state == TaskGroupState.State.READY || state == TaskGroupState.State.FAILED_RECOVERABLE);
//    });
//
//    // Since this is an input read failure, other task groups in the stage must be made failed_recoverable as well.
//    otherTaskGroupIds.forEach(taskGroupId -> {
//      final Enum state =
//          jobStateManager.getTaskGroupState(taskGroupId).getStateMachine().getCurrentState();
//      assertTrue(state == TaskGroupState.State.READY || state == TaskGroupState.State.FAILED_RECOVERABLE);
//    });
  }
}