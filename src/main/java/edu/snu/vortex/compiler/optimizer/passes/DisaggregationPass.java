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

import edu.snu.vortex.compiler.ir.Attributes;
import edu.snu.vortex.compiler.ir.DAG;
import edu.snu.vortex.compiler.ir.Edge;

import java.util.List;
import java.util.Optional;

/**
 * Disaggregated Resources pass for tagging operators.
 */
public final class DisaggregationPass implements Pass {
  public DAG process(final DAG dag) throws Exception {
    dag.doTopological(operator -> {
      final Optional<List<Edge>> inEdges = dag.getInEdgesOf(operator);
      operator.setAttr(Attributes.Key.Placement, Attributes.Placement.Compute);
    });

    dag.getOperators().forEach(operator -> {
      final Optional<List<Edge>> inEdges = dag.getInEdgesOf(operator);
      if (inEdges.isPresent()) {
        inEdges.get().forEach(edge -> {
          if (edge.getType().equals(Edge.Type.O2O)) {
            edge.setAttr(Attributes.Key.EdgeChannel, Attributes.EdgeChannel.Memory);
          } else {
            edge.setAttr(Attributes.Key.EdgeChannel,  Attributes.EdgeChannel.DistributedStorage);
          }
        });
      }
    });
    return dag;
  }
}
