package org.enso.interpreter.node.expression.builtin.number.decimal;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.dsl.BuiltinMethod;
import org.enso.interpreter.node.expression.builtin.number.utils.BigIntegerOps;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.builtin.Builtins;
import org.enso.interpreter.runtime.callable.atom.Atom;
import org.enso.interpreter.runtime.error.PanicException;
import org.enso.interpreter.runtime.number.EnsoBigInteger;

@BuiltinMethod(type = "Decimal", name = "/", description = "Division of numbers.")
public abstract class DivideNode extends Node {
  abstract double execute(double _this, Object that);

  static DivideNode build() {
    return DivideNodeGen.create();
  }

  @Specialization
  double doDouble(double _this, double that) {
    return _this / that;
  }

  @Specialization
  double doLong(double _this, long that) {
    return _this / that;
  }

  @Specialization
  double doBigInteger(double _this, EnsoBigInteger that) {
    return _this / BigIntegerOps.toDouble(that.getValue());
  }

  @Fallback
  double doOther(double _this, Object that) {
    Builtins builtins = Context.get(this).getBuiltins();
    Atom number = builtins.number().getNumber().newInstance();
    throw new PanicException(builtins.error().makeTypeError(number, that, "that"), this);
  }
}
