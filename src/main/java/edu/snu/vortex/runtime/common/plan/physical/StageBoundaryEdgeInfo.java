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
package edu.snu.vortex.runtime.common.plan.physical;


import edu.snu.vortex.runtime.common.RuntimeAttributeMap;
import edu.snu.vortex.runtime.common.plan.logical.RuntimeVertex;
import edu.snu.vortex.utils.dag.Edge;

import java.io.Serializable;

/**
 * Contains information stage boundary {@link edu.snu.vortex.runtime.common.plan.logical.StageEdge}.
 */
public final class StageBoundaryEdgeInfo extends Edge<PhysicalStage> implements Serializable {
  private final String stageBoundaryEdgeInfoId;
  private final RuntimeAttributeMap edgeAttributes;

  /**
   * The endpoint {@link edu.snu.vortex.runtime.common.plan.logical.RuntimeVertex} in the other stage.
   * The vertex is connected to a vertex of this stage connected by the edge this class represents.
   */
  private final RuntimeVertex srcVertex;

  /**
   * The endpoint {@link edu.snu.vortex.runtime.common.plan.logical.RuntimeVertex} in the other stage.
   * The vertex is connected to a vertex of this stage connected by the edge this class represents.
   */
  private final RuntimeVertex dstVertex;

  /**
   * IRVertex attributes of the endpoint vertex.
   */
  private final RuntimeAttributeMap externalVertexAttr;

  public StageBoundaryEdgeInfo(final String runtimeEdgeId,
                               final RuntimeAttributeMap edgeAttributes,
                               final RuntimeVertex srcVertex,
                               final RuntimeVertex dstVertex,
                               final RuntimeAttributeMap externalVertexAttr,
                               final PhysicalStage srcStage,
                               final PhysicalStage dstStage) {
    super(srcStage, dstStage);
    this.stageBoundaryEdgeInfoId = runtimeEdgeId;
    this.edgeAttributes = edgeAttributes;
    this.srcVertex = srcVertex;
    this.dstVertex = dstVertex;
    this.externalVertexAttr = externalVertexAttr;
  }

  public String getStageBoundaryEdgeInfoId() {
    return stageBoundaryEdgeInfoId;
  }

  public RuntimeAttributeMap getEdgeAttributes() {
    return edgeAttributes;
  }

  public RuntimeVertex getSrcVertex() {
    return srcVertex;
  }

  public RuntimeVertex getDstVertex() {
    return dstVertex;
  }

  public RuntimeAttributeMap getExternalVertexAttr() {
    return externalVertexAttr;
  }

  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer("StageBoundaryEdgeInfo{");
    sb.append("stageBoundaryEdgeInfoId='").append(stageBoundaryEdgeInfoId).append('\'');
    sb.append(", src='").append(getSrc().getId());
    sb.append(", dst='").append(getDst().getId());
    sb.append(", edgeAttributes=").append(edgeAttributes);
    sb.append(", externalVertexId='").append(srcVertex).append('\'');
    sb.append(", externalVertexAttr=").append(externalVertexAttr);
    sb.append('}');
    return sb.toString();
  }
}
