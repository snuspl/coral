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
package edu.snu.onyx.compiler.optimizer.pass.compiletime.annotating;

import edu.snu.onyx.compiler.ir.IREdge;
import edu.snu.onyx.compiler.ir.IRVertex;
import edu.snu.onyx.common.dag.DAG;
import edu.snu.onyx.compiler.ir.executionproperty.ExecutionProperty;
import edu.snu.onyx.compiler.ir.executionproperty.edge.DataStoreProperty;
import edu.snu.onyx.compiler.ir.executionproperty.vertex.ExecutorPlacementProperty;
import edu.snu.onyx.runtime.executor.data.stores.LocalFileStore;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pado pass for tagging edges with DataStore ExecutionProperty.
 */
public final class PadoEdgeDataStorePass extends AnnotatingPass {
  public PadoEdgeDataStorePass() {
    super(ExecutionProperty.Key.DataStore, Stream.of(
        ExecutionProperty.Key.ExecutorPlacement
    ).collect(Collectors.toSet()));
  }

  @Override
  public DAG<IRVertex, IREdge> apply(final DAG<IRVertex, IREdge> dag) {
    dag.getVertices().forEach(vertex -> {
      final List<IREdge> inEdges = dag.getIncomingEdgesOf(vertex);
      if (!inEdges.isEmpty()) {
        inEdges.forEach(edge -> {
          if (fromTransientToReserved(edge) || fromReservedToTransient(edge)) {
            edge.setProperty(DataStoreProperty.of(LocalFileStore.class));
          }
        });
      }
    });
    return dag;
  }

  /**
   * checks if the edge is from transient container to a reserved container.
   * @param irEdge edge to check.
   * @return whether or not the edge satisfies the condition.
   */
  static boolean fromTransientToReserved(final IREdge irEdge) {
    return irEdge.getSrc().getProperty(ExecutionProperty.Key.ExecutorPlacement)
        .equals(ExecutorPlacementProperty.TRANSIENT)
        && irEdge.getDst().getProperty(ExecutionProperty.Key.ExecutorPlacement)
        .equals(ExecutorPlacementProperty.RESERVED);
  }

  /**
   * checks if the edge is from reserved container to a transient container.
   * @param irEdge edge to check.
   * @return whether or not the edge satisfies the condition.
   */
  static boolean fromReservedToTransient(final IREdge irEdge) {
    return irEdge.getSrc().getProperty(ExecutionProperty.Key.ExecutorPlacement)
        .equals(ExecutorPlacementProperty.RESERVED)
        && irEdge.getDst().getProperty(ExecutionProperty.Key.ExecutorPlacement)
        .equals(ExecutorPlacementProperty.TRANSIENT);
  }
}
