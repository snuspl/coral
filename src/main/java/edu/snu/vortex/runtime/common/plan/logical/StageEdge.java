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


import edu.snu.vortex.runtime.common.*;

/**
 * Stage IREdge.
 */
public final class StageEdge {
  private final String stageEdgeId;
  private final RuntimeAttributeMap edgeAttributes;
  private final RuntimeVertex srcRuntimeVertex;
  private final RuntimeVertex dstRuntimeVertex;

  /**
   * Represents the edge between vertices in a logical plan.
   * @param irEdgeId id of this edge.
   * @param edgeAttributes to control the data flow on this edge.
   * @param srcRuntimeVertex source vertex.
   * @param dstRuntimeVertex destination vertex.
   */
  public StageEdge(final String irEdgeId,
                   final RuntimeAttributeMap edgeAttributes,
                   final RuntimeVertex srcRuntimeVertex,
                   final RuntimeVertex dstRuntimeVertex) {
    this.stageEdgeId = RuntimeIdGenerator.generateRuntimeEdgeId(irEdgeId);
    this.edgeAttributes = edgeAttributes;
    this.srcRuntimeVertex = srcRuntimeVertex;
    this.dstRuntimeVertex = dstRuntimeVertex;
  }

  public String getId() {
    return stageEdgeId;
  }

  public RuntimeAttributeMap getEdgeAttributes() {
    return edgeAttributes;
  }

  public RuntimeVertex getSrcRuntimeVertex() {
    return srcRuntimeVertex;
  }

  public RuntimeVertex getDstRuntimeVertex() {
    return dstRuntimeVertex;
  }
}
