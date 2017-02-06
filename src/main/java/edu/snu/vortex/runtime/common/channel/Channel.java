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


import java.util.List;

public interface Channel<T> {

  /**
   * initialize the internal state and the read/writer of the channel
   */
  void initialize();

  /**
   * return the id of the channel
   */
  String getId();

  /**
   * return the current state of the channel
   */
  ChannelState getState();

  /**
   * return the type {@link ChannelType} of the channel.
   */
  ChannelType getType();

  /**
   * return the channel mode {@link ChannelMode}.
   */
  ChannelMode getMode();

  /**
   * return the source task id of the channel
   */
  String getSrcTaskId();

  /**
   * return the destination task id of the channel
   */
  String getDstTaskId();

  //TODO: is it better to support channel writer/reader?
  /**
   * write data to the channel from a given byte buffer.
   * this method is available only when the channel mode is OUTPUT or INOUT.
   * @param data byte buffer of data to write
   */
  void write(List<T> data);

  /**
   * read data from the channel into a given byte buffer.
   * this method is available only when the channel mode is INPUT or INOUT.
   * @return the list of data read
   */
  List<T> read();

}
