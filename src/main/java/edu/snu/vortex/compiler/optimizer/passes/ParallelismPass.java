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

import edu.snu.vortex.compiler.ir.IREdge;
import edu.snu.vortex.compiler.ir.IRVertex;
import edu.snu.vortex.compiler.ir.OperatorVertex;
import edu.snu.vortex.compiler.ir.SourceVertex;
import edu.snu.vortex.compiler.ir.attribute.Attribute;
import edu.snu.vortex.utils.dag.DAG;

import java.util.Collections;
import java.util.List;

/**
 * Optimization pass for tagging parallelism attributes.
 */
public final class ParallelismPass implements Pass {
  @Override
  public DAG<IRVertex, IREdge> process(final DAG<IRVertex, IREdge> dag) throws Exception {
    // Propagate forward source parallelism
    dag.topologicalDo(vertex -> {
      try {
        final List<IREdge> inEdges = dag.getIncomingEdgesOf(vertex);
        if (inEdges.isEmpty() && vertex instanceof SourceVertex) {
          final SourceVertex sourceVertex = (SourceVertex) vertex;
          vertex.setAttr(Attribute.IntegerKey.Parallelism, sourceVertex.getReaders(1).size());
        } else if (!inEdges.isEmpty()) {
          Integer parallelism = inEdges.stream()
              // let's be conservative and take the min value so that
              // the sources can support the desired parallelism in the back-propagation phase
              .mapToInt(edge -> edge.getSrc().getAttr(Attribute.IntegerKey.Parallelism)).min().getAsInt();
          vertex.setAttr(Attribute.IntegerKey.Parallelism, parallelism);
        } else {
          throw new RuntimeException("Weird situation: there is a non-source vertex that doesn't have any inEdges");
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // Propagate backward with OneToOne edges, fixing conflicts between different sources with different parallelism
    final List<IRVertex> reverseTopologicalSort = getReverseTopologicalSort(dag);
    for (final IRVertex vertex : reverseTopologicalSort) {
      if (vertex instanceof SourceVertex) {
        final SourceVertex sourceVertex = (SourceVertex) vertex;
        final int desiredParallelism = sourceVertex.getAttr(Attribute.IntegerKey.Parallelism);
        final int actualParallelism = sourceVertex.getReaders(desiredParallelism).size();
        if (sourceVertex.getReaders(desiredParallelism).size() != desiredParallelism) {
          throw new RuntimeException("Source " + vertex.toString() + " cannot support back-propagated parallelism:"
              + "desired " + desiredParallelism + ", actual" + actualParallelism);
        }
      } else if (vertex instanceof OperatorVertex) {
        final int parallelism = vertex.getAttr(Attribute.IntegerKey.Parallelism);
        dag.getIncomingEdgesOf(vertex).stream()
            .filter(edge -> edge.getAttr(Attribute.Key.CommunicationPattern) == Attribute.OneToOne)
            .map(IREdge::getSrc)
            .forEach(src -> {
              src.setAttr(Attribute.IntegerKey.Parallelism, parallelism);
            });
      } else {
        throw new UnsupportedOperationException("Unknown vertex type: " + vertex.toString());
      }
    }

    // Check all OneToOne edges have src/dst with the same parallelism
    // TODO #22: DAG Integrity Check
    dag.topologicalDo(vertex -> {
      final List<IREdge> inEdges = dag.getIncomingEdgesOf(vertex);
      inEdges.stream()
          .filter(edge -> edge.getAttr(Attribute.Key.CommunicationPattern) == Attribute.OneToOne)
          .forEach(edge -> {
            final int srcParallelism = edge.getSrc().getAttr(Attribute.IntegerKey.Parallelism);
            final int dstParallelism = edge.getDst().getAttr(Attribute.IntegerKey.Parallelism);
            if (srcParallelism != dstParallelism) {
              throw new RuntimeException(edge.toString() + " is OneToOne, but src/dst parallelisms differ");
            }
          });
    });
    return dag;
  }

  private List<IRVertex> getReverseTopologicalSort(final DAG<IRVertex, IREdge> dag) {
    final List<IRVertex> reverseTopologicalSort = dag.getTopologicalSort();
    Collections.reverse(dag.getTopologicalSort());
    return reverseTopologicalSort;
  }
}
