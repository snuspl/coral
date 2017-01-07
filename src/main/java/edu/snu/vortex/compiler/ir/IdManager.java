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
package edu.snu.vortex.compiler.ir;

import java.util.concurrent.atomic.AtomicInteger;

public final class IdManager {
  private static AtomicInteger operatorId = new AtomicInteger(1);
  private static AtomicInteger edgeId = new AtomicInteger(1);
  private static AtomicInteger stageId = new AtomicInteger(1);

  public static String newOperatorId() {
    return "operator" + operatorId.getAndIncrement();
  }
  public static String newEdgeId() {
    return "edge" + edgeId.getAndIncrement();
  }
  public static String newStageId() {
    return "stage" + stageId.getAndIncrement();
  }
}
