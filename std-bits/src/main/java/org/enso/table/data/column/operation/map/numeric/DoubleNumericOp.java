package org.enso.table.data.column.operation.map.numeric;

import org.enso.table.data.column.operation.map.MapOperation;
import org.enso.table.data.column.storage.DoubleStorage;
import org.enso.table.data.column.storage.LongStorage;
import org.enso.table.data.column.storage.Storage;
import org.enso.table.error.UnexpectedTypeException;

import java.util.BitSet;

/** An operation expecting a numeric argument and returning a number. */
public abstract class DoubleNumericOp extends MapOperation<DoubleStorage> {

  public DoubleNumericOp(String name) {
    super(name);
  }

  protected abstract double doDouble(double a, double b);

  @Override
  public Storage runMap(DoubleStorage storage, Object arg) {
    double x;
    if (arg instanceof Double) {
      x = (Double) arg;
    } else if (arg instanceof Long) {
      x = (Long) arg;
    } else {
      throw new UnexpectedTypeException("a Number.");
    }
    long[] out = new long[storage.size()];
    for (int i = 0; i < storage.size(); i++) {
      if (!storage.isNa(i)) {
        out[i] = Double.doubleToRawLongBits(doDouble(storage.getItem(i), x));
      }
    }
    return new DoubleStorage(out, storage.size(), storage.getIsMissing());
  }

  @Override
  public Storage runZip(DoubleStorage storage, Storage arg) {
    if (arg instanceof LongStorage) {
      LongStorage v = (LongStorage) arg;
      long[] out = new long[storage.size()];
      BitSet newMissing = new BitSet();
      for (int i = 0; i < storage.size(); i++) {
        if (!storage.isNa(i) && i < v.size() && !v.isNa(i)) {
          out[i] = Double.doubleToRawLongBits(doDouble(storage.getItem(i), v.getItem(i)));
        } else {
          newMissing.set(i);
        }
      }
      return new DoubleStorage(out, storage.size(), newMissing);
    } else if (arg instanceof DoubleStorage) {
      DoubleStorage v = (DoubleStorage) arg;
      long[] out = new long[storage.size()];
      BitSet newMissing = new BitSet();
      for (int i = 0; i < storage.size(); i++) {
        if (!storage.isNa(i) && i < v.size() && !v.isNa(i)) {
          out[i] = Double.doubleToRawLongBits(doDouble(storage.getItem(i), v.getItem(i)));
        } else {
          newMissing.set(i);
        }
      }
      return new DoubleStorage(out, storage.size(), newMissing);
    } else {
      throw new UnexpectedTypeException("a Number.");
    }
  }
}
