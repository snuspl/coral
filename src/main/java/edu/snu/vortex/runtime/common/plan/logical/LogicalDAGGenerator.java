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
package edu.snu.vortex.runtime.common.plan.logical;


import edu.snu.vortex.compiler.frontend.beam.BoundedSourceVertex;
import edu.snu.vortex.compiler.ir.IREdge;
import edu.snu.vortex.compiler.ir.IRVertex;
import edu.snu.vortex.compiler.ir.OperatorVertex;
import edu.snu.vortex.compiler.ir.attribute.Attribute;
import edu.snu.vortex.runtime.exception.IllegalVertexOperationException;
import edu.snu.vortex.runtime.utils.RuntimeAttributeConverter;
import edu.snu.vortex.utils.dag.DAG;
import edu.snu.vortex.utils.dag.DAGBuilder;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static edu.snu.vortex.compiler.ir.attribute.Attribute.Local;
import static edu.snu.vortex.compiler.ir.attribute.Attribute.SideInput;

/**
 * A function that converts an IR DAG to runtime's logical DAG.
 * @param <I> Input type of IREdge.
 * @param <O> Output type of IREdge.
 */
public final class LogicalDAGGenerator<I, O>
    implements Function<DAG<IRVertex, IREdge<I, O>>, DAG<RuntimeStage, StageEdge>> {

  /**
   * The IR DAG to convert.
   */
  private DAG<IRVertex, IREdge<I, O>> irDAG;

  /**
   * The builder for the logical DAG.
   */
  private final DAGBuilder<RuntimeStage, StageEdge> logicalDAGBuilder;

  private final HashMap<IRVertex, Integer> vertexStageNumHashMap;
  private final List<List<IRVertex>> vertexListForEachStage;
  private final HashMap<Integer, Integer> stageDependencyMap;
  private final AtomicInteger stageNumber;

  public LogicalDAGGenerator() {
    logicalDAGBuilder = new DAGBuilder<>();
    vertexStageNumHashMap = new HashMap<>();
    vertexListForEachStage = new ArrayList<>();
    stageDependencyMap = new HashMap<>();
    stageNumber = new AtomicInteger(0);
  }

  @Override
  public DAG<RuntimeStage, StageEdge> apply(final DAG<IRVertex, IREdge<I, O>> inputDAG) {
    this.irDAG = inputDAG;

    stagePartitionIrDAG();
    convertToLogicalDAG();

    return logicalDAGBuilder.build();
  }

  private void convertToLogicalDAG() {
    final Set<RuntimeVertex> currentStageVertices = new HashSet<>();
    final Set<StageEdgeBuilder> currentStageIncomingEdges = new HashSet<>();
    final Map<String, RuntimeVertex> irVertexIdToRuntimeVertexMap = new HashMap<>();
    final Map<String, RuntimeStage> runtimeVertexIdToRuntimeStageMap = new HashMap<>();

    for (final List<IRVertex> stageVertices : vertexListForEachStage) {
      // Create a new runtime stage builder.
      final RuntimeStageBuilder runtimeStageBuilder = new RuntimeStageBuilder();

      // For each vertex in the stage,
      for (final IRVertex irVertex : stageVertices) {

        // Convert the vertex into a runtime vertex, and add to the logical DAG
        final RuntimeVertex runtimeVertex = convertVertex(irVertex);
        runtimeStageBuilder.addRuntimeVertex(runtimeVertex);
        currentStageVertices.add(runtimeVertex);
        irVertexIdToRuntimeVertexMap.put(irVertex.getId(), runtimeVertex);

        // Connect all the incoming edges for the runtime vertex
        final Set<IREdge<I, O>> inEdges = irDAG.getIncomingEdges(irVertex);
        final Optional<Set<IREdge<I, O>>> inEdgeList = (inEdges == null) ? Optional.empty() : Optional.of(inEdges);
        inEdgeList.ifPresent(edges -> edges.forEach(irEdge -> {
          final RuntimeVertex srcRuntimeVertex =
              irVertexIdToRuntimeVertexMap.get(irEdge.getSrcIRVertex().getId());
          final RuntimeVertex dstRuntimeVertex =
              irVertexIdToRuntimeVertexMap.get(irEdge.getDstIRVertex().getId());

          if (srcRuntimeVertex == null || dstRuntimeVertex == null) {
            throw new IllegalVertexOperationException("unable to locate srcRuntimeVertex and/or dstRuntimeVertex");
          }

          // either the edge is within the stage
          if (currentStageVertices.contains(srcRuntimeVertex) && currentStageVertices.contains(dstRuntimeVertex)) {
            runtimeStageBuilder.connectInternalRuntimeVertices(srcRuntimeVertex, dstRuntimeVertex);

          // or the edge is from another stage
          } else {
            final RuntimeStage srcRuntimeStage = runtimeVertexIdToRuntimeStageMap.get(srcRuntimeVertex.getId());

            if (srcRuntimeStage == null) {
              throw new IllegalVertexOperationException(
                  "srcRuntimeVertex and/or dstRuntimeVertex are not yet added to the ExecutionPlanBuilder");
            }

            final StageEdgeBuilder newEdgeBuilder = new StageEdgeBuilder(irEdge.getId());
            newEdgeBuilder.setEdgeAttributes(RuntimeAttributeConverter.convertEdgeAttributes(irEdge.getAttributes()));
            newEdgeBuilder.setSrcRuntimeVertex(srcRuntimeVertex);
            newEdgeBuilder.setDstRuntimeVertex(dstRuntimeVertex);
            newEdgeBuilder.setSrcRuntimeStage(srcRuntimeStage);
            currentStageIncomingEdges.add(newEdgeBuilder);
          }
        }));
      }

      // If this runtime stage contains at least one vertex, build it!
      if (!runtimeStageBuilder.isEmpty()) {
        final RuntimeStage currentStage = runtimeStageBuilder.build();
        logicalDAGBuilder.addVertex(currentStage);

        // Add this stage as the destination stage for all the incoming edges.
        currentStageIncomingEdges.forEach(stageEdgeBuilder -> {
          stageEdgeBuilder.setDstRuntimeStage(currentStage);
          final StageEdge stageEdge = stageEdgeBuilder.build();
          logicalDAGBuilder.connectVertices(stageEdge);
        });
        currentStageIncomingEdges.clear();

        currentStageVertices.forEach(runtimeVertex ->
            runtimeVertexIdToRuntimeStageMap.put(runtimeVertex.getId(), currentStage));
        currentStageVertices.clear();
      }
    }
  }

  private RuntimeVertex convertVertex(final IRVertex irVertex) {
    final RuntimeVertex newVertex;

    // TODO #100: Add irVertex Type in IR
    if (irVertex instanceof BoundedSourceVertex) {
      newVertex = new RuntimeBoundedSourceVertex((BoundedSourceVertex) irVertex,
          RuntimeAttributeConverter.convertVertexAttributes(irVertex.getAttributes()));
    } else if (irVertex instanceof OperatorVertex) {
      newVertex = new RuntimeOperatorVertex((OperatorVertex) irVertex,
          RuntimeAttributeConverter.convertVertexAttributes(irVertex.getAttributes()));
    } else {
      throw new IllegalVertexOperationException("Supported types: BoundedSourceVertex, OperatorVertex");
    }
    return newVertex;
  }

  private void stagePartitionIrDAG() {
    final List<IRVertex> test = irDAG.getTopologicalSort();
    // First, traverse the DAG topologically to add each vertices to a list associated with each of the stage number.
    irDAG.topologicalDo(vertex -> {
      final Set<IREdge<I, O>> inEdges = irDAG.getIncomingEdges(vertex);
      final Optional<Set<IREdge<I, O>>> inEdgeList = (inEdges == null) ? Optional.empty() : Optional.of(inEdges);

      if (!inEdgeList.isPresent()) { // If Source vertex
        createNewStage(vertex);
      } else {
        final Optional<List<IREdge>> inEdgesForStage = inEdgeList.map(e -> e.stream()
            .filter(edge -> edge.getType().equals(IREdge.Type.OneToOne))
            .filter(edge -> edge.getAttr(Attribute.Key.ChannelDataPlacement).equals(Local))
            .filter(edge -> edge.getAttr(Attribute.Key.SideInput) != SideInput) // remove with TODO #132: Refactor DAG
            .filter(edge -> edge.getSrc().getAttributes().equals(edge.getDst().getAttributes()))
            .filter(edge -> vertexStageNumHashMap.containsKey(edge.getSrc()))
            .collect(Collectors.toList()));
        final Optional<IREdge> edgeToConnect = inEdgesForStage.map(edges -> edges.stream().filter(edge ->
            !stageDependencyMap.containsKey(vertexStageNumHashMap.get(edge.getSrc()))).findFirst())
            .orElse(Optional.empty());

        if (!inEdgesForStage.isPresent() || inEdgesForStage.get().isEmpty() || !edgeToConnect.isPresent()) {
          // when we cannot connect vertex in other stages
          createNewStage(vertex);
          inEdgeList.ifPresent(edges -> edges.forEach(inEdge -> {
            stageDependencyMap.put(vertexStageNumHashMap.get(inEdge.getSrc()), stageNumber.get());
          }));
        } else {
          final IRVertex irVertexToConnect = edgeToConnect.get().getSrcIRVertex();
          vertexStageNumHashMap.put(vertex, vertexStageNumHashMap.get(irVertexToConnect));
          final Optional<List<IRVertex>> list =
              vertexListForEachStage.stream().filter(l -> l.contains(irVertexToConnect)).findFirst();
          list.ifPresent(lst -> {
            vertexListForEachStage.remove(lst);
            lst.add(vertex);
            vertexListForEachStage.add(lst);
          });
        }
      }
    });
  }

  private void createNewStage(final IRVertex irVertex) {
    vertexStageNumHashMap.put(irVertex, stageNumber.getAndIncrement());
    final List<IRVertex> newList = new ArrayList<>();
    newList.add(irVertex);
    vertexListForEachStage.add(newList);
  }
}
