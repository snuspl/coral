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
package edu.snu.vortex.runtime.common.execplan;

import edu.snu.vortex.compiler.frontend.beam.BoundedSourceVertex;
import edu.snu.vortex.compiler.ir.*;
import edu.snu.vortex.compiler.ir.attributes.AttributesMap;
import edu.snu.vortex.runtime.common.*;
import edu.snu.vortex.runtime.common.RuntimeAttributes;
import edu.snu.vortex.runtime.exception.IllegalVertexOperationException;
import edu.snu.vortex.runtime.exception.UnsupportedAttributeException;

import java.util.*;

/**
 * ExecutionPlanBuilder.
 */
public final class ExecutionPlanBuilder {
  private final List<RuntimeStageBuilder> runtimeStageBuilderList;
  private final Map<String, RuntimeStageBuilder> vertexIdToRuntimeStageBuilderMap;
  private RuntimeStageBuilder stageBuilder;

  /**
   * Builds an {@link ExecutionPlan}.
   */
  public ExecutionPlanBuilder() {
    this.runtimeStageBuilderList = new LinkedList<>();
    this.vertexIdToRuntimeStageBuilderMap = new HashMap<>();
  }

  /**
   * Adds a {@link Vertex} to the execution plan, by adding it to the stage currently being built.
   * The vertices must be added in the order of execution.
   * @param vertex to add.
   */
  public void addVertex(final Vertex vertex) {
    final RuntimeVertex newVertex;

    // TODO #100: Add Vertex Type in IR
    if (vertex instanceof BoundedSourceVertex) {
      newVertex = new RuntimeBoundedSourceVertex((BoundedSourceVertex) vertex,
          convertVertexAttributes(vertex.getAttributes()));
    } else if (vertex instanceof OperatorVertex) {
      newVertex = new RuntimeOperatorVertex((OperatorVertex) vertex, convertVertexAttributes(vertex.getAttributes()));
    } else {
      throw new IllegalVertexOperationException("Supported types: BoundedSourceVertex, OperatorVertex");
    }
    stageBuilder.addRuntimeVertex(newVertex);
    vertexIdToRuntimeStageBuilderMap.putIfAbsent(newVertex.getId(), stageBuilder);
  }

  /**
   * Converts IR's Vertex Attributes to Runtime's attributes.
   * @param irAttributes attributes to convert.
   * @return a map of Runtime Vertex attributes.
   */
  // TODO #000: Must clean up IR and Runtime attributes.
  private Map<RuntimeAttributes.RuntimeVertexAttribute, Object> convertVertexAttributes(
      final AttributesMap irAttributes) {
    final Map<RuntimeAttributes.RuntimeVertexAttribute, Object> runtimeVertexAttributes = new HashMap<>();

    irAttributes.forEachAttr(((irAttributeKey, irAttributeVal) -> {
      switch (irAttributeKey) {
      case Placement:
        final Object runtimeAttributeVal;
        switch (irAttributeVal) {
        case Transient:
          runtimeAttributeVal = RuntimeAttributes.ResourceType.TRANSIENT;
          break;
        case Reserved:
          runtimeAttributeVal = RuntimeAttributes.ResourceType.RESERVED;
          break;
        case Compute:
          runtimeAttributeVal = RuntimeAttributes.ResourceType.COMPUTE;
          break;
        default:
          throw new UnsupportedAttributeException("this IR attribute is not supported");
        }
        runtimeVertexAttributes.put(RuntimeAttributes.RuntimeVertexAttribute.RESOURCE_TYPE, runtimeAttributeVal);
      default:
        throw new UnsupportedAttributeException("this IR attribute is not supported");
      }
    }));
    irAttributes.forEachIntAttr((irAttributeKey, irAttributeVal) -> {
      switch (irAttributeKey) {
        case Parallelism:
          runtimeVertexAttributes.put(RuntimeAttributes.RuntimeVertexAttribute.PARALLELISM, 0);
          break;
        default:
          throw new UnsupportedAttributeException("this IR attribute is not supported");
      }
    });
    return runtimeVertexAttributes;
  }

