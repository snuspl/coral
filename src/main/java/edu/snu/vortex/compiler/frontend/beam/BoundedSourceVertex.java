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
package edu.snu.vortex.compiler.frontend.beam;

import edu.snu.vortex.compiler.ir.Element;
import edu.snu.vortex.compiler.ir.Reader;
import edu.snu.vortex.compiler.ir.SourceVertex;
import org.apache.beam.sdk.io.BoundedSource;

import java.util.ArrayList;
import java.util.List;

/**
 * SourceVertex implementation for BoundedSource.
 * @param <O> output type.
 */
public final class BoundedSourceVertex<O> extends SourceVertex<O> {
  private final BoundedSource<O> source;

  /**
   * Constructor of BoundedSourceVertex.
   * @param source BoundedSource to read from.
   */
  public BoundedSourceVertex(final BoundedSource<O> source) {
    this.source = source;
  }

  @Override
  public BoundedSourceVertex getClone() {
    return new BoundedSourceVertex<>(this.source);
  }

  @Override
  public List<Reader<O>> getReaders(final int desiredNumOfSplits) throws Exception {
    final List<Reader<O>> readers = new ArrayList<>();
    System.out.println("estimate: " + source.getEstimatedSizeBytes(null));
    System.out.println("desired: " + desiredNumOfSplits);

    source.splitIntoBundles(source.getEstimatedSizeBytes(null) / desiredNumOfSplits, null).forEach(boundedSource -> {
      readers.add(new BoundedSourceReader<>(boundedSource));
    });

    System.out.println("readers");
    return readers;
  }

  @Override
  public String propertiesToJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{");
    sb.append(irVertexPropertiesToString());
    sb.append(", \"source\": \"");
    sb.append(source);
    sb.append("\"}");
    return sb.toString();
  }

  /**
   * BoundedSourceReader class.
   * @param <T> type.
   */
  public class BoundedSourceReader<T> implements Reader<T> {
    private final BoundedSource<T> boundedSource;

    /**
     * Constructor of the BoundedSourceReader.
     * @param boundedSource the BoundedSource.
     */
    BoundedSourceReader(final BoundedSource boundedSource) {
      this.boundedSource = boundedSource;
    }

    @Override
    public final Iterable<Element<T, ?, ?>> read() throws Exception {
      final ArrayList<Element<T, ?, ?>> data = new ArrayList<>();
      try (BoundedSource.BoundedReader<T> reader = boundedSource.createReader(null)) {
        for (boolean available = reader.start(); available; available = reader.advance()) {
          data.add(new BeamElement<>(reader.getCurrent()));
        }
      }
      return data;
    }
  }
}
