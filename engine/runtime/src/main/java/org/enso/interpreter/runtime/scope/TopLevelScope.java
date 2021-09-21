package org.enso.interpreter.runtime.scope;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import org.enso.compiler.Compiler;
import org.enso.compiler.PackageRepository;
import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.Module;
import org.enso.interpreter.runtime.builtin.Builtins;
import org.enso.interpreter.runtime.data.Array;
import org.enso.interpreter.runtime.type.Types;
import org.enso.interpreter.util.ScalaConversions;
import org.enso.pkg.Package;
import org.enso.pkg.QualifiedName;
import org.enso.polyglot.MethodNames;

/** Represents the top scope of Enso execution, containing all the importable modules. */
@ExportLibrary(InteropLibrary.class)
public class TopLevelScope implements TruffleObject {
  private final Builtins builtins;
  private final PackageRepository packageRepository;

  /**
   * Creates a new instance of top scope.
   *
   * @param builtins the automatically-imported builtin module.
   * @param packageRepository the {@link PackageRepository} instance that manages loaded packages
   */
  public TopLevelScope(Builtins builtins, PackageRepository packageRepository) {
    this.builtins = builtins;
    this.packageRepository = packageRepository;
  }

  /** @return the list of modules in the scope. */
  public Collection<Module> getModules() {
    return ScalaConversions.asJava(packageRepository.getLoadedModules());
  }

  /**
   * Looks up a module by name.
   *
   * @param name the name of the module to look up.
   * @return empty result if the module does not exist or the requested module.
   */
  public Optional<Module> getModule(String name) {
    return ScalaConversions.asJava(packageRepository.getLoadedModule(name));
  }

  /**
   * Creates and registers a new module with given name and source file.
   *
   * @param name the module name.
   * @param sourceFile the module source file.
   * @return the newly created module.
   */
  public Module createModule(QualifiedName name, Package<TruffleFile> pkg, TruffleFile sourceFile) {
    Module module = new Module(name, pkg, sourceFile);
    packageRepository.registerModuleCreatedInRuntime(module);
    return module;
  }

  /**
   * Returns the builtins module.
   *
   * @return the builtins module.
   */
  public Builtins getBuiltins() {
    return builtins;
  }

  /**
   * Marks this object as having members accessible through the polyglot API.
   *
   * @return {@code true}
   */
  @ExportMessage
  boolean hasMembers() {
    return true;
  }

  /**
   * Returns a collection of all the members of this scope.
   *
   * <p>The exported members are the {@code get_module} and {@code create_module} methods.
   *
   * @param includeInternal ignored.
   * @return a collection of all the exported members.
   */
  @ExportMessage
  Array getMembers(boolean includeInternal) {
    return new Array(
        MethodNames.TopScope.GET_MODULE,
        MethodNames.TopScope.CREATE_MODULE,
        MethodNames.TopScope.REGISTER_MODULE,
        MethodNames.TopScope.UNREGISTER_MODULE,
        MethodNames.TopScope.COMPILE_PACKAGES);
  }

  /** Handles member invocation through the polyglot API. */
  @ExportMessage
  abstract static class InvokeMember {
    private static Module getModule(
        TopLevelScope scope,
        Object[] arguments,
        TruffleLanguage.ContextReference<Context> contextReference)
        throws ArityException, UnsupportedTypeException, UnknownIdentifierException {
      String moduleName = Types.extractArguments(arguments, String.class);

      var module = scope.getModule(moduleName);
      if (module.isEmpty()) {
        throw UnknownIdentifierException.create(moduleName);
      }

      return module.get();
    }

    private static Module createModule(TopLevelScope scope, Object[] arguments, Context context)
        throws ArityException, UnsupportedTypeException {
      String moduleName = Types.extractArguments(arguments, String.class);
      return Module.empty(QualifiedName.simpleName(moduleName), null);
    }