  /**
   * Converts IR's Edge Attributes to Runtime's attributes.
   * @param irAttributes attributes to convert.
   * @return a map of Runtime Edge attributes.
   */
  // TODO #000: Must clean up IR and Runtime attributes.
  private Map<RuntimeAttributes.RuntimeEdgeAttribute, Object> convertEdgeAttributes(
      final AttributesMap irAttributes) {
    final Map<RuntimeAttributes.RuntimeEdgeAttribute, Object> runtimeEdgeAttributes = new HashMap<>();

    irAttributes.forEachAttr(((irAttributeKey, irAttributeVal) -> {
      switch (irAttributeKey) {
      case EdgePartitioning:
        final Object partitioningAttrVal;
        switch (irAttributeVal) {
        case Hash:
          partitioningAttrVal = RuntimeAttributes.Partition.HASH;
          break;
        case Range:
          partitioningAttrVal = RuntimeAttributes.Partition.RANGE;
          break;
        default:
          throw new UnsupportedAttributeException("this IR attribute is not supported");
        }
        runtimeEdgeAttributes.put(RuntimeAttributes.RuntimeEdgeAttribute.PARTITION, partitioningAttrVal);
      case EdgeChannel:
        final Object channelAttrVal;
        switch (irAttributeVal) {
        case Memory:
          channelAttrVal = RuntimeAttributes.Channel.LOCAL_MEM;
          break;
        case TCPPipe:
          channelAttrVal = RuntimeAttributes.Channel.TCP;
          break;
        case File:
          channelAttrVal = RuntimeAttributes.Channel.FILE;
          break;
        case DistributedStorage:
          channelAttrVal = RuntimeAttributes.Channel.DISTR_STORAGE;
          break;
        default:
          throw new UnsupportedAttributeException("this IR attribute is not supported");
        }
        runtimeEdgeAttributes.put(RuntimeAttributes.RuntimeEdgeAttribute.CHANNEL, channelAttrVal);
      default:
        throw new UnsupportedAttributeException("this IR attribute is not supported");
      }
    }));
    return runtimeEdgeAttributes;
  }

  /**
   * Connects two {@link RuntimeVertex} that belong to the same stage, using the information given in {@link Edge}.
   * @param edge to use for the connection.
   */
  public void connectStageInternalVertices(final Edge edge) {
    final String srcRuntimeVertexId = RuntimeIdGenerator.generateRuntimeVertexId(edge.getSrc().getId());
    final String dstRuntimeVertexId = RuntimeIdGenerator.generateRuntimeVertexId(edge.getDst().getId());

    stageBuilder.connectInternalRuntimeVertices(srcRuntimeVertexId, dstRuntimeVertexId);
  }

  /**
   * Connects two {@link RuntimeVertex} that belong to different stages, using the information given in {@link Edge}.
   * @param edge to use for the connection.
   */
  public void connectStageBoundaryVertices(final Edge edge) {
    final String srcRuntimeVertexId = RuntimeIdGenerator.generateRuntimeVertexId(edge.getSrc().getId());
    final String dstRuntimeVertexId = RuntimeIdGenerator.generateRuntimeVertexId(edge.getDst().getId());

    final RuntimeEdge newEdge = new RuntimeEdge(edge.getId(), convertEdgeAttributes(edge.getAttributes()),
        srcRuntimeVertexId, dstRuntimeVertexId);
    vertexIdToRuntimeStageBuilderMap.get(srcRuntimeVertexId).connectRuntimeStages(srcRuntimeVertexId, newEdge);
    vertexIdToRuntimeStageBuilderMap.get(dstRuntimeVertexId).connectRuntimeStages(dstRuntimeVertexId, newEdge);
  }

  /**
   * Creates and adds a new {@link RuntimeStage} to the execution plan.
   * The {@link RuntimeStage} that was previously created is finalized.
   * Stages must be created in the order of execution.
   */
  public void createNewStage() {
    if (!stageBuilder.isEmpty()) {
      runtimeStageBuilderList.add(stageBuilder);
    }
    stageBuilder = new RuntimeStageBuilder();
  }

  /**
   * Builds and returns the {@link ExecutionPlan} to be submitted to Runtime.
   * @return the execution plan.
   */
  public ExecutionPlan build() {
    return new ExecutionPlan(RuntimeIdGenerator.generateExecutionPlanId(), runtimeStageBuilderList);
  }
}
