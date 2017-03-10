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

import java.util.*;

/**
 * DAG Builder.
 */
public final class DAGBuilder {
  private Map<String, List<Edge>> id2inEdges;
  private Map<String, List<Edge>> id2outEdges;
  private List<Vertex> vertices;

  public DAGBuilder() {
    this.id2inEdges = new HashMap<>();
    this.id2outEdges = new HashMap<>();
    this.vertices = new ArrayList<>();
  }

  /**
   * add an vertex.
   * @param vertex .
   */
  public void addOperator(final Vertex vertex) {
    if (this.contains(vertex)) {
      throw new RuntimeException("DAGBuilder is trying to add an vertex multiple times");
    }
    vertices.add(vertex);
  }

  /**
   */
  /**
   * add an edge for the given vertices.
   * @param src source operator.
   * @param dst destination operator.
   * @param type edge type.
   * @return .
   * @return
   */
  public Edge connectOperators(final Vertex src, final Vertex dst, final Edge.Type type) {
    final Edge edge = new Edge(type, src, dst);
    if (this.contains(edge)) {
      throw new RuntimeException("DAGBuilder is trying to add an edge multiple times");
    }
    addToEdgeList(id2outEdges, src.getId(), edge);
    addToEdgeList(id2inEdges, dst.getId(), edge);
    return edge;
  }

  private void addToEdgeList(final Map<String, List<Edge>> map, final String id, final Edge edge) {
    if (map.containsKey(id)) {
      map.get(id).add(edge);
    } else {
      final List<Edge> inEdges = new ArrayList<>(1);
      inEdges.add(edge);
      map.put(id, inEdges);
    }
  }

  /**
   * check if the DAGBuilder contains the vertex.
   * @param vertex .
   * @return .
   */
  public boolean contains(final Vertex vertex) {
    return vertices.contains(vertex);
  }

  /**
   * check if the DAGBuilder contains the edge.
   * @param edge .
   * @return .
   */
  public boolean contains(final Edge edge) {
    return (id2inEdges.containsValue(edge) || id2outEdges.containsValue(edge));
  }

  /**
   * returns the number of vertices in the DAGBuilder.
   * @return .
   */
  public int size() {
    return vertices.size();
  }

  /**
   * build the DAG.
   * @return .
   */
  public DAG build() {
    // TODO #22: DAG Integrity Check
    final boolean sourceCheck = vertices.stream()
        .filter(operator -> !id2inEdges.containsKey(operator.getId()))
        .allMatch(operator -> operator instanceof Source);

    if (!sourceCheck) {
      throw new RuntimeException("DAG integrity unsatisfied: there are root vertices that are not Sources.");
    }

    return new DAG(vertices, id2inEdges, id2outEdges);
  }
}
