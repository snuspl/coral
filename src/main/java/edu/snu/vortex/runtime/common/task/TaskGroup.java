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
package edu.snu.vortex.runtime.common.task;

import edu.snu.vortex.runtime.common.execplan.RuntimeAttributes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * TaskGroup.
 */
public class TaskGroup implements Serializable {
  private final String taskGroupId;
  private final List<Task> taskList;
  private final RuntimeAttributes.ResourceType resourceType;

  public TaskGroup(final String taskGroupId,
                   final int capacity,
                   final RuntimeAttributes.ResourceType resourceType) {
    this.taskGroupId = taskGroupId;
    this.taskList = new ArrayList<>(capacity);
    this.resourceType = resourceType;
  }

  public String getTaskGroupId() {
    return taskGroupId;
  }

  public List<Task> getTaskList() {
    return taskList;
  }

  public void addTask(final Task task) {
    taskList.add(task);
  }

  public RuntimeAttributes.ResourceType getResourceType() {
    return resourceType;
  }
}
