package org.enso.table.data.column.builder.object;

import java.util.BitSet;
import org.enso.table.data.column.storage.BoolStorage;
import org.enso.table.data.column.storage.Storage;

/** A builder for boolean columns. */
public class BoolBuilder extends TypedBuilder {
  private final BitSet vals = new BitSet();
  private final BitSet isNa = new BitSet();
  int size = 0;

  @Override
  public void appendNoGrow(Object o) {
    if (o == null) {
      isNa.set(size);
    } else {
      if ((Boolean) o) {
        vals.set(size);
      }
    }
    size++;
  }

  @Override
  public void append(Object o) {
    appendNoGrow(o);
  }

  /**
   * Append a new boolean to this builder.
   *
   * @param data the boolean to append
   */
  public void appendBoolean(boolean data) {
    if (data) {
      vals.set(size);
    }
    size++;
  }

  @Override
  public void appendNulls(int count) {
    isNa.set(size, size + count);
    size += count;
  }

  @Override
  public Storage seal() {
    return new BoolStorage(vals, isNa, size, false);
  }

  @Override
  public int getCurrentSize() {
    return size;
  }

  @Override
  public int getCurrentCapacity() {
    return vals.size();
  }

  @Override
  public void writeTo(Object[] items) {
    for (int i = 0; i < size; i++) {
      if (isNa.get(i)) {
        items[i] = null;
      } else {
        items[i] = vals.get(i);
      }
    }
  }

  @Override
  public boolean canRetypeTo(long type) {
    return false;
  }

  @Override
  public TypedBuilder retypeTo(long type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getType() {
    return Storage.Type.BOOL;
  }
}
