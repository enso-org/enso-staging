package org.enso.interpreter.runtime;

import com.google.common.collect.HashBiMap;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.enso.compiler.Compiler;
import org.enso.home.HomeManager;
import org.enso.interpreter.Language;
import org.enso.interpreter.OptionsHelper;
import org.enso.interpreter.runtime.builtin.Builtins;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.scope.TopLevelScope;
import org.enso.interpreter.runtime.type.Types.Pair;
import org.enso.interpreter.runtime.util.ShadowedPackage;
import org.enso.interpreter.runtime.util.TruffleFileSystem;
import org.enso.interpreter.util.ScalaConversions;
import org.enso.pkg.Package;
import org.enso.pkg.PackageManager;
import org.enso.pkg.QualifiedName;
import org.enso.polyglot.RuntimeOptions;

/**
 * The language context is the internal state of the language that is associated with each thread in
 * a running Enso program.
 */
public class Context {

  private final Language language;
  private final Env environment;
  private final Compiler compiler;
  private final PrintStream out;
  private final PrintStream err;
  private final InputStream in;
  private final BufferedReader inReader;
  private List<Package<TruffleFile>> packages;
  private @CompilationFinal TopLevelScope topScope;
  private final ThreadManager threadManager;
  private final ResourceManager resourceManager;
  private final boolean isCachingDisabled;
  private final Builtins builtins;
  private final String home;
  private final List<ShadowedPackage> shadowedPackages;

  /**
   * Creates a new Enso context.
   *
   * @param language the language identifier
   * @param environment the execution environment of the {@link TruffleLanguage}
   */
  public Context(Language language, String home, Env environment) {
    this.language = language;
    this.environment = environment;
    this.out = new PrintStream(environment.out());
    this.err = new PrintStream(environment.err());
    this.in = environment.in();
    this.inReader = new BufferedReader(new InputStreamReader(environment.in()));
    this.threadManager = new ThreadManager();
    this.resourceManager = new ResourceManager(this);
    this.isCachingDisabled = environment.getOptions().get(RuntimeOptions.DISABLE_INLINE_CACHES_KEY);
    this.home = home;
    this.shadowedPackages = new ArrayList<>();

    builtins = new Builtins(this);

    this.compiler = new Compiler(this, builtins);
  }

  /** Perform expensive initialization logic for the context. */
  public void initialize() {
    TruffleFileSystem fs = new TruffleFileSystem();
    packages = new ArrayList<>();
    HashMap<String, Package<TruffleFile>> packageMap = new HashMap<>();

    if (home != null) {
      HomeManager<TruffleFile> homeManager =
          new HomeManager<>(environment.getInternalTruffleFile(home), fs);
      packages.addAll(homeManager.loadStdLib().collect(Collectors.toList()));
    }

    PackageManager<TruffleFile> packageManager = new PackageManager<>(fs);

    List<TruffleFile> packagePaths = OptionsHelper.getPackagesPaths(environment);

    // Add user packages one-by-one, shadowing previously added packages. It assumes that the
    // standard library packages will not clash. In the future, we should be able to disambiguate
    // packages that clash.
    for (var packagePath : packagePaths) {
      var asPackage = ScalaConversions.asJava(packageManager.fromDirectory(packagePath));
      if (asPackage.isPresent()) {
        var pkg = asPackage.get();
        var nameExists =
            packages.stream().filter(p -> p.config().name().equals(pkg.name())).findFirst();
        nameExists.ifPresent(
            truffleFilePackage -> {
              shadowedPackages.add(
                  new ShadowedPackage(
                      truffleFilePackage.root().getPath(), pkg.root().getPath(), pkg.name()));
              packages.remove(truffleFilePackage);
            });
        packages.add(pkg);
      }
    }

    packages.forEach(
        pkg -> {
          List<TruffleFile> classPathItems =
              ScalaConversions.asJava(pkg.listPolyglotExtensions("java"));
          classPathItems.forEach(environment::addToHostClassPath);
        });

    Map<String, Module> knownFiles =
        packages.stream()
            .flatMap(p -> ScalaConversions.asJava(p.listSources()).stream())
            .collect(
                Collectors.toMap(
                    srcFile -> srcFile.qualifiedName().toString(),
                    srcFile -> new Module(srcFile.qualifiedName(), srcFile.file())));

    topScope = new TopLevelScope(builtins, knownFiles);
  }

  public TruffleFile getTruffleFile(File file) {
    return getEnvironment().getInternalTruffleFile(file.getAbsolutePath());
  }

  /**
   * Gets the compiler instance.
   *
   * <p>The compiler is the portion of the interpreter that performs static analysis and
   * transformation passes on the input program. A handle to the compiler lets you execute various
   * portions of the compilation pipeline, including parsing, analysis, and final code generation.
   *
   * <p>Having this access available means that Enso programs can metaprogram Enso itself.
   *
   * @return a handle to the compiler
   */
  public final Compiler getCompiler() {
    return compiler;
  }

