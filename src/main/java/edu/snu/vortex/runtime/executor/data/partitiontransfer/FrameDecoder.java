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
package edu.snu.vortex.runtime.executor.data.partitiontransfer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * Interprets inbound byte streams to compose frames.
 *
 * More specifically,
 * <ul>
 *   <li>Recognizes the type of the frame, namely control or data.</li>
 *   <li>If the received bytes are a part of a control frame, waits until the full content of the frame becomes
 *   available and decode the frame to emit a control frame object.</li>
 *   <li>If the received bytes consists a data frame, supply the data to the corresponding {@link PartitionInputStream}.
 *   <li>If the end of a data message is recognized, closes the corresponding {@link PartitionInputStream}.</li>
 * </ul>
 *
 * Control frame specification:
 * <pre>
 * {@literal
 *   <------------ HEADER -----------> <----- BODY ----->
 *   +---------+------------+---------+-------...-------+
 *   |   Type  |   Unused   |  Length |       Body      |
 *   | 2 bytes |  2 bytes   | 4 bytes | Variable length |
 *   +---------+------------+---------+-------...-------+
 * }
 * </pre>
 *
 * Data frame specification:
 * <pre>
 * {@literal
 *   <------------ HEADER -----------> <----- BODY ----->
 *   +---------+------------+---------+-------...-------+
 *   |   Type  | TransferId |  Length |       Body      |
 *   | 2 bytes |  2 bytes   | 4 bytes | Variable length |
 *   +---------+------------+---------+-------...-------+
 * }
 * </pre>
 *
 * Literals used in frame header:
 * <ul>
 *   <li>Type
 *     <ul>
 *       <li>0: control frame</li>
 *       <li>2: data frame for pull-based transfer</li>
 *       <li>3: data frame for pull-based transfer, the last frame of the message</li>
 *       <li>4: data frame for push-based transfer</li>
 *       <li>5: data frame for push-based transfer, the last frame of the message</li>
 *     </ul>
 *   </li>
 *   <li>TransferId: the transfer id to distinguish which transfer this frame belongs to</li>
 *   <li>Length: the number of bytes in the body, not the entire frame</li>
 * </ul>
 *
 * @see ChannelInitializer
 */
final class FrameDecoder extends ByteToMessageDecoder {

  static final short CONTROL_TYPE = 0;
  static final short PULL_NONENDING = 2;
  static final short PULL_ENDING = 3;
  static final short PUSH_NONENDING = 4;
  static final short PUSH_ENDING = 5;

  /**
   * The number of bytes consisting body of a data frame to be read next.
   * Decoder expects beginning of a frame if this value is 0.
   */
  private int dataBodyBytesToRead = 0;

  @Override
  protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) {
    // TODO #1: there can be multiple frames in single ByteBuf. Repeat until no decoding is available.
    // TODO #1: there can be multiple data frames in single ByteBuf. Make a derived *retained* buffer


  }

  /**
   * Decode a new frame.
   *
   * @param in  the {@link ByteBuf} from which to read data
   * @param out the {@link List} to which decoded messages are added
   * @return {@code true} if a header was decoded, {@code false} otherwise
   */
  private boolean onFrameBegins(final ByteBuf in, final List<Object> out) {
    return true;
  }

  /**
   * Supply byte stream to an existing {@link PartitionInputStream}.
   *
   * @param in  the {@link ByteBuf} from which to read data
   */
  private void onDataBodyAdded(final ByteBuf in) {

  }
}