    private static Module registerModule(TopLevelScope scope, Object[] arguments, Context context)
        throws ArityException, UnsupportedTypeException {
      Types.Pair<String, String> args =
          Types.extractArguments(arguments, String.class, String.class);
      QualifiedName qualName = QualifiedName.fromString(args.getFirst());
      File location = new File(args.getSecond());
      Module module = new Module(qualName, null, context.getTruffleFile(location));
      scope.packageRepository.registerModuleCreatedInRuntime(module);
      return module;
    }

    private static Object unregisterModule(TopLevelScope scope, Object[] arguments, Context context)
        throws ArityException, UnsupportedTypeException {
      String name = Types.extractArguments(arguments, String.class);
      scope.packageRepository.deregisterModule(name);
      return context.getNothing().newInstance();
    }

    private static Object leakContext(Context context) {
      return context.getEnvironment().asGuestValue(context);
    }

    private static Object compilePackages(Object[] arguments, Context context)
        throws UnsupportedTypeException, ArityException {

      String[] packageRoots = Types.extractHostArguments(arguments, String[].class, context);



      return Boolean.TRUE;
    }

    @Specialization
    static Object doInvoke(
        TopLevelScope scope,
        String member,
        Object[] arguments,
        @CachedContext(Language.class) TruffleLanguage.ContextReference<Context> contextRef)
        throws UnknownIdentifierException, ArityException, UnsupportedTypeException {
      switch (member) {
        case MethodNames.TopScope.GET_MODULE:
          return getModule(scope, arguments, contextRef);
        case MethodNames.TopScope.CREATE_MODULE:
          return createModule(scope, arguments, contextRef.get());
        case MethodNames.TopScope.REGISTER_MODULE:
          return registerModule(scope, arguments, contextRef.get());
        case MethodNames.TopScope.UNREGISTER_MODULE:
          return unregisterModule(scope, arguments, contextRef.get());
        case MethodNames.TopScope.LEAK_CONTEXT:
          return leakContext(contextRef.get());
        case MethodNames.TopScope.COMPILE_PACKAGES:
          return compilePackages(arguments, contextRef.get());
        default:
          throw UnknownIdentifierException.create(member);
      }
    }
  }

  /**
   * Checks if a member can be invoked through the polyglot API.
   *
   * @param member the member name.
   * @return {@code true} if the member is invocable, {@code false} otherwise.
   */
  @ExportMessage
  boolean isMemberInvocable(String member) {
    return member.equals(MethodNames.TopScope.GET_MODULE)
        || member.equals(MethodNames.TopScope.CREATE_MODULE)
        || member.equals(MethodNames.TopScope.REGISTER_MODULE)
        || member.equals(MethodNames.TopScope.UNREGISTER_MODULE)
        || member.equals(MethodNames.TopScope.COMPILE_PACKAGES);
  }

  /**
   * Checks if the receiver is a scope object.
   *
   * @return {@code true}
   */
  @ExportMessage
  boolean isScope() {
    return true;
  }

  /**
   * Returns true if this scope has an enclosing parent scope, else false.
   *
   * @return {@code false}
   */
  @ExportMessage
  boolean hasScopeParent() {
    return false;
  }

  /**
   * Returns the parent scope of this scope, if one exists.
   *
   * @return {@code null}
   * @throws UnsupportedMessageException always, as this scope can never have a parent
   */
  @ExportMessage
  Object getScopeParent() throws UnsupportedMessageException {
    return UnsupportedMessageException.create();
  }

  /**
   * Checks if this value is associated with a language.
   *
   * @return {@code true}
   */
  @ExportMessage
  final boolean hasLanguage() {
    return true;
  }

  /**
   * Returns the language associated with this scope value.
   *
   * @return the language with which this value is associated
   */
  @ExportMessage
  final Class<Language> getLanguage() {
    return Language.class;
  }

  /**
   * Converts this scope to a human readable string.
   *
   * @param allowSideEffects whether or not side effects are allowed
   * @return a string representation of this scope
   */
  @ExportMessage
  final Object toDisplayString(boolean allowSideEffects) {
    return "Enso.Top_Scope";
  }
}
