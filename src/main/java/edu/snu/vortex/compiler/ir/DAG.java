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

import edu.snu.vortex.compiler.ir.operator.Operator;

import java.util.*;
import java.util.function.Consumer;

/**
 * Physical execution plan of a user program.
 */
public class DAG {
  private final Map<String, List<Edge>> id2inEdges;
  private final Map<String, List<Edge>> id2outEdges;
  private final List<Operator> rootOperators;

  DAG(final List<Operator> rootOperators,
             final Map<String, List<Edge>> id2inEdges,
             final Map<String, List<Edge>> id2outEdges) {
    this.rootOperators = rootOperators;
    this.id2inEdges = id2inEdges;
    this.id2outEdges = id2outEdges;
  }

  public List<Operator> getRootOperators() {
    return rootOperators;
  }

  /**
   * Gets the edges coming in to the given operator
   * @param operator
   * @return
   */
  public Optional<List<Edge>> getInEdgesOf(final Operator operator) {
    final List<Edge> inEdges = id2inEdges.get(operator.getId());
    return inEdges == null ? Optional.empty() : Optional.of(inEdges);
  }

  /**
   * Gets the edges going out of the given operator
   * @param operator
   * @return
   */
  public Optional<List<Edge>> getOutEdgesOf(final Operator operator) {
    final List<Edge> outEdges = id2outEdges.get(operator.getId());
    return outEdges == null ? Optional.empty() : Optional.of(outEdges);
  }

  /**
   * Finds the edge between two operators in the DAG.
   * @param operator1
   * @param operator2
   * @return
   */
  public Optional<Edge> getEdgeBetween(final Operator operator1, final Operator operator2) {
    final Optional<List<Edge>> inEdges = this.getInEdgesOf(operator1);
    final Optional<List<Edge>> outEdges = this.getOutEdgesOf(operator1);
    final Set<Edge> edges = new HashSet<>();

    if (inEdges.isPresent()) {
      inEdges.get().forEach(e -> {
        if (e.getSrc().equals(operator2)) {
          edges.add(e);
        }
      });
    }
    if (outEdges.isPresent()) {
      outEdges.get().forEach(e -> {
        if (e.getDst().equals(operator2)) {
          edges.add(e);
        }
      });
    }

    if (edges.size() > 1) {
      throw new RuntimeException("There are more than one edge between two operators, this should never happen");
    } else if (edges.size() == 1) {
      return Optional.of(edges.iterator().next());
    } else {
      return Optional.empty();
    }
  }

  /////////////// Auxiliary overriding functions

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DAG dag = (DAG) o;

    if (id2inEdges != null ? !id2inEdges.equals(dag.id2inEdges) : dag.id2inEdges != null) return false;
    if (id2outEdges != null ? !id2outEdges.equals(dag.id2outEdges) : dag.id2outEdges != null) return false;
    return rootOperators != null ? rootOperators.equals(dag.rootOperators) : dag.rootOperators == null;
  }

  @Override
  public int hashCode() {
    int result = id2inEdges != null ? id2inEdges.hashCode() : 0;
    result = 31 * result + (id2outEdges != null ? id2outEdges.hashCode() : 0);
    result = 31 * result + (rootOperators != null ? rootOperators.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    this.doDFS((operator -> {
      sb.append("<operator> ");
      sb.append(operator.toString());
      sb.append(" / <inEdges> ");
      sb.append(this.getInEdgesOf(operator).toString());
      sb.append("\n");
    }), VisitOrder.PreOrder);
    return sb.toString();
  }

  ////////// DFS Traversal
  public enum VisitOrder {
    PreOrder,
    PostOrder
  }

  /**
   * Do a DFS traversal with the given visit order.
   * @param function
   * @param visitOrder
   */
  public void doDFS(final Consumer<Operator> function,
                           final VisitOrder visitOrder) {
    final HashSet<Operator> visited = new HashSet<>();
    this.getRootOperators().stream()
        .filter(source -> !visited.contains(source))
        .forEach(source -> visit(source, function, visitOrder, visited));
  }

  private void visit(final Operator operator,
                            final Consumer<Operator> operatorConsumer,
                            final VisitOrder visitOrder,
                            final HashSet<Operator> visited) {
    visited.add(operator);
    if (visitOrder == VisitOrder.PreOrder) {
      operatorConsumer.accept(operator);
    }
    final Optional<List<Edge>> outEdges = getOutEdgesOf(operator);
    if (outEdges.isPresent()) {
      outEdges.get().stream()
          .map(outEdge -> outEdge.getDst())
          .filter(outOperator -> !visited.contains(outOperator))
          .forEach(outOperator -> visit(outOperator, operatorConsumer, visitOrder, visited));
    }
    if (visitOrder == VisitOrder.PostOrder) {
      operatorConsumer.accept(operator);
    }
  }
}

