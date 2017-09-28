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
package edu.snu.vortex.compiler.optimizer.pass.compiletime.supre;

import edu.snu.vortex.compiler.ir.executionproperty.ExecutionProperty;
import edu.snu.vortex.compiler.optimizer.pass.compiletime.CompileTimePass;

import java.util.Set;

/**
 * A super pass that can do anything the user specifies, without any restrictions or safety checks.
 */
public abstract class SuperPass implements CompileTimePass {
  private final Set<ExecutionProperty.Key> prerequisiteExecutionProperties;

  SuperPass(Set<ExecutionProperty.Key> prerequisiteExecutionProperties) {
    this.prerequisiteExecutionProperties = prerequisiteExecutionProperties;
  }

  @Override
  public Set<ExecutionProperty.Key> getPrerequisiteExecutionProperties() {
    return prerequisiteExecutionProperties;
  }
}
