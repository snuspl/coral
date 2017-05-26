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
package edu.snu.vortex.compiler.optimizer.passes.optimization;

import edu.snu.vortex.compiler.ir.IREdge;
import edu.snu.vortex.compiler.ir.IRVertex;
import edu.snu.vortex.compiler.ir.LoopVertex;
import edu.snu.vortex.compiler.optimizer.passes.Pass;
import edu.snu.vortex.utils.dag.DAG;
import edu.snu.vortex.utils.dag.DAGBuilder;

import java.util.*;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

/**
 * Loop Optimization.
 */
public class LoopOptimizations {
  /**
   * @return a new LoopFusionPass class.
   */
  public final LoopFusionPass getLoopFusionPass() {
    return new LoopFusionPass();
  }

  /**
   * @return a new LoopInvariantCodeMotionPass class.
   */
  public final LoopInvariantCodeMotionPass getLoopInvariantCodeMotionPass() {
    return new LoopInvariantCodeMotionPass();
  }

  /**
   * Static function to collect LoopVertices.
   * @param dag DAG to observe.
   * @param loopVertices Map to save the LoopVertices to, according to their termination conditions.
   * @param inEdges incoming edges of LoopVertices.
   * @param outEdges outgoing Edges of LoopVertices.
   * @param builder builder to build the rest of the DAG on.
   */
  private static void collectLoopVertices(final DAG<IRVertex, IREdge> dag,
                                          final List<LoopVertex> loopVertices,
                                          final Map<LoopVertex, List<IREdge>> inEdges,
                                          final Map<LoopVertex, List<IREdge>> outEdges,
                                          final DAGBuilder<IRVertex, IREdge> builder) {
    // Collect loop vertices.
    dag.topologicalDo(irVertex -> {
      if (irVertex instanceof LoopVertex) {
        final LoopVertex loopVertex = (LoopVertex) irVertex;
        loopVertices.add(loopVertex);

        dag.getIncomingEdgesOf(loopVertex).forEach(irEdge -> {
          inEdges.putIfAbsent(loopVertex, new ArrayList<>());
          inEdges.get(loopVertex).add(irEdge);
          if (irEdge.getSrc() instanceof LoopVertex) {
            final LoopVertex source = (LoopVertex) irEdge.getSrc();
            outEdges.putIfAbsent(source, new ArrayList<>());
            outEdges.get(source).add(irEdge);
          }
        });
      } else {
        builder.addVertex(irVertex, dag);
        dag.getIncomingEdgesOf(irVertex).forEach(irEdge -> {
          if (irEdge.getSrc() instanceof LoopVertex) {
            final LoopVertex loopVertex = (LoopVertex) irEdge.getSrc();
            outEdges.putIfAbsent(loopVertex, new ArrayList<>());
            outEdges.get(loopVertex).add(irEdge);
          } else {
            builder.connectVertices(irEdge);
          }
        });
      }
    });
  }

  /**
   * Pass for Loop Fusion optimization.
   */
  public final class LoopFusionPass implements Pass {
    @Override
    public DAG<IRVertex, IREdge> process(final DAG<IRVertex, IREdge> dag) throws Exception {
      final List<LoopVertex> loopVertices = new ArrayList<>();
      final Map<LoopVertex, List<IREdge>> inEdges = new HashMap<>();
      final Map<LoopVertex, List<IREdge>> outEdges = new HashMap<>();
      final DAGBuilder<IRVertex, IREdge> builder = new DAGBuilder<>();

      collectLoopVertices(dag, loopVertices, inEdges, outEdges, builder);

      // Collect and group those with same termination condition.
      final Set<Set<LoopVertex>> setOfLoopsToBeFused = new HashSet<>();
      loopVertices.forEach(loopVertex -> {
        final IntPredicate terminationCondition = loopVertex.getTerminationCondition();
        final Integer numberOfIterations = loopVertex.getMaxNumberOfIterations();
        final List<LoopVertex> adjacentLoops = getAdjacentLoopVertices(loopVertex, inEdges, outEdges);

        final Set<LoopVertex> loopsToBeFused = new HashSet<>();
        loopsToBeFused.add(loopVertex);
        adjacentLoops.forEach(adjacentLoop -> {
          Boolean canBeMerged = adjacentLoop.getMaxNumberOfIterations().equals(numberOfIterations);
          for (int i = 0; i < numberOfIterations; i++) {
            if (adjacentLoop.getTerminationCondition().test(numberOfIterations)
                != (terminationCondition.test(numberOfIterations))) {
              canBeMerged = false;
            }
          }
          if (canBeMerged) {
            loopsToBeFused.add(adjacentLoop);
          }
        });

        final Optional<Set<LoopVertex>> listToAddVerticesTo = setOfLoopsToBeFused.stream()
            .filter(list -> list.stream().anyMatch(loopsToBeFused::contains)).findFirst();
        if (listToAddVerticesTo.isPresent()) {
          listToAddVerticesTo.get().addAll(loopsToBeFused);
        } else {
          setOfLoopsToBeFused.add(loopsToBeFused);
        }
      });

      // merge and add to builder.
      setOfLoopsToBeFused.forEach(loops -> {
        if (loops.size() > 1) {
          final LoopVertex newLoopVertex = mergeLoopVertices(loops);
          builder.addVertex(newLoopVertex, dag);
          loops.forEach(loopVertex -> {
            // inEdges.
            inEdges.getOrDefault(loopVertex, new ArrayList<>()).forEach(irEdge -> {
              if (builder.contains(irEdge.getSrc())) {
                final IREdge newIREdge =
                    new IREdge(irEdge.getType(), irEdge.getSrc(), newLoopVertex, irEdge.getCoder());
                IREdge.copyAttributes(irEdge, newIREdge);
                builder.connectVertices(newIREdge);
              }
            });
            // outEdges.
            outEdges.getOrDefault(loopVertex, new ArrayList<>()).forEach(irEdge -> {
              if (builder.contains(irEdge.getDst())) {
                final IREdge newIREdge =
                    new IREdge(irEdge.getType(), newLoopVertex, irEdge.getDst(), irEdge.getCoder());
                IREdge.copyAttributes(irEdge, newIREdge);
                builder.connectVertices(newIREdge);
              }
            });
          });
        } else {
          loops.forEach(loopVertex -> {
            builder.addVertex(loopVertex);
            // inEdges.
            inEdges.getOrDefault(loopVertex, new ArrayList<>()).forEach(edge -> {
              if (builder.contains(edge.getSrc())) {
                builder.connectVertices(edge);
              }
            });
            // outEdges.
            outEdges.getOrDefault(loopVertex, new ArrayList<>()).forEach(edge -> {
              if (builder.contains(edge.getDst())) {
                builder.connectVertices(edge);
              }
            });
          });
        }
      });

      return builder.build();
    }

