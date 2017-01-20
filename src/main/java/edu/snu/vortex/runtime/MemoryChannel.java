package edu.snu.vortex.runtime;

import java.util.List;

public class MemoryChannel implements Channel {
  List data;

  @Override
  public void write(List data) {
    System.out.println("Channel WRITE: " + data);
    this.data = data;
  }

  @Override public List read() {
    System.out.println("Channel READ: " + data);
    return this.data;
  }
}
