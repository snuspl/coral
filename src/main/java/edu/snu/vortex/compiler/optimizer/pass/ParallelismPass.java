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
package edu.snu.vortex.compiler.optimizer.pass;

import edu.snu.vortex.common.dag.DAGBuilder;
import edu.snu.vortex.compiler.ir.IREdge;
import edu.snu.vortex.compiler.ir.IRVertex;
import edu.snu.vortex.compiler.ir.SourceVertex;
import edu.snu.vortex.common.dag.DAG;
import edu.snu.vortex.compiler.ir.executionproperty.ExecutionProperty;
import edu.snu.vortex.compiler.ir.executionproperty.vertex.ParallelismProperty;
import edu.snu.vortex.runtime.executor.datatransfer.data_communication_pattern.Broadcast;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Optimization pass for tagging parallelism execution property.
 */
public final class ParallelismPass implements StaticOptimizationPass {
  @Override
  public DAG<IRVertex, IREdge> apply(final DAG<IRVertex, IREdge> dag) {
    // Propagate forward source parallelism
    dag.topologicalDo(vertex -> {
      try {
        final List<IREdge> inEdges = dag.getIncomingEdgesOf(vertex).stream()
            .filter(edge -> !Boolean.TRUE.equals(edge.get(ExecutionProperty.Key.IsSideInput)))
            .collect(Collectors.toList());
        if (inEdges.isEmpty() && vertex instanceof SourceVertex) {
          final SourceVertex sourceVertex = (SourceVertex) vertex;
          vertex.setProperty(ParallelismProperty.of(sourceVertex.getReaders(1).size()));
        } else if (!inEdges.isEmpty()) {
          final OptionalInt parallelism = inEdges.stream()
              // No reason to propagate via Broadcast edges, as the data streams that will use the broadcasted data
              // as a sideInput will have their own number of parallelism
              .filter(edge -> !Broadcast.class.equals(edge.get(ExecutionProperty.Key.DataCommunicationPattern)))
              .mapToInt(edge -> (Integer) edge.getSrc().get(ExecutionProperty.Key.Parallelism))
              .max();
          if (parallelism.isPresent()) {
            vertex.setProperty(ParallelismProperty.of(parallelism.getAsInt()));
          }
        } else {
          throw new RuntimeException("There is a non-source vertex that doesn't have any inEdges other than SideInput");
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    final DAGBuilder<IRVertex, IREdge> builder = new DAGBuilder<>(dag);
    return builder.build();
  }
}