    private LoopVertex mergeLoopVertices(final Set<LoopVertex> loopVertices) {
      final String newName =
          String.join("+", loopVertices.stream().map(LoopVertex::getName).collect(Collectors.toList()));
      final LoopVertex mergedLoopVertex = new LoopVertex(newName);
      loopVertices.forEach(loopVertex -> {
        final DAG<IRVertex, IREdge> dagToCopy = loopVertex.getDAG();
        dagToCopy.topologicalDo(v -> {
          mergedLoopVertex.getBuilder().addVertex(v);
          dagToCopy.getIncomingEdgesOf(v).forEach(mergedLoopVertex.getBuilder()::connectVertices);
        });
        loopVertex.getDagIncomingEdges().forEach((v, es) -> es.forEach(mergedLoopVertex::addDagIncomingEdge));
        loopVertex.getIterativeIncomingEdges().forEach((v, es) ->
            es.forEach(mergedLoopVertex::addIterativeIncomingEdge));
        loopVertex.getNonIterativeIncomingEdges().forEach((v, es) ->
            es.forEach(mergedLoopVertex::addNonIterativeIncomingEdge));
        loopVertex.getDagOutgoingEdges().forEach((v, es) -> es.forEach(mergedLoopVertex::addDagOutgoingEdge));
      });
      return mergedLoopVertex;
    }

    private List<LoopVertex> getAdjacentLoopVertices(final LoopVertex loopVertex,
                                                     final Map<LoopVertex, List<IREdge>> inEdges,
                                                     final Map<LoopVertex, List<IREdge>> outEdges) {
      final List<LoopVertex> neighboringLoops = new ArrayList<>();
      inEdges.getOrDefault(loopVertex, new ArrayList<>()).stream().map(IREdge::getSrc)
          .filter(irVertex -> irVertex instanceof LoopVertex).map(irVertex -> (LoopVertex) irVertex)
          .forEach(neighboringLoops::add);
      outEdges.getOrDefault(loopVertex, new ArrayList<>()).stream().map(IREdge::getDst)
          .filter(irVertex -> irVertex instanceof LoopVertex).map(irVertex -> (LoopVertex) irVertex)
          .forEach(neighboringLoops::add);
      return neighboringLoops;
    }
  }

  /**
   * Pass for Loop Invariant Code Motion optimization.
   */
  public final class LoopInvariantCodeMotionPass implements Pass {
    @Override
    public DAG<IRVertex, IREdge> process(final DAG<IRVertex, IREdge> dag) throws Exception {
      final List<LoopVertex> loopVertices = new ArrayList<>();
      final Map<LoopVertex, List<IREdge>> inEdges = new HashMap<>();
      final Map<LoopVertex, List<IREdge>> outEdges = new HashMap<>();
      final DAGBuilder<IRVertex, IREdge> builder = new DAGBuilder<>();

      collectLoopVertices(dag, loopVertices, inEdges, outEdges, builder);

      // Refactor those with same data scan / operation, without dependencies in the loop.
      loopVertices.forEach(loopVertex -> {
        final List<Map.Entry<IRVertex, Set<IREdge>>> candidates = loopVertex.getNonIterativeIncomingEdges().entrySet()
            .stream().filter(entry ->
                loopVertex.getDAG().getIncomingEdgesOf(entry.getKey()).size() == 0 // no internal inEdges
                    && loopVertex.getIterativeIncomingEdges().get(entry.getKey()).size() == 0) // no external inEdges
            .collect(Collectors.toList());
        candidates.forEach(candidate -> {
          // add refactored vertex to builder.
          builder.addVertex(candidate.getKey());
          // connect incoming edges.
          candidate.getValue().forEach(builder::connectVertices);
          // connect outgoing edges.
          loopVertex.getDAG().getOutgoingEdgesOf(candidate.getKey()).forEach(loopVertex::addNonIterativeIncomingEdge);
          // clear garbage.
          loopVertex.getBuilder().removeVertex(candidate.getKey());
          loopVertex.getNonIterativeIncomingEdges().remove(candidate.getKey());
        });
      });

      // Add LoopVertices.
      loopVertices.forEach(loopVertex -> {
        builder.addVertex(loopVertex);
        inEdges.getOrDefault(loopVertex, new ArrayList<>()).forEach(builder::connectVertices);
        outEdges.getOrDefault(loopVertex, new ArrayList<>()).forEach(builder::connectVertices);
      });

      return builder.build();
    }
  }
}
