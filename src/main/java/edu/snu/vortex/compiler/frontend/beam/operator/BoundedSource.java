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
package edu.snu.vortex.compiler.frontend.beam.operator;

import edu.snu.vortex.compiler.ir.Reader;
import edu.snu.vortex.compiler.ir.SourceVertex;

import java.util.ArrayList;
import java.util.List;

/**
 * SourceVertex operator implementation.
 * @param <O> output type.
 */
public final class BoundedSource<O> extends SourceVertex<O> {
  private final org.apache.beam.sdk.io.BoundedSource<O> source;

  public BoundedSource(final org.apache.beam.sdk.io.BoundedSource<O> source) {
    this.source = source;
  }

  @Override
  public List<Reader<O>> getReaders(final long desiredBundleSizeBytes) throws Exception {
    // Can't use lambda due to exception thrown
    final List<Reader<O>> readers = new ArrayList<>();
    for (final org.apache.beam.sdk.io.BoundedSource<O> s : source.splitIntoBundles(desiredBundleSizeBytes, null)) {
      readers.add(new BoundedSourceReader<>(s.createReader(null)));
    }
    return readers;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    sb.append(", BoundedSource: ");
    sb.append(source);
    return sb.toString();
  }

  /**
   * BoundedSourceReader class.
   * @param <T> type.
   */
  public class BoundedSourceReader<T> implements Reader<T> {
    private final org.apache.beam.sdk.io.BoundedSource.BoundedReader<T> beamReader;
    BoundedSourceReader(final org.apache.beam.sdk.io.BoundedSource.BoundedReader<T> beamReader) {
      this.beamReader = beamReader;
    }

    @Override
    public final Iterable<T> read() throws Exception {
      final ArrayList<T> data = new ArrayList<>();
      try (final org.apache.beam.sdk.io.BoundedSource.BoundedReader<T> reader = beamReader) {
        for (boolean available = reader.start(); available; available = reader.advance()) {
          data.add(reader.getCurrent());
        }
      }
      return data;
    }
  }
}
