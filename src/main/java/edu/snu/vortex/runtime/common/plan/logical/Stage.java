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

import edu.snu.vortex.runtime.common.plan.RuntimeEdge;
import edu.snu.vortex.utils.dag.DAG;

/**
 * Represents a stage in Runtime's execution of a job.
 * Each stage contains a part of a whole execution plan.
 * Stage partitioning is determined by {@link edu.snu.vortex.compiler.backend.vortex.VortexBackend}.
 */
public final class Stage {
  private final String stageId;

  private final DAG<RuntimeVertex, RuntimeEdge<RuntimeVertex>> stageInternalDAG;

  public Stage(final String stageId,
               final DAG<RuntimeVertex, RuntimeEdge<RuntimeVertex>> stageInternalDAG) {
    this.stageId = stageId;
    this.stageInternalDAG = stageInternalDAG;
  }

  public DAG<RuntimeVertex, RuntimeEdge<RuntimeVertex>> getStageInternalDAG() {
    return stageInternalDAG;
  }

  public String getStageId() {
    return stageId;
  }

  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer("Stage{");
    sb.append("stageId='").append(stageId).append('\'');
    sb.append(", stageInternalDAG=").append(stageInternalDAG);
    sb.append('}');
    return sb.toString();
  }
}
