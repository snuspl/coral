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
package edu.snu.vortex.compiler.optimizer.pass.compiletime.annotating;

import edu.snu.vortex.common.dag.DAG;
import edu.snu.vortex.compiler.ir.IREdge;
import edu.snu.vortex.compiler.ir.IRVertex;
import edu.snu.vortex.compiler.ir.executionproperty.ExecutionProperty;
import edu.snu.vortex.compiler.ir.executionproperty.edge.DataFlowModelProperty;

/**
 * Pass for initiating IREdge DataFlowModel ExecutionProperty with default values.
 * Pull is the default value.
 */
public final class DefaultEdgeDataFlowModelPass extends AnnotatingPass {
  public static final String SIMPLE_NAME = "DefaultEdgeDataFlowModelPass";

  public DefaultEdgeDataFlowModelPass() {
    super(ExecutionProperty.Key.DataFlowModel);
  }

  @Override
  public DAG<IRVertex, IREdge> apply(final DAG<IRVertex, IREdge> dag) {
    dag.topologicalDo(irVertex ->
        dag.getIncomingEdgesOf(irVertex).forEach(irEdge -> {
          if (irEdge.getProperty(ExecutionProperty.Key.DataFlowModel) == null) {
            irEdge.setProperty(DataFlowModelProperty.of(DataFlowModelProperty.Value.Pull));
          }
        }));
    return dag;
  }
}
