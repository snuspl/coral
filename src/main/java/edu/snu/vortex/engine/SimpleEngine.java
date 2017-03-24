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
package edu.snu.vortex.engine;

import edu.snu.vortex.compiler.frontend.beam.transform.BroadcastTransform;
import edu.snu.vortex.compiler.ir.*;
import org.apache.beam.sdk.values.PCollectionView;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A simple engine that prints vertex outputs to stdout.
 */
public final class SimpleEngine {
  public void executeDAG(final DAG dag) throws Exception {
    final Map<String, List<Iterable<Element>>> edgeIdToPartitions = new HashMap<>();
    final Map<String, Object> edgeIdToBroadcast = new HashMap<>();

    dag.doTopological(vertex -> {
      if (vertex instanceof SourceVertex) {
        try {
          final SourceVertex sourceVertex = (SourceVertex) vertex;
          final List<Reader> readers = sourceVertex.getReaders(10); // 10 Bytes per BoundedSourceReader
          final List<Iterable<Element>> partitions = new ArrayList<>(readers.size());

          System.out.println("Begin processing SourceVertex: " + sourceVertex);

          for (final Reader reader : readers) {
            partitions.add(reader.read());
          }
          dag.getOutEdgesOf(vertex).get().stream()
              .map(outEdge -> outEdge.getId())
              .forEach(id -> edgeIdToPartitions.put(id, partitions));

          System.out.println(" Output of {" + vertex.getId() + "} for edges " + dag.getOutEdgesOf(vertex).get().stream()
              .map(Edge::getId).collect(Collectors.toList()) + ": " + partitions);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      } else if (vertex instanceof OperatorVertex) {
        final OperatorVertex operatorVertex = (OperatorVertex) vertex;
        final Transform transform = operatorVertex.getTransform();
        final List<Edge> inEdges = dag.getInEdgesOf(vertex).get(); // must be at least one edge
        final List<Edge> outEdges = dag.getOutEdgesOf(vertex).orElse(new ArrayList<>(0)); // empty lists for sinks

        final Map<PCollectionView, Object> broadcastedInput = new HashMap<>();
        inEdges.stream()
            .filter(edge -> edge.getType().equals(Edge.Type.Broadcast))
            .forEach(edge -> broadcastedInput.put(
                ((BroadcastTransform) ((OperatorVertex) edge.getSrc()).getTransform()).getTag(),
                edgeIdToBroadcast.get(edge.getId())));

        // Process each input edge
        inEdges.forEach(inEdge -> {
          if (edgeIdToPartitions.get(inEdge.getId()) != null && !edgeIdToPartitions.get(inEdge.getId()).isEmpty()) {
            final List<Iterable<Element>> inDataPartitions;
            if (operatorVertex.getTransform() instanceof BroadcastTransform) {
              final List<Element> iterableList = new ArrayList<>();
              edgeIdToPartitions.get(inEdge.getId()).forEach(iterable -> iterable.forEach(iterableList::add));
              inDataPartitions = Arrays.asList(iterableList);
            } else {
              inDataPartitions = edgeIdToPartitions.get(inEdge.getId());
            }
            final List<Iterable<Element>> outDataPartitions = new ArrayList<>();

            // Process each partition of an edge
            System.out.println("Begin processing {" + inEdge.getId() + "} for OperatorVertex: " + operatorVertex);

            inDataPartitions.forEach(inData -> {
              final Transform.Context transformContext = new ContextImpl(broadcastedInput);
              final OutputCollectorImpl outputCollector = new OutputCollectorImpl();
              transform.prepare(transformContext, outputCollector);
              transform.onData(inData, inEdge.getSrc().getId());
              transform.close();
              outDataPartitions.add(outputCollector.getOutputList());
            });

            // Save the results
            if (outEdges.size() > 0) {
              outEdges.forEach(outEdge -> {
                if (outEdge.getType() == Edge.Type.Broadcast) {
                  if (outDataPartitions.size() != 1) {
                    throw new RuntimeException("Size of out data partitions of a broadcast operator must match 1");
                  }
                  outDataPartitions.get(0).forEach(element ->
                      edgeIdToBroadcast.put(outEdge.getId(), element.getData()));
                } else {
                  edgeIdToPartitions.put(outEdge.getId(), routePartitions(outDataPartitions, outEdge));
                }
              });
            } else if (outEdges.size() == 0) {
              System.out.println("Sink Vertex");
            } else {
              throw new IllegalStateException("Size must not be negative");
            }

            System.out.println(" Output of {" + vertex.getId() + "} for edges " +
                outEdges.stream().map(Edge::getId).collect(Collectors.toList()) + ": " + outDataPartitions);
          }
        });
      } else {
        throw new UnsupportedOperationException(vertex.toString());
      }
    });

    System.out.println("Job completed.");
  }

  private List<Iterable<Element>> routePartitions(final List<Iterable<Element>> partitions,
                                                  final Edge edge) {
    final Edge.Type edgeType = edge.getType();
    if (edgeType == Edge.Type.OneToOne) {
      return partitions;
    } else if (edgeType == Edge.Type.ScatterGather) {
      final int numOfDsts = partitions.size(); // Same as the number of current partitions
      final List<List<Element>> routedPartitions = new ArrayList<>(numOfDsts);
      IntStream.range(0, numOfDsts).forEach(x -> routedPartitions.add(new ArrayList<>()));

      // Hash-based routing
      partitions.forEach(partition -> {
        partition.forEach(element -> {
          final int dstIndex = Math.abs(element.getKey().hashCode() % numOfDsts);
          routedPartitions.get(dstIndex).add(element);
        });
      });

      // for some reason, Java requires the type to be explicit
      final List<Iterable<Element>> explicitlyIterables = new ArrayList<>();
      routedPartitions.forEach(explicitlyIterables::add);
      return explicitlyIterables;
    } else {
      throw new UnsupportedOperationException();
    }
  }
}
