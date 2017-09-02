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

import edu.snu.vortex.common.dag.DAG;
import edu.snu.vortex.compiler.ir.IREdge;
import edu.snu.vortex.compiler.ir.IRVertex;

import java.util.*;

import static edu.snu.vortex.compiler.ir.attribute.Attribute.IntegerKey.ScheduleGroupIndex;
import static edu.snu.vortex.compiler.ir.attribute.Attribute.IntegerKey.StageId;

/**
 * A pass for assigning each stages in schedule groups.
 * We traverse the DAG topologically to find the dependency information between stages and number them appropriately
 * to give correct order or schedule groups.
 */
public final class ScheduleGroupPass implements StaticOptimizationPass {
  @Override
  public DAG<IRVertex, IREdge> process(final DAG<IRVertex, IREdge> dag) {
    // We assume that the input dag is tagged with stage ids.
    if (dag.getVertices().stream().anyMatch(irVertex -> irVertex.getAttr(StageId) == null)) {
      throw new RuntimeException("There exists an IR vertex going through ScheduleGroupPass "
          + "without stage id tagged.");
    }

    // Map of stage id to the stage ids that it depends on.
    final Map<Integer, Set<Integer>> dependentStagesMap = new HashMap<>();
    dag.topologicalDo(irVertex -> {
      final Integer currentStageId = irVertex.getAttr(StageId);
      dependentStagesMap.putIfAbsent(currentStageId, new HashSet<>());
      // while traversing, we find the stages that point to the current stage and add them to the list.
      dag.getIncomingEdgesOf(irVertex).stream()
          .map(IREdge::getSrc)
          .map(vertex -> vertex.getAttr(StageId))
          .filter(n -> !n.equals(currentStageId))
          .forEach(n -> dependentStagesMap.get(currentStageId).add(n));
    });

    // Map to put our results in.
    final Map<Integer, Integer> stageIdToScheduleGroupIndexMap = new HashMap<>();

    // Calculate schedule group number of each stages step by step
    while (stageIdToScheduleGroupIndexMap.size() < dependentStagesMap.size()) {
      // This is to ensure that each iteration is making progress.
      final Integer previousSize = stageIdToScheduleGroupIndexMap.size();
      dependentStagesMap.forEach((stageId, dependentStages) -> {
        if (!stageIdToScheduleGroupIndexMap.keySet().contains(stageId)
            && dependentStages.size() == 0) { // initial source stages
          stageIdToScheduleGroupIndexMap.put(stageId, 0); // initial source stages are indexed with schedule group 0.
        } else if (!stageIdToScheduleGroupIndexMap.keySet().contains(stageId)
            && dependentStages.stream().allMatch(stageIdToScheduleGroupIndexMap::containsKey)) { // next stages
          // We find the maximum schedule group index from previous stages, and index current stage with that number +1.
          final Integer maxDependentSchedulerGroup =
              dependentStages.stream()
                  .mapToInt(stageIdToScheduleGroupIndexMap::get)
                  .max().orElseThrow(() ->
                    new RuntimeException("A stage that is not a source stage much have dependent stages"));
          stageIdToScheduleGroupIndexMap.put(stageId, maxDependentSchedulerGroup + 1);
        }
      });
      if (previousSize == stageIdToScheduleGroupIndexMap.size()) {
        throw new RuntimeException("Iteration for indexing schedule groups in "
            + ScheduleGroupPass.class.getSimpleName() + " is not making progress");
      }
    }

    // do the tagging
    dag.topologicalDo(irVertex ->
        irVertex.setAttr(ScheduleGroupIndex, stageIdToScheduleGroupIndexMap.get(irVertex.getAttr(StageId))));

    return dag;
  }
}
