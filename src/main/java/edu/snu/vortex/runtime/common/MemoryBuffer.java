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
package edu.snu.vortex.runtime.common;


import java.nio.Buffer;
import java.nio.ByteBuffer;

public final class MemoryBuffer implements DataBuffer {
  private final int bufferId;
  private final ByteBuffer buffer;
  private final int bufferSize;
  private int dataSize;
  private int bufferSeek;

  MemoryBuffer(final int bufferId, final ByteBuffer buffer, final int bufferSize) {
    this.bufferId = bufferId;
    this.buffer = buffer;
    this.bufferSize = bufferSize;
    this.bufferSeek = 0;
    this.dataSize = 0;
  }

  public int getId() {
    return bufferId;
  }

  public int writeNext(final byte [] data, final int bufSizeInByte) {
    int writeDataSize = (bufSizeInByte > bufferSize - dataSize)? (bufferSize - dataSize): bufSizeInByte;
    buffer.put(data, dataSize, writeDataSize);
    dataSize += dataSize;

    return writeDataSize;
  }

  public int readNext(final byte [] readBuffer, final int bufSizeInByte) {
    int readDataSize = (bufSizeInByte > dataSize - bufferSeek)? (dataSize - bufferSeek): bufSizeInByte;
    buffer.get(readBuffer, bufferSeek, readDataSize);
    bufferSeek += readDataSize;

    return readDataSize;
  }

  public void seekFirst() {
    bufferSeek = 0;
  }

  public int getBufferSize() {
    return bufferSize;
  }

  public int getRemainingDataSize() {
    return (dataSize - bufferSeek);
  }

  public void clear() {
    buffer.clear();
    bufferSeek = 0;
    dataSize = 0;
  }
}
