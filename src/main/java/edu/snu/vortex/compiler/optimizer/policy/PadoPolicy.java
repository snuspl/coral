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
package edu.snu.vortex.compiler.optimizer.policy;

import edu.snu.vortex.compiler.optimizer.pass.*;
import edu.snu.vortex.compiler.optimizer.pass.optimization.LoopOptimizations;

import java.util.Arrays;
import java.util.List;

/**
 * A policy to perform Pado optimization that uses transient resources on data centers.
 * link to paper: http://dl.acm.org/citation.cfm?id=3064181
 */
public final class PadoPolicy implements Policy {
  @Override
  public List<StaticOptimizationPass> getOptimizationPasses() {
    return  Arrays.asList(
        new ParallelismPass(), // Provides parallelism information.
        new LoopGroupingPass(),
        LoopOptimizations.getLoopFusionPass(),
        LoopOptimizations.getLoopInvariantCodeMotionPass(),
        new LoopUnrollingPass(), // Groups then unrolls loops. TODO #162: remove unrolling pt.
        // Processes vertices and edges with Pado algorithm.
        new PadoVertexPass(), new PadoEdgePass(),
        new DefaultStagePartitioningPass(),
        new ScheduleGroupPass()
    );
  }
}
