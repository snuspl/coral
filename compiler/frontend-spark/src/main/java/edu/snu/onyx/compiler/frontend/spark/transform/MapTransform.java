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
package edu.snu.onyx.compiler.frontend.spark.transform;

import edu.snu.onyx.common.ir.OutputCollector;
import edu.snu.onyx.common.ir.vertex.transform.Transform;

import java.io.Serializable;

/**
 * Map Transform for Spark.
 * @param <I> input type.
 * @param <O> output type.
 */
public final class MapTransform<I extends Serializable, O extends Serializable> implements Transform<I, O> {
  private final SerializableFunction<I, O> func;
  private OutputCollector<O> oc;

  /**
   * Constructor.
   * @param func the function to run map with.
   */
  public MapTransform(final SerializableFunction<I, O> func) {
    this.func = func;
  }

  @Override
  public void prepare(final Context context, final OutputCollector<O> outputCollector) {
    this.oc = outputCollector;
  }

  @Override
  public void onData(final Iterable<I> elements, final String srcVertexId) {
    elements.forEach(element -> oc.emit(func.apply(element)));
  }

  @Override
  public void close() {
  }
}
