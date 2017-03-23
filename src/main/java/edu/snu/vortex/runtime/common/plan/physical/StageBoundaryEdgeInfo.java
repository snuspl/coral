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


import edu.snu.vortex.runtime.common.RuntimeAttributes;

import java.io.Serializable;
import java.util.Map;

/**
 * Runtime Edge Info.
 */
public final class StageBoundaryEdgeInfo implements Serializable {
  private final String stageBoundaryEdgeInfo;
  private final Map<RuntimeAttributes.RuntimeEdgeAttribute, Object> edgeAttributes;
  private final String externalEndpointVertexId;
  private final Map<RuntimeAttributes.RuntimeVertexAttribute, Object> externalEndpointVertexAttr;

  public StageBoundaryEdgeInfo(final String runtimeEdgeId,
                         final Map<RuntimeAttributes.RuntimeEdgeAttribute, Object> edgeAttributes,
                         final String externalEndpointVertexId,
                         final Map<RuntimeAttributes.RuntimeVertexAttribute, Object> externalEndpointVertexAttr) {
    this.stageBoundaryEdgeInfo = runtimeEdgeId;
    this.edgeAttributes = edgeAttributes;
    this.externalEndpointVertexId = externalEndpointVertexId;
    this.externalEndpointVertexAttr = externalEndpointVertexAttr;
  }

  public String getStageBoundaryEdgeInfo() {
    return stageBoundaryEdgeInfo;
  }

  public Map<RuntimeAttributes.RuntimeEdgeAttribute, Object> getEdgeAttributes() {
    return edgeAttributes;
  }

  public String getExternalEndpointVertexId() {
    return externalEndpointVertexId;
  }

  public Map<RuntimeAttributes.RuntimeVertexAttribute, Object> getExternalEndpointVertexAttr() {
    return externalEndpointVertexAttr;
  }
}
