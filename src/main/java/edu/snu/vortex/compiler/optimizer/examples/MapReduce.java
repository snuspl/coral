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
package edu.snu.vortex.compiler.optimizer.examples;

import edu.snu.vortex.compiler.optimizer.ir.Attributes;
import edu.snu.vortex.compiler.optimizer.ir.DAG;
import edu.snu.vortex.compiler.optimizer.ir.DAGBuilder;
import edu.snu.vortex.compiler.optimizer.ir.Edge;
import edu.snu.vortex.compiler.optimizer.ir.operator.Do;
import edu.snu.vortex.compiler.optimizer.ir.operator.Operator;
import edu.snu.vortex.compiler.optimizer.ir.operator.Source;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MapReduce {
  public static void main(final String[] args) {
    final EmptySource source = new EmptySource();
    final EmptyDo<String, Pair<String, Integer>, Void> map = new EmptyDo<>("MapOperator");
    final EmptyDo<Pair<String, Iterable<Integer>>, String, Void> reduce = new EmptyDo<>("ReduceOperator");

    // Before
    final DAGBuilder builder = new DAGBuilder();
    builder.addOp(source);
    builder.addOp(map);
    builder.addOp(reduce);
    builder.connectOps(source, map, Edge.Type.O2O);
    builder.connectOps(map, reduce, Edge.Type.M2M);
    final DAG dag = builder.build();
    System.out.println("Before Optimization");
    DAG.print(dag);

    // Optimize
    final List<Operator> topoSorted = new LinkedList<>();
    DAG.doDFS(dag, (operator -> topoSorted.add(0, operator)), DAG.VisitOrder.PostOrder);
    topoSorted.forEach(operator -> {
      final Optional<List<Edge>> inEdges = dag.getInEdges(operator);
      if (!inEdges.isPresent()) {
        operator.setAttr(Attributes.Key.Placement, Attributes.Placement.Compute);
      } else {
        operator.setAttr(Attributes.Key.Placement, Attributes.Placement.Storage);
      }
    });

    // After
    System.out.println("After Optimization");
    DAG.print(dag);
  }

  private static class Pair<K, V> {
    private K key;
    private V val;

    Pair(final K key, final V val) {
      this.key = key;
      this.val = val;
    }
  }

  private static class EmptySource extends Source {
    @Override
    public List<Reader> getReaders(long desiredBundleSizeBytes) throws Exception {
      return null;
    }
  }

  private static class EmptyDo<I, O, T> extends Do<I, O, T> {
    private final String name;

    EmptyDo(final String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder();
      sb.append(super.toString());
      sb.append(", name: ");
      sb.append(name);
      return sb.toString();
    }

    @Override
    public Iterable<O> transform(final Iterable<I> input, final Map<T, Object> broadcasted) {
      return null;
    }
  }

}
