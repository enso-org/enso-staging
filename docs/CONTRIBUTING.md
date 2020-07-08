---
layout: developer-doc
title: Contributing to Enso
category: summary
tags: [summary, contributing]
order: 2
---

# Contributing to Enso
Thank you for your interest in contributing to Enso! We believe that only
through community involvement can Enso be the best it can be! There are a whole
host of ways to contribute, and every single one is appreciated. The major
sections of this document are linked below:

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [The Contributor License Agreement](#the-contributor-license-agreement)
- [Issues](#issues)
- [Feature Enhancements](#feature-enhancements)
- [Bug Reports](#bug-reports)
- [Hacking on Enso](#hacking-on-enso)
  - [Design Documentation](#design-documentation)
  - [System Requirements](#system-requirements)
  - [Getting the Sources](#getting-the-sources)
  - [Getting Set Up (Rust)](#getting-set-up-rust)
  - [Getting Set Up (JVM)](#getting-set-up-jvm)
  - [Building Enso](#building-enso)
  - [Running Enso](#running-enso)
- [Pull Requests](#pull-requests)
- [Documentation](#documentation)
- [Issue Triage](#issue-triage)
- [Out-of-Tree Contributions](#out-of-tree-contributions)
- [Helpful Documentation and Links](#helpful-documentation-and-links)


<!-- /MarkdownTOC -->

All contributions to Enso should be in keeping with our
[Code of Conduct](./CODE_OF_CONDUCT.md).

## The Contributor License Agreement
As part of your first contribution to this repository, you need to accept the
Contributor License Agreement. You will automatically be asked to sign the CLA
when you make your first pull request.

Any work intentionally submitted for inclusion in Enso shall be licensed under
this CLA.

The CLA you sign applies to all repositories associated with the Enso project,
so you will only have to sign it once at the start of your contributions.

## Issues
If you're wanting to get involved with Enso's development and are looking for
somewhere to start, you can check out the following tags in our issues:

- [Good First Issue](https://github.com/enso-org/enso/labels/Status%3A%20Good%20First%20Issue)
- [Help Wanted](https://github.com/enso-org/enso/labels/Status%3A%20Help%20Wanted)

You can use the "Size" and "Difficulty" labels that should be assigned to every
issue to get a better idea of how much work a given issue might be.

## Feature Enhancements
If you feel like you have a suggestion for a change to the way that Enso works
as a language, please take a look at the
[Enso RFC process](./rfcs/README.md) to learn how to file an RFC for the
project.

In essence, the RFC process provides a way to propose major changes to the
language, the compiler, and the runtime in a way that ensures that they get
seen and discussed by all the major stakeholders involved.

If, on the other hand, you're asking for a smaller feature, please feel free to
submit a [feature request](https://github.com/enso-org/enso/issues/new?assignees=&labels=Type%3A+Enhancement&template=feature-request.md&title=)
to the repository.

## Bug Reports
While it's never great to find a bug, they are a reality of software and
software development! We can't fix or improve on the things that we don't know
about, so report as many bugs as you can! If you're not sure whether something
is a bug, file it anyway!

**If you are concerned that your bug publicly presents a security risk to the
users of Enso, please look at our [security guidelines](./SECURITY.md).**

Even though GitHub search can be a bit hard to use sometimes, we'd appreciate if
you could
[search](https://github.com/enso-org/enso/search?q=&type=Issues&utf8=%E2%9C%93) for
your issue before filing a bug as it's possible that someone else has already
reported the issue. We know the search isn't the best, and it can be hard to
know what to search for, so we really don't mind if you do submit a duplicate!

Opening an issue is as easy as following
[this link](https://github.com/enso-org/enso/issues/new?template=bug-report.md)
and filling out the fields. The template is intended to collect all the
information we need to best diagnose the issue, so please take the time to fill
it out accurately.

The reproduction steps are particularly important, as the more easily we can
reproduce it, the faster we can fix the bug! It's also helpful to have the
output of `enso --version`, as that will let us know if the bug is Operating
System or Architecture specific.

## Hacking on Enso
This will get you up and running for Enso development, with only a minimal
amount of setup required. Enso's build system is fairly simple, allowing you to
bootstrap the compiler as long as you have a minimal set of tools.

### Design Documentation
If you're going to start contributing to Enso, it is often a good idea to take a
look at the design documentation for the language. These files explain provide
both a rigorous specification of Enso's design, but also insight into the _why_
behind the decisions that have been made.

These can be found in [`docs/`](README.md), and are organised by the part of the
compiler that they relate to.

### System Requirements
In order to build and run Enso you will need the following tools:

- [sbt](https://www.scala-sbt.org/) with the same version as specified in
  [`project/build.properties`](../project/build.properties).
- [GraalVM](https://www.graalvm.org/) with the same version as described in the
  [`build.sbt`](../build.sbt) file, configured as your default JVM. GraalVM is
  distributed for different Java versions, so you need a GraalVM distribution
  for the same Java version as specified in [`build.sbt`](../build.sbt).
- [Flatbuffers Compiler](https://google.github.io/flatbuffers) with version
  1.12.0.
- [Cargo](https://doc.rust-lang.org/cargo/getting-started/installation.html),
  the rust build tool.
- [Rustup](https://rustup.rs), the rust toolchain management utility.

Managing multiple JVM installations can be a pain, so some of the team use
[Jenv](http://www.jenv.be/): A useful tool for managing multiple JVMs.

The flatbuffers `flatc` compiler can be installed from the following locations:

- Using the `conda` package manager (`conda install flatbuffers`). This will
  work on all platforms, but requires some knowledge of `conda` and how its
  environments work.
- Windows users can download binaries directly from the flatbuffers github
  [releases](https://github.com/google/flatbuffers/releases).
- MacOS users can install it via homebrew (`brew install flatbuffers`).

### Getting the Sources
Given you've probably been reading this document on GitHub, you might have an
inkling where to look!. You can clone Enso using two methods:

- **Via HTTPS:** We recommend you only use HTTPS if checking out the sources as
  read-only.

```
git clone https://github.com/enso-org/enso.git
```

- **Via SSH:** For those who plan on regularly making direct commits, cloning
  over SSH may provide a better user experience (but requires setting up your
  SSH Keys with GitHub).

```
git clone git@github.com:enso-org/enso.git
```

### Getting Set Up (Rust)
This project currently requires a specific nightly rust toolchain, as well as a
special set-up step in SBT. To get this project set up, you can run the
following commands:

```bash
rustup toolchain install nightly-2019-11-04
rustup override set nightly-2019-11-04
rustup component add clippy
```

You will also need `node` in order to run the `wasm` tests. We only support the
latest LTS version of [NodeJS](https://nodejs.org/en/download) and NPM. We
recommend using [`nvm`](https://github.com/nvm-sh/nvm) to manage node versions.
The current LTS is `v12.18.0`.

Please note that once the parser is integrated into the SBT build, the
rust-related commands will be automatically performed for you.

### Getting Set Up (JVM)
In order to properly build the `runtime` component, the JVM running SBT needs
to have some dependency JARs available in its module path at startup. To ensure
they are available, before running any compilation or other tasks, these
dependencies should be prepared. To do so, run the following command in the
repository root directory:

```bash
sbt bootstrap
```

It is preferred to not run this command from the sbt shell, but in batch mode,
because SBT has to be launched again anyway to pick up these JARs at startup.

Bootstrap has to be run only when building the project for the first time
**and** after each change of Graal version.

### Building Enso
There are multiple projects in this repository, but all can be built, run and
tested using `sbt`. As long as your configuration is correct, with the correct
versions of SBT and GraalVM, the same steps can be followed on all of our
supported platforms (Linux, MacOS and Windows).

SBT will handle downloading and building library dependencies as needed, meaning
that you don't need to handle any of this manually.

**Please note** that at the current time, the Windows build of GraalVM is in an
experimental state. This means that while it may function, we are not intending
to provide work-arounds for building on that platform while it is still in an
unstable state.

#### Building Enso Components
In order to build a specific component (e.g. `runtime`), please follow the
following steps.

1. Enter the sbt shell in the repository root directory by typing `sbt`.
2. Change to the project you are concerned with (in our case `runtime`) by
   executing `project runtime`.
3. Execute `compile` in order to compile the project in question. This will
   compile the project and all its dependencies as necessary.

You can substitute both `bench` and `test` for `compile` in step 3, and the sbt
shell will execute the appropriate thing. Furthermore we have `testOnly` and
`benchOnly` that accept a glob pattern that delineates some subset of the tests
or benchmarks to run (e.g. `testOnly *FunctionArguments*`).

#### Building the Interpreter CLI Fat Jar
In order to build a fat jar with the CLI component, run the `assembly` task
inside the `runner` subproject:

```bash
sbt "runner/assembly"
```

This will produce an executable `enso.jar` fat jar in the repository root.
It's self contained, with its only dependencies being available inside
a vanilla GraalVM distribution. To run it, use:

```bash
JAVA_HOME=<PATH_TO_GRAAL_HOME> ./enso.jar <CLI_ARGS>
```

#### Building the Launcher Native Binary
If you want to build the native launcher binary, you need to ensure that the
Native Image component is installed in your GraalVM distribution. To install it,
run:

```bash
gu install native-image
```

Then, you can build the launcher using:

```bash
sbt launcher/buildNativeImage
```

#### Testing Enso
Running the tests for the JVM enso components is as simple as running
`sbt / test`. To test the Rust components you can run `cargo test`. Finally, you
can run the WASM tests for the rust components by using `./run --test-wasm`.

#### Installing the Jupyter kernel
Enso has a highly experimental and not-actively-maintained Jupyer Kernel.
To run it:

1. Build (or download from the CI server) the CLI Fat Jar.
2. Fill in the `engine/language-server/jupyter-kernel/enso/kernel.json`
   file, providing correct paths to the `enso.jar` distribution and GraalVM
   `JAVA_HOME`.
3. Run:

```bash
jupyter kernelspec install <ROOT_OF_THIS_REPO>/engine/language-server/jupyter-kernel/enso
```

Congratulations, your Jupyter Kernel should now be installed and ready to use.

#### Passing Debug Options
GraalVM provides some useful debugging options, including the ability to output
the compilation graph during JIT optimisation, and the ASM generated by the JIT.

However, as we don't want these things polluting our standard builds, we provide
a helper SBT command `withDebug` to allow for passing these options. It supports
the following flags:

- `--dumpGraphs`: This dumps the IGV (a Graal tool) graphs for the program to
  allow for manual analysis and discovery of optimisation failures.
- `--showCompilations`: Prints the truffle compilation trace information.
- `--printAssembly`: Prints the assembly output from the HotSpot JIT tier.

For more information on this sbt command, please see
[WithDebugCommand.scala](../project/WithDebugCommand.scala).

It is used as an addendum to the basic sbt command you want to run (e.g. `test`
from above). The format is `withDebug COMMAND [OPTIONS...]`, and if you need to
pass any additional options to `COMMAND` you must do so following a `--`. For
example:

```
withDebug run --dumpGraphs --printAssembly -- --run MyFile.enso
withDebug benchOnly --showCompilations -- RecursionBenchmark
```

#### Working with Assembly
In order to examine the assembly generated by GraalVM and HotSpot you need to
provide your JVM install with a dynamic library that supports the dumping of
assembly. It can be acquired for MacOS and Linux
[here](https://github.com/liuzhengyang/hsdis/), and for windows from
[here](http://fcml-lib.com/). There are other methods to acquire it, as well, so
please choose one best suited for you.

Once you have a copy of the dynamic library, it needs to be placed in
`$JVM_HOME/lib/server`.


#### Native Image
Native image is a capability provided alongside GraalVM that allows the
generation of native executables from JVM language programs (such as the Enso
interpreter itself). However, it results in significantly degraded peak
performance, so it is not part of our roadmap currently.

If you would like to experiment with it, you can execute the `buildNativeImage`
command in the sbt shell while inside the `runner` project. Please note
that while the command is available at the moment, and you are welcome to
[report an issue](https://github.com/enso-org/enso/issues/new?assignees=&labels=Type%3A+Bug&template=bug-report.md&title=)
with the functionality, any bugs you report will _not_ be considered high
priority.

**WE CURRENTLY DO NOT SUPPORT THE NATIVE IMAGE BUILD.**

#### Using IntelliJ
Internally, most of the developers working on the Enso project use IntelliJ as
their primary IDE. To that end, what follows is a basic set of instructions for
getting the project into a working state in IntelliJ.

1.  Clone the project sources.
2.  Open IntelliJ
3.  File -> New -> Project From Existing Sources.
4.  Navigate to the directory into which you cloned the project sources. By
    default this will be called `enso`. Select the directory, and not the
    `build.sbt` file it contains.
5.  In the 'Import Project' dialogue, select 'Import project from external
    model' and choose 'sbt'.
6.  Where it says 'Download:', ensure you check both 'Library Sources' and 'sbt
    sources'.
7.  In addition, check the boxes next to 'Use sbt shell:' such that it is used
    both 'for imports' and 'for builds'.
8.  Disallow the overriding of the sbt version.
9.  Under the 'Project JDK' setting, please ensure that it is set up to use a
    GraalVM version as described in [System requirements](#system-requirements).
    You may need to add it using the 'New' button if it isn't already set up.
10. Click 'Finish'. This will prompt you as to whether you want to overwrite the
    `project` folder. Select 'Yes' to continue. The Enso project will load up
    with an open SBT shell, which can be interacted with as described above. You
    will want to use scalafmt for formatting of Scala code, and install Google
    Java Format for formatting Java code. For more information see the relevant
    [Style Guides](style-guide/README.md).

However, as mentioned in the [Troubleshooting](#troubleshooting) section below,
the forked nature of execution in the SBT shell means that we can't trivially
make use of the IntelliJ debugger. In order to get debugging working, you will
need to follow these steps:

1. Go to Run -> Edit Configurations.
2. Click the `+` button in the header of the 'Run/Debug Configurations'
   dialogue that pops up.
3. Select 'Remote' and name the new configuration appropriately.
4. In the options for that configuration select 'Listen to remote JVM' under
   'Debugger mode:'
5. Where it provides the command-line arguments for the remote JVM, copy these
   and add them to `truffleRunOptions` in [`build.sbt`](build.sbt). Remove the
   portion of these options after `suspend=y`, including the comma. They are
   placeholders that we don't use.
6. Now, when you want to debug something, you can place a breakpoint as usual in
   IntelliJ, and then execute your remote debugging configuration. Now, in the
   SBT shell, run a command to execute the code you want to debug (e.g.
   `testOnly *CurryingTest*`). This will open the standard debugger interface
   and will allow you to step through your code.

**Please be careful** to ensure that you don't commit these changes to the sbt
configuration as they are specific to your machine.

#### Troubleshooting
If you are having issues building Enso, please check the list below before
filing an issue with us.

- **`StackOverflowError` During Compilation:** Please ensure that your version
  of sbt is respecting the project's `.jvmopts` settings. We make significant
  use of recursion when expanding macros for the parser, and these require use
  of additional stack. Alternatively, you can explicitly pass `-Xss8M` to the
  `sbt` invocation.
- **Debugging Not Working:** The sbt tasks run the invoked programs in a forked
  JVM. This means that to attach a debugger to it you need to use the JVM remote
  debugging support. We cannot support all possible configurations for this, but
  if you use IntelliJ please see the [Using IntelliJ](#using-intellj) section
  above for instructions.

If your problem was not listed above, please
[file a bug report](https://github.com/enso-org/enso/issues/new?assignees=&labels=Type%3A+Bug&template=bug-report.md&title=)
in our issue tracker and we will get back to you as soon as possible.

### Running Enso
The only component in this repository with a proper executable is the Enso
interpreter. It can be run using the sbt `run` command in the project
`runner` and provides a rudimentary command-line interface to the basic
capabilities of the interpreter.

Detailed information on the flags it supports can be obtained by executing `run
--help`, but the primary functionality is as follows:

- `--new PATH`: Creates a new Enso project at the location spcified by `PATH`.
- `--run PATH`: Executes the interpreter on the Enso source specified by `PATH`.
  In this case, `PATH` must point to either a standalone Enso file or an Enso
  project.

#### Language Server Mode
The Language Server can be run using the `--server` option. It requires also a
content root to be provided (`--root-id` and `--path` options). Command-line
interface of the runner prints all server options when you execute it with
`--help` option.

Below are options uses by the Language Server:
- `--server`: Runs the Language Server
- `--root-id <uuid>`: Content root id.
- `--path <path>`: Path to the content root.
- `--interface <interface>`: Interface for processing all incoming connections.
  Default value is 127.0.0.1
- `--port <port>`: Port for processing all incoming connections. Default value
  is 8080.

To run the Language Server on 127.0.0.1:8080 type:

```bash
./enso.jar \
  --server \
  --root-id 3256d10d-45be-45b1-9ea4-7912ef4226b1 \
  --path /tmp/content-root
```

If you want to provide a socket that the server should listen to, you must
specify the following options:

- `--interface`: The interface on which the socket will exist (e.g. `0.0.0.0`).
- `--port`: The port on `interface` where the socket will be opened (e.g. `80`).

## Pull Requests
Pull Requests are the primary method for making changes to Enso. GitHub has
[fantastic documentation](https://help.github.com/articles/about-pull-requests/)
on using the pull request feature. Enso uses the 'fork-and-pull' model of
development. It is as described
[here](https://help.github.com/articles/about-collaborative-development-models/)
and involves people pushing changes to their own fork and creating pull requests
to bring those changes into the main Enso repository.

Please make all pull requests against the `main` branch.

- We run CI on all contributions to Enso, but it's still useful for you to run
  the tests yourself locally first! This can be done by running `test` in the
  `enso` project in sbt.
- Additionally, please ensure that your code conforms to the Enso style guides,
  particularly the [Scala Style Guide](./style-guide/scala.md) and the
  [Java Style Guide](./style-guide/java.md).

Make sure you perform these checks before _every_ pull request. You can even add
[git hooks](https://git-scm.com/book/en/v2/Customizing-Git-Git-Hooks) before
every push to make sure that you can't forget.

- Every pull request to the Enso repository is reviewed by a member of the core
  team! You'll get assigned a reviewer based on the areas your PR touches, but
  please feel free to ask for a specific person if you've worked with them in a
  specific area before!
- If you have questions, or would like to begin the review process before your
  PR is 'done', please use the [Draft Pull Requests](https://github.blog/2019-02-14-introducing-draft-pull-requests/)
  feature on GitHub. Doing so will allow you to make use of our CI
  infrastructure as part of your development process.

Once the reviewer approves your pull request it will be tested by our continuous
integration provider before being merged. If we request changes to your PR,
please feel free to discuss the suggestions and comments! We can only achieve
the best results through open collaboration.

## Documentation
Documentation improvements are very welcome! For now, the main documentation
available is the _developer_ documentation for the language, which can be found
at the [dev docs site](https://dev.enso.org). The source for this documentation
is found in the [`docs/`](.) folder, and can be altered from there.

Documentation pull requests will be reviewed in exactly the same way as normal
pull requests.

To find documentation-related issues, sort by the
[Category: Documentation](hhttps://github.com/enso-org/enso/labels/Category%3A%20Documentation)
label.

## Issue Triage
Sometimes issues can be left open long after the bug has been fixed. Other
times, a bug might go stale because something has changed in the meantime.

It can be helpful to go through older bug reports and make sure that they are
still valid. Load up an older issue, double check that it's still true, and
leave a comment letting us know if it is or is not. The
[least recently updated](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+sort%3Aupdated-asc)
sort is good for finding issues like this.

Contributors with sufficient permissions can help by adding labels to help with
issue triage.

If you're looking for somewhere to start, take a look at the
[Difficulty: Beginner](https://github.com/enso-org/enso/labels/Difficulty%3A%20Beginner)
issue label, as well as the
[Status: Help Wanted](https://github.com/enso-org/enso/labels/Status%3A%20Help%20Wanted)
and
[Status: Good First Issue](https://github.com/enso-org/enso/labels/Status%3A%20Good%20First%20Issue) labels.

## Out-of-Tree Contributions
As helpful as contributing to Enso directly is, it can also be just as helpful
to contribute in other ways outside this repository:

- Answer questions in the [Discord](https://chat.luna-lang.org) or on
  [StackOverflow](https://stackoverflow.com/questions/tagged/enso).

## Helpful Documentation and Links
For people new to Enso, and just starting to contribute, or even for more
seasoned developers, some useful places to look for information are:

- The [design documentation](./README.md).
- The community! Don't be afraid to ask questions.

