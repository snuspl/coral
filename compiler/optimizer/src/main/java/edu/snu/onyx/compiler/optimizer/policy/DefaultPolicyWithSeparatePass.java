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
package edu.snu.onyx.compiler.optimizer.policy;

import edu.snu.onyx.compiler.optimizer.pass.compiletime.annotating.DefaultStagePartitioningPass;
import edu.snu.onyx.compiler.optimizer.pass.compiletime.annotating.ScheduleGroupPass;
import edu.snu.onyx.compiler.optimizer.pass.compiletime.CompileTimePass;
import edu.snu.onyx.compiler.optimizer.pass.compiletime.composite.CompositePass;
import edu.snu.onyx.compiler.optimizer.pass.compiletime.composite.InitiationCompositePass;
import edu.snu.onyx.runtime.common.optimizer.pass.runtime.RuntimePass;

import java.util.Arrays;
import java.util.List;

/**
 * A simple example policy to demonstrate a policy with a separate, refactored pass.
 * It simply performs what is done with the default pass.
 * This example simply shows that users can define their own pass in their policy.
 */
public final class DefaultPolicyWithSeparatePass implements Policy {
  private final Policy policy;

  public DefaultPolicyWithSeparatePass() {
    this.policy = new PolicyBuilder()
        .registerCompileTimePass(new InitiationCompositePass())
        .registerCompileTimePass(new RefactoredPass())
        .build();
  }

  @Override
  public List<CompileTimePass> getCompileTimePasses() {
    return this.policy.getCompileTimePasses();
  }

  @Override
  public List<RuntimePass<?>> getRuntimePasses() {
    return this.policy.getRuntimePasses();
  }

  /**
   * A simple custom pass consisted of the two passes at the end of the default pass.
   */
  public final class RefactoredPass extends CompositePass {
    public static final String SIMPLE_NAME =  "RefactoredPass";

    RefactoredPass() {
      super(Arrays.asList(
          new DefaultStagePartitioningPass(),
          new ScheduleGroupPass()
      ));
    }
  }
}
