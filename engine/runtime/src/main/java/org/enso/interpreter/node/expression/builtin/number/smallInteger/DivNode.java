package org.enso.interpreter.node.expression.builtin.number.smallInteger;

import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.Language;
import org.enso.interpreter.dsl.BuiltinMethod;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.builtin.Builtins;
import org.enso.interpreter.runtime.callable.atom.Atom;
import org.enso.interpreter.runtime.error.DataflowError;
import org.enso.interpreter.runtime.error.PanicException;
import org.enso.interpreter.runtime.number.EnsoBigInteger;

@BuiltinMethod(type = "Small_Integer", name = "div", description = "Division of numbers.")
public abstract class DivNode extends Node {
  abstract Object execute(long _this, Object that);

  static DivNode build() {
    return DivNodeGen.create();
  }

  @Specialization
  Object doLong(
      long _this, long that, @CachedContext(Language.class) ContextReference<Context> ctxRef) {
    try {
      return _this / that;
    } catch (ArithmeticException e) {
      return DataflowError.withoutTrace(
          ctxRef.get().getBuiltins().error().getDivideByZeroError(), this);
    }
  }

  @Specialization
  Object doBigInteger(long _this, EnsoBigInteger that) {
    // No need to trap, as 0 is never represented as an EnsoBigInteger.
    return 0L;
  }

  @Fallback
  Object doOther(long _this, Object that) {
    Builtins builtins = lookupContextReference(Language.class).get().getBuiltins();
    Atom integer = builtins.number().getInteger().newInstance();
    throw new PanicException(builtins.error().makeTypeError(integer, that, "that"), this);
  }
}
