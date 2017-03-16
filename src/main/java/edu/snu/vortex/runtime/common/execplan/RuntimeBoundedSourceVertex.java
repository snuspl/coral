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
package edu.snu.vortex.runtime.common.execplan;

import edu.snu.vortex.compiler.frontend.beam.BoundedSourceVertex;
import edu.snu.vortex.runtime.common.RuntimeAttributes;
import edu.snu.vortex.runtime.common.task.BoundedSourceTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runtime Bounded Source Vertex.
 */
public final class RuntimeBoundedSourceVertex extends RuntimeVertex {
  private final List<BoundedSourceTask> taskList;
  private final BoundedSourceVertex boundedSourceVertex;

  public RuntimeBoundedSourceVertex(final BoundedSourceVertex boundedSourceVertex,
                                    final Map<RuntimeAttributes.RuntimeVertexAttribute, Object> vertexAttributes) {
    super(boundedSourceVertex.getId(), vertexAttributes);
    this.boundedSourceVertex = boundedSourceVertex;
    this.taskList = new ArrayList<>();
  }

  @Override
  public List<BoundedSourceTask> getTaskList() {
    return null;
  }

  public BoundedSourceVertex getBoundedSourceVertex() {
    return boundedSourceVertex;
  }
}
