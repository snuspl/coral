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
package edu.snu.vortex.compiler.frontend.beam.transform;

import edu.snu.vortex.compiler.ir.Element;
import edu.snu.vortex.compiler.ir.OutputCollector;
import edu.snu.vortex.compiler.ir.Transform;
import org.apache.beam.sdk.transforms.windowing.WindowFn;

/**
 * Windowing transform implementation.
 * This transform simply windows the given elements into finite windows according to a user-specified WindowTransform.
 * As this functionality is unnecessary for batch processing workloads and for Vortex Runtime, this is left as below.
 */
public final class WindowTransform implements Transform {
  private final WindowFn windowFn;
  private OutputCollector outputCollector;

  public WindowTransform(final WindowFn windowFn) {
    this.windowFn = windowFn;
  }

  @Override
  public void prepare(final Context context, final OutputCollector oc) {
    this.outputCollector = oc;
  }

  @Override
  public void onData(final Iterable<Element> data) {
    // TODO #36: Actually assign windows
    data.forEach(outputCollector::emit);
  }

  @Override
  public void close() {
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("WindowTransform:" + windowFn);
    return sb.toString();
  }
}
