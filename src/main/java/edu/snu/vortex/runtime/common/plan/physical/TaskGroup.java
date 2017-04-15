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

import edu.snu.vortex.runtime.common.RuntimeAttribute;
import edu.snu.vortex.runtime.common.plan.RuntimeEdge;
import edu.snu.vortex.utils.dag.DAG;

import java.io.Serializable;

/**
 * TaskGroup.
 */
public final class TaskGroup implements Serializable {
  private final String taskGroupId;
  private final DAG<Task, RuntimeEdge<Task>> taskDAG;
  private final RuntimeAttribute resourceType;

  public TaskGroup(final String taskGroupId,
                   final DAG<Task, RuntimeEdge<Task>> taskDAG,
                   final RuntimeAttribute resourceType) {
    this.taskGroupId = taskGroupId;
    this.taskDAG = taskDAG;
    this.resourceType = resourceType;
  }

  public RuntimeAttribute getResourceType() {
    return resourceType;
  }

  public DAG<Task, RuntimeEdge<Task>> getTaskDAG() {
    return taskDAG;
  }

  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer("TaskGroup{");
    sb.append("taskGroupId='").append(taskGroupId).append('\'');
    sb.append(", taskDAG=").append(taskDAG);
    sb.append(", resourceType=").append(resourceType);
    sb.append('}');
    return sb.toString();
  }
}