  /**
   * Returns the {@link Env} instance used by this context.
   *
   * @return the {@link Env} instance used by this context
   */
  public Env getEnvironment() {
    return environment;
  }

  /**
   * Gets the language to which this context belongs.
   *
   * @return the language to which this context belongs
   */
  public Language getLanguage() {
    return language;
  }

  /**
   * Returns the standard output stream for this context.
   *
   * @return the standard output stream for this context.
   */
  public PrintStream getOut() {
    return out;
  }

  /**
   * Returns the standard error stream for this context.
   *
   * @return the standard error stream for this context
   */
  public PrintStream getErr() {
    return err;
  }

  /**
   * Returns the standard input stream for this context.
   *
   * @return the standard input stream of bytes.
   */
  public InputStream getIn() {
    return in;
  }

  /** @return the standard input stream of characters. */
  public BufferedReader getInReader() {
    return inReader;
  }

  /**
   * Fetches the module name associated with a given file, using the environment packages
   * information.
   *
   * @param path the path to decode.
   * @return a qualified name of the module corresponding to the file, if exists.
   */
  public Optional<QualifiedName> getModuleNameForFile(File path) {
    TruffleFile p = getTruffleFile(path);
    return packages.stream()
        .filter(pkg -> p.startsWith(pkg.sourceDir()))
        .map(pkg -> pkg.moduleNameForFile(p))
        .findFirst();
  }

  /**
   * Renames project in packages and modules.
   *
   * @param oldName the old project name
   * @param newName the new project name
   */
  public void renameProject(String oldName, String newName) {
    renamePackages(oldName, newName);
    topScope.renameProjectInModules(oldName, newName);
  }

  private void renamePackages(String oldName, String newName) {
    List<Package<TruffleFile>> toChange =
        packages.stream()
            .filter(p -> p.config().name().equals(oldName))
            .collect(Collectors.toList());

    packages.removeAll(toChange);

    List<Package<TruffleFile>> renamed =
        toChange.stream().map(p -> p.setPackageName(newName)).collect(Collectors.toList());

    packages.addAll(renamed);
  }

  /**
   * Fetches a module associated with a given file.
   *
   * @param path the module path to lookup.
   * @return the relevant module, if exists.
   */
  public Optional<Module> getModuleForFile(File path) {
    return getModuleNameForFile(path).flatMap(n -> getTopScope().getModule(n.toString()));
  }

  /**
   * Fetches a module with a given name.
   *
   * @param moduleName the qualified name of the module to lookup.
   * @return the relevant module, if exists.
   */
  public Optional<Module> findModule(String moduleName) {
    return getTopScope().getModule(moduleName);
  }

  /**
   * Finds the package the provided module belongs to.
   *
   * @param module the module to find the package of
   * @return {@code module}'s package, if exists
   */
  public Optional<Package<TruffleFile>> getPackageOf(Module module) {
    if (module.getSourceFile() == null) {
      return Optional.empty();
    }
    return packages.stream()
        .filter(
            pkg ->
                module.getSourceFile().getAbsoluteFile().startsWith(pkg.root().getAbsoluteFile()))
        .findFirst();
  }

  /**
   * Registers a new module corresponding to a given file.
   *
   * @param path the file to register.
   * @return the newly created module, if the file is a source file.
   */
  public Optional<Module> createModuleForFile(File path) {
    return getModuleNameForFile(path)
        .map(name -> getTopScope().createModule(name, getTruffleFile(path)));
  }

  /**
   * Gets the builtin functions from the compiler.
   *
   * @return an object containing the builtin functions
   */
  public Builtins getBuiltins() {
    return getTopScope().getBuiltins();
  }

  /**
   * Gets the top-level language scope.
   *
   * @return an object containing the top level language scope
   */
  public TopLevelScope getTopScope() {
    return this.topScope;
  }

  /**
   * Returns the atom constructor corresponding to the {@code Nothing} type, for builtin constructs
   * that need to return an atom of this type.
   *
   * @return the builtin {@code Nothing} atom constructor
   */
  public AtomConstructor getNothing() {
    return getBuiltins().nothing();
  }

  /**
   * Checks whether the strict errors option was set for this context.
   *
   * @return true if the strict errors option is enabled, false otherwise.
   */
  public boolean isStrictErrors() {
    return getEnvironment().getOptions().get(RuntimeOptions.STRICT_ERRORS_KEY);
  }

  /** Creates a new thread that has access to the current language context. */
  public Thread createThread(Runnable runnable) {
    return environment.createThread(runnable);
  }

  /** @return the thread manager for this context. */
  public ThreadManager getThreadManager() {
    return threadManager;
  }

  /** @return the resource manager for this context */
  public ResourceManager getResourceManager() {
    return resourceManager;
  }

  /** @return whether inline caches should be disabled for this context. */
  public boolean isCachingDisabled() {
    return isCachingDisabled;
  }

  /** @return the list of shadowed packages */
  public List<ShadowedPackage> getShadowedPackages() {
    return shadowedPackages;
  }
}
