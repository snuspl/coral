package edu.snu.vortex.runtime.executor.block;

import edu.snu.vortex.compiler.ir.Element;

import java.util.HashMap;

/**
 * Store data in local memory, unserialized.
 */
public final class LocalBlockStorage implements BlockStorage {
  private final HashMap<String, Iterable<Element>> blockIdToData;

  public LocalBlockStorage() {
    this.blockIdToData = new HashMap<>();
  }

  public Iterable<Element> getBlock(final String blockId) {
    return blockIdToData.get(blockId);
  }

  public void putBlock(final String blockId, final Iterable<Element> data) {
    blockIdToData.put(blockId, data);
  }
}
