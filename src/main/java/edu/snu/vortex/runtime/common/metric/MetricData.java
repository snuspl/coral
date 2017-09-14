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
package edu.snu.vortex.runtime.common.metric;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.snu.vortex.runtime.exception.JsonParseException;

import java.util.Map;

/**
 * MetricData that holds executor side metrics.
 */
public class MetricData {
  private final Enum computationUnitEnum;
  private final String computationUnitId;
  private final ObjectMapper objectMapper;
  private final Map<String, Object> metrics;

  /**
   * Computation units to measure.
   */
  public enum ComputationUnit {
    JOB,
    STAGE,
    TASKGROUP,
    TASK
  }

  public MetricData(final Enum computationUnitEnum,
             final String computationUnitId,
             final Map<String, Object> metrics) {
    this.computationUnitEnum = computationUnitEnum;
    this.computationUnitId = computationUnitId;
    this.objectMapper = new ObjectMapper();
    this.metrics = metrics;
  }

  public final Enum getComputationUnitEnum() {
    return computationUnitEnum;
  }
  public final String getComputationUnitId() {
    return computationUnitId;
  }
  public final Map<String, Object> getMetrics() {
    return metrics;
  }

  public final String toJson() {
    try {
      final String jsonStr = objectMapper.writeValueAsString(this);
      return jsonStr;
    } catch (final Exception e) {
      throw new JsonParseException(e);
    }
  }
}
