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
package edu.snu.vortex.compiler.ir.attribute.edge;

import edu.snu.vortex.compiler.ir.attribute.ExecutionFactor;

/**
 * DataCommunicationPattern ExecutionFactor.
 */
public final class DataCommunicationPattern extends ExecutionFactor<String> {
  private DataCommunicationPattern(final String attribute) {
    super(Type.DataCommunicationPattern, attribute, String.class);
  }

  public static DataCommunicationPattern of(final String dataCommunicationPattern) {
    return new DataCommunicationPattern(dataCommunicationPattern);
  }

  // List of default pre-configured attributes.
  public static final String ONE_TO_ONE = "OneToOne";
  public static final String BROADCAST = "Broadcast";
  public static final String SCATTER_GATHER = "ScatterGather";
}