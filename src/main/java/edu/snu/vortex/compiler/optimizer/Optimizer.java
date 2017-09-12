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
package edu.snu.vortex.compiler.optimizer;

import edu.snu.vortex.compiler.ir.IREdge;
import edu.snu.vortex.compiler.ir.IRVertex;
import edu.snu.vortex.compiler.ir.MetricCollectionBarrierVertex;
import edu.snu.vortex.compiler.ir.attribute.Attribute;
import edu.snu.vortex.compiler.optimizer.pass.*;
import edu.snu.vortex.compiler.optimizer.pass.dynamic_optimization.DataSkewDynamicOptimizationPass;
import edu.snu.vortex.common.dag.DAG;
import edu.snu.vortex.compiler.optimizer.policy.Policy;
import edu.snu.vortex.runtime.common.plan.physical.PhysicalPlan;

import java.util.*;

/**
 * Optimizer class.
 */
public final class Optimizer {
  // Private constructor
  private Optimizer() {
  }

  /**
   * Optimize function.
   * @param dag input DAG.
   * @param optimizationPolicy the optimization policy that we want to use to optimize the DAG.
   * @param dagDirectory directory to save the DAG information.
   * @return optimized DAG, tagged with attributes.
   * @throws Exception throws an exception if there is an exception.
   */
  public static DAG<IRVertex, IREdge> optimize(final DAG<IRVertex, IREdge> dag, final Policy optimizationPolicy,
                                               final String dagDirectory) throws Exception {
    if (optimizationPolicy == null || optimizationPolicy.getPolicyContent().isEmpty()) {
      throw new RuntimeException("A policy name should be specified.");
    }
    return process(dag, optimizationPolicy.getPolicyContent(), dagDirectory);
  }

  /**
   * A recursive method to process each pass one-by-one to the given DAG.
   * @param dag DAG to process.
   * @param passes passes to apply.
   * @param dagDirectory directory to save the DAG information.
   * @return the processed DAG.
   * @throws Exception Exceptionso n the way.
   */
  private static DAG<IRVertex, IREdge> process(final DAG<IRVertex, IREdge> dag,
                                               final List<StaticOptimizationPass> passes,
                                               final String dagDirectory) throws Exception {
    if (passes.isEmpty()) {
      return dag;
    } else {
      final DAG<IRVertex, IREdge> processedDAG = passes.get(0).apply(dag);
      processedDAG.storeJSON(dagDirectory, "ir-after-" + passes.get(0).getClass().getSimpleName(),
          "DAG after optimization");
      return process(processedDAG, passes.subList(1, passes.size()), dagDirectory);
    }
  }

  /**
   * Dynamic optimization method to process the dag with an appropriate pass, decided by the stats.
   * @param originalPlan original physical execution plan.
   * @param metricCollectionBarrierVertex the vertex that collects metrics and chooses which optimization to perform.
   * @return the newly updated optimized physical plan.
   */
  public static synchronized PhysicalPlan dynamicOptimization(
          final PhysicalPlan originalPlan,
          final MetricCollectionBarrierVertex metricCollectionBarrierVertex) {
    final Attribute dynamicOptimizationType =
        metricCollectionBarrierVertex.getAttr(Attribute.Key.DynamicOptimizationType);

    switch (dynamicOptimizationType) {
      case DataSkew:
        // Map between a partition ID to corresponding metric data (e.g., the size of each block).
        final Map<String, List<Long>> metricData = metricCollectionBarrierVertex.getMetricData();
        return new DataSkewDynamicOptimizationPass().apply(originalPlan, metricData);
      default:
        return originalPlan;
    }
  }
}
