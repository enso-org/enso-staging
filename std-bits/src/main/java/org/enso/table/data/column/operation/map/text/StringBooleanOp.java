package org.enso.table.data.column.operation.map.text;

import java.util.BitSet;
import org.enso.table.data.column.operation.map.MapOperation;
import org.enso.table.data.column.storage.BoolStorage;
import org.enso.table.data.column.storage.Storage;
import org.enso.table.data.column.storage.StringStorage;
import org.enso.table.error.UnexpectedTypeException;

public abstract class StringBooleanOp extends MapOperation<StringStorage> {
  public StringBooleanOp(String name) {
    super(name);
  }

  protected abstract boolean doString(String a, String b);

  protected boolean doObject(String a, Object o) {
    throw new UnexpectedTypeException("a Text");
  }

  @Override
  public Storage runMap(StringStorage storage, Object arg) {
    if (arg == null) {
      BitSet newVals = new BitSet();
      BitSet newMissing = new BitSet();
      newMissing.set(0, storage.size());
      return new BoolStorage(newVals, newMissing, storage.size(), false);
    } else if (arg instanceof String) {
      String argString = (String) arg;
      BitSet newVals = new BitSet();
      BitSet newMissing = new BitSet();
      for (int i = 0; i < storage.size(); i++) {
        if (storage.isNa(i)) {
          newMissing.set(i);
        } else if (doString(storage.getItem(i), argString)) {
          newVals.set(i);
        }
      }
      return new BoolStorage(newVals, newMissing, storage.size(), false);
    } else {
      BitSet newVals = new BitSet();
      BitSet newMissing = new BitSet();
      for (int i = 0; i < storage.size(); i++) {
        if (storage.isNa(i)) {
          newMissing.set(i);
        } else if (doObject(storage.getItem(i), arg)) {
          newVals.set(i);
        }
      }
      return new BoolStorage(newVals, newMissing, storage.size(), false);
    }
  }

  @Override
  public Storage runZip(StringStorage storage, Storage arg) {
    if (arg instanceof StringStorage) {
      StringStorage v = (StringStorage) arg;
      BitSet newVals = new BitSet();
      BitSet newMissing = new BitSet();
      for (int i = 0; i < storage.size(); i++) {
        if (!storage.isNa(i) && i < v.size() && !v.isNa(i)) {
          if (doString(storage.getItem(i), v.getItem(i))) {
            newVals.set(i);
          }
        } else {
          newMissing.set(i);
        }
      }
      return new BoolStorage(newVals, newMissing, storage.size(), false);
    } else {
      BitSet newVals = new BitSet();
      BitSet newMissing = new BitSet();
      for (int i = 0; i < storage.size(); i++) {
        if (!storage.isNa(i) && i < arg.size() && !arg.isNa(i)) {
          Object x = arg.getItemBoxed(i);
          if (x instanceof String) {
            if (doString(storage.getItem(i), (String) x)) {
              newVals.set(i);
            }
          } else {
            if (doObject(storage.getItem(i), x)) {
              newVals.set(i);
            }
          }
        } else {
          newMissing.set(i);
        }
      }
      return new BoolStorage(newVals, newMissing, storage.size(), false);
    }
  }
}
