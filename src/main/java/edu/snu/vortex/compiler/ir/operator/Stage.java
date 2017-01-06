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
package edu.snu.vortex.compiler.ir.operator;

import edu.snu.vortex.compiler.ir.DAG;

public class Stage<I, O> extends Operator<I, O> {
  private final DAG dag;

  public Stage(final DAG dag) {
    this.dag = dag;
  }

  public DAG getDAG() {
    return dag;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("STAGE " + this.getId() + "\n");
    sb.append(dag.toString());
    sb.append("END OF STAGE " + this.getId() + "\n");
    return sb.toString();
  }
}
