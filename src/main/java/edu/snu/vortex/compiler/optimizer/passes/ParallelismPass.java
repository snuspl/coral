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
package edu.snu.vortex.compiler.optimizer.passes;

import edu.snu.vortex.common.dag.DAGBuilder;
import edu.snu.vortex.compiler.ir.IREdge;
import edu.snu.vortex.compiler.ir.IRVertex;
import edu.snu.vortex.compiler.ir.SourceVertex;
import edu.snu.vortex.common.dag.DAG;
import edu.snu.vortex.compiler.ir.attribute.ExecutionFactor;
import edu.snu.vortex.compiler.ir.attribute.edge.DataCommunicationPattern;
import edu.snu.vortex.compiler.ir.attribute.vertex.Parallelism;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Optimization pass for tagging parallelism attributes.
 */
public final class ParallelismPass implements StaticOptimizationPass {
  @Override
  public DAG<IRVertex, IREdge> process(final DAG<IRVertex, IREdge> dag) throws Exception {
    // Propagate forward source parallelism
    dag.topologicalDo(vertex -> {
      try {
        final List<IREdge> inEdges = dag.getIncomingEdgesOf(vertex).stream()
            .filter(edge -> !Boolean.TRUE.equals(edge.getBooleanAttr(ExecutionFactor.Type.IsSideInput)))
            .collect(Collectors.toList());
        if (inEdges.isEmpty() && vertex instanceof SourceVertex) {
          final SourceVertex sourceVertex = (SourceVertex) vertex;
          vertex.setAttr(Parallelism.of(sourceVertex.getReaders(1).size()));
        } else if (!inEdges.isEmpty()) {
          final OptionalInt parallelism = inEdges.stream()
              // No reason to propagate via Broadcast edges, as the data streams that will use the broadcasted data
              // as a sideInput will have their own number of parallelism
              .filter(edge -> !edge.getStringAttr(ExecutionFactor.Type.DataCommunicationPattern)
                  .equals(DataCommunicationPattern.BROADCAST))
              .mapToInt(edge -> edge.getSrc().getIntegerAttr(ExecutionFactor.Type.Parallelism))
              .max();
          if (parallelism.isPresent()) {
            vertex.setAttr(Parallelism.of(parallelism.getAsInt()));
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
