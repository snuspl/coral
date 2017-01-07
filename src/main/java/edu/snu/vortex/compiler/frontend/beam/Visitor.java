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

import edu.snu.vortex.compiler.frontend.beam.operator.BroadcastImpl;
import edu.snu.vortex.compiler.frontend.beam.operator.DoImpl;
import edu.snu.vortex.compiler.frontend.beam.operator.GroupByKeyImpl;
import edu.snu.vortex.compiler.frontend.beam.operator.SourceImpl;
import edu.snu.vortex.compiler.ir.DAGBuilder;
import edu.snu.vortex.compiler.ir.Edge;
import edu.snu.vortex.compiler.ir.component.operator.Broadcast;
import edu.snu.vortex.compiler.ir.component.Operator;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.Read;
import org.apache.beam.sdk.io.Write;
import org.apache.beam.sdk.runners.TransformHierarchy;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.values.PValue;

import java.util.HashMap;
import java.util.Map;

final class Visitor implements Pipeline.PipelineVisitor {
  private final DAGBuilder builder;
  private final Map<PValue, Operator> pValueToOpOutput;

  Visitor(final DAGBuilder builder) {
    this.builder = builder;
    this.pValueToOpOutput = new HashMap<>();
  }

  @Override
  public CompositeBehavior enterCompositeTransform(final TransformHierarchy.Node beamOperator) {
    // Print if needed for development
    // System.out.println("enter composite " + node.getTransform());
    return CompositeBehavior.ENTER_TRANSFORM;
  }

  @Override
  public void leaveCompositeTransform(final TransformHierarchy.Node beamOperator) {
    // Print if needed for development
    // System.out.println("leave composite " + node.getTransform());
  }

  @Override
  public void visitPrimitiveTransform(final TransformHierarchy.Node beamOperator) {
    // Print if needed for development
    // System.out.println("visitp " + beamOperator.getTransform());
    if (beamOperator.getOutputs().size() > 1 || beamOperator.getInputs().size() > 1)
      throw new UnsupportedOperationException(beamOperator.toString());

    final Operator vortexOperator = createOperator(beamOperator);
    builder.addOperator(vortexOperator);

    beamOperator.getOutputs()
        .forEach(output -> pValueToOpOutput.put(output, vortexOperator));

    beamOperator.getInputs().stream()
        .filter(pValueToOpOutput::containsKey)
        .map(pValueToOpOutput::get)
        .forEach(src -> builder.connectOperators(src, vortexOperator, getInEdgeType(vortexOperator)));
  }

  @Override
  public void visitValue(final PValue value, final TransformHierarchy.Node producer) {
    // Print if needed for development
    // System.out.println("visitv value " + value);
    // System.out.println("visitv producer " + producer.getTransform());
  }

  private <I, O> Operator createOperator(final TransformHierarchy.Node beamOperator) {
    final PTransform transform = beamOperator.getTransform();
    if (transform instanceof Read.Bounded) {
      final Read.Bounded<O> read = (Read.Bounded)transform;
      final SourceImpl<O> source = new SourceImpl<>(read.getSource());
      return source;
    } else if (transform instanceof GroupByKey) {
      return new GroupByKeyImpl();
    } else if (transform instanceof View.CreatePCollectionView) {
      final View.CreatePCollectionView view = (View.CreatePCollectionView)transform;
      final Broadcast vortexOperator = new BroadcastImpl(view.getView());
      pValueToOpOutput.put(view.getView(), vortexOperator);
      return vortexOperator;
    } else if (transform instanceof Write.Bound) {
      throw new UnsupportedOperationException(transform.toString());
    } else if (transform instanceof ParDo.Bound) {
      final ParDo.Bound<I, O> parDo = (ParDo.Bound<I, O>)transform;
      final DoImpl<I, O> vortexOperator = new DoImpl<>(parDo.getNewFn());
      parDo.getSideInputs().stream()
          .filter(pValueToOpOutput::containsKey)
          .map(pValueToOpOutput::get)
          .forEach(src -> builder.connectOperators(src, vortexOperator, Edge.Type.O2O)); // Broadcasted = O2O
      return vortexOperator;
    } else {
      throw new UnsupportedOperationException(transform.toString());
    }
  }

  private Edge.Type getInEdgeType(final Operator operator) {
    if (operator instanceof edu.snu.vortex.compiler.ir.component.operator.GroupByKey) {
      return Edge.Type.M2M;
    } else if (operator instanceof Broadcast) {
      return Edge.Type.O2M;
    } else {
      return Edge.Type.O2O;
    }
  }
}
