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
package edu.snu.vortex.runtime.common.channel;


import edu.snu.vortex.compiler.ir.Element;

import java.util.List;

/**
 * Input channel interface.
 */
public interface InputChannel extends Channel {

  /**
   * @return the state of this input channel.
   */
  InputChannelState getState();

  /**
   * read data transferred from the respective {@link OutputChannel}.
   * if no data available, it immediately returns with null.
   * @return a list of data elements.
   */
  List<Element> read();

  /**
   * read data transferred from the respective {@link OutputChannel}.
   * if there is no data to read, it will be blocked until data get available.
   * @param timeout the timeout in millisecond.
   *                if the value is the maximum value of {@link Long}, it wait without timeout.
   * @return a list of data elements.
   */
  List<Element> read(long timeout);
}
