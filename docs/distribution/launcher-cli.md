---
layout: developer-doc
title: Enso Launcher CLI
category: distribution
tags: [distribution, launcher, cli, command]
order: 5
---

# Enso Launcher CLI

This document describes available command-line options of the Enso launcher.

> The actionables for this document are:
>
> - This document is a draft. The explanations are just to give an idea of the
>   commands. It will be updated with detailed explanations when the CLI is
>   developed.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Commands](#commands)
  - [`new`](#new)
  - [`install engine`](#install-engine)
  - [`uninstall engine`](#uninstall-engine)
  - [`install distribution`](#install-distribution)
  - [`uninstall distribution`](#uninstall-distribution)
  - [`list`](#list)
  - [`default`](#default)
  - [`config`](#config)
  - [`run`](#run)
  - [`repl`](#repl)
  - [`language-server`](#language-server)
  - [`upgrade`](#upgrade)
  - [`version`](#version)
  - [`help`](#help)
- [General Options](#general-options)
  - [`--use-enso-version`](#--use-enso-version)
  - [`--use-system-jvm`](#--use-system-jvm)
  - [`--auto-confirm`](#--auto-confirm)
  - [`--hide-progress`](#--hide-progress)
  - [`--ensure-portable`](#--ensure-portable)
- [Options From Newer Versions](#options-from-newer-versions)
- [JVM Options](#jvm-options)

<!-- /MarkdownTOC -->

## Commands

### `new`

Create a new, empty project in a specified directory. By default uses the
`default` enso version, which can be overriden with `--use-enso-version`.

Examples:

```bash
> enso new project1 --path /somewhere/on/the/filesystem
    # creates project called Project1 in the specified directory
    # using the `default` Enso version
> enso new project2 --use-enso-version 2.0.1
    # creates the project in the current directory, using the 2.0.1 version
```

### `install engine`

Installs a specific version of the Enso engine.

Examples:

```bash
> enso install engine 2.0.1
```

### `uninstall engine`

Uninstalls a specific version of the Enso engine.

Examples:

```bash
> enso uninstall engine 2.0.1
```

### `install distribution`

Installs a portable Enso distribution into system-defined directories, as
explained in
[Installed Enso Distribution Layout](./distribution.md#installed-enso-distribution-layout).
By default, it asks the user for confirmation, but this can be skipped by adding
the `--auto-confirm` flag.

Examples:

```
> extraction-location/bin/enso install distribution
This will install Enso to ~/.local/share/enso/.
Configuration will be placed in ~/.config/enso/.
The universal `enso` launcher will be placed in ~/.local/bin/.
Do you want to continue? [Y/n]
```

### `uninstall distribution`

Uninstalls an installed Enso distribution from the installation location
described in
[Installed Enso Distribution Layout](./distribution.md#installed-enso-distribution-layout).
It removes the universal launcher and all components. By default, it asks the
user for confirmation, but this can be skipped by adding a `--auto-confirm`
flag.

Examples:

```
> enso uninstall distribution
This will completely uninstall Enso from ~/.local/share/enso/,
remove configuration from ~/.config/enso/
and the launcher script from ~/.local/bin/.
Do you want to continue? [y/N]
```

### `list`

Lists all installed versions of Enso and managed GraalVM distributions.

Optional arguments are `enso` to just list Enso installations or `runtime` to
list the installed runtimes.

Examples:

```bash
> enso list
Enso 2.0.1 -> GraalVM 20.1.0-java11
Enso 2.2.3 -> GraalVM 20.1.0-java11
Enso 3.0.0 -> GraalVM 21.1.0-java14
> enso list enso
2.0.1
2.2.3
3.0.0
> enso list runtime
GraalVM 20.1.0-java11 (used by 2 Enso installations)
GraalVM 21.1.0-java14 (used by 1 Enso installation)
```

### `default`

Sets the default Enso version used outside of projects.

If run without arguments, displays currently configured `default` version.

Examples:

```bash
> enso default 2.0.1
default set to version 2.0.1
> enso default
default version is 2.0.1
```

### `config`

Can be used to manage global user configuration.

If only the config path is provided, currently configured value is printed. If
the provided value is an empty string, the given key is removed from the config.

Examples:

```bash
> enso config author.name Example User # Sets `author.name`.
> enso config author.email # Prints current value.
user@example.com
> enso config author.name "" # Removes the value from the config.
```

### `run`

Runs a project or an Enso script file.

Examples:

```bash
> enso run script.enso # runs the file in script mode
> enso run path/to/project1 # runs the project
> enso run # runs the current project based on current directory
```

### `repl`

Launches an Enso REPL. If outside a project, it uses the `default` Enso version.
If run inside a project or an optional project path is specified, the REPL is
run in the context of that project, using the version specified in its
configuration.

Examples:

```bash
> enso repl # version and context depend on current working directory
> enso repl /path/to/project # runs the REPL in context of the specified project
```

### `language-server`

Launches the language server for a given project.

Examples:

```bash
> enso language-server \
    --root-id 3256d10d-45be-45b1-9ea4-7912ef4226b1 \
    --path /tmp/content-root
```

### `upgrade`

Checks for updates of the launcher and downloads any new versions.

Examples:

```bash
> enso upgrade
...
[info] Successfully upgraded launcher to 3.0.2.
> enso upgrade 2.0.1
...
[info] Successfully upgraded launcher to 2.0.1.
```

### `version`

Prints the version of the installed launcher as well as the full version string
of the currently selected Enso distribution.

Flag `--json` can be added to get the output in JSON format, instead of the
human-readable format that is the default.

```bash
> enso version
Enso Launcher
Version:    0.1.0
Built with: scala-2.13.3 for GraalVM 20.2.0
Built from: wip/rw/launcher-self-update* @ c76f7fe6a9e9f37cd8a296c615b7515d1b896d73
Built on:   Linux (amd64)
Current default Enso engine:
Enso Compiler and Runtime
Version:    0.1.1-rc5
Built with: scala-2.13.3 for GraalVM 20.1.0
Built from: enso-0.1.1-rc5 @ 391eca6de06b0c642cf7868db62209a9af3d241d
Running on: OpenJDK 64-Bit Server VM, GraalVM Community, JDK 11.0.7+10-jvmci-20.1-b02
            Linux 4.15.0-112-generic (amd64)
```

Besides `enso version`, `enso --version` is also supported and yields the same
result.

### `help`

Print this summary of available command and their usage.

## General Options

### `--use-enso-version`

Overrides the inferred (project local or `default`) version when running a
command.

### `--use-system-jvm`

Tells the launcher to use the default JVM (based on `JAVA_HOME`) instead of the
managed one. Will not work if the set-up JVM version is not GraalVM.

### `--auto-confirm`

Tells the launcher to not ask questions, but proceed with defaults. Useful for
automation.

### `--hide-progress`

Suppresses displaying progress bars for downloads and other long running
actions. May be needed if program output is piped.

### `--ensure-portable`

Checks if the launcher is run in portable mode and if it is not, terminates the
application.

## Options From Newer Versions

For commands that launch an Enso component inside a JVM (`repl`, `run` and
`language-server`), parameters that the launcher does not know about (for
example introduced in versions of Enso newer than the launcher knows about) may
be passed after a double dash (`--`), i.e. `enso repl -- --someUnknownFlag`.

## JVM Options

If an environment variable `ENSO_JVM_OPTS` is defined, JVM options defined there
are passed to the launcher JVM.

> Note: Currently the `ENSO_JVM_OPTS` are parsed by splitting on the space
> character, so individual options listed in this environment variable should
> not contain spaces or they may be interpreted incorrectly.

Moreover, it is possible to pass parameters to the JVM that is used to launch
these components, which may be helpful with debugging. A parameter of the form
`--jvm.argumentName=argumentValue` will be passed to the JVM as
`-DargumentName=argumentValue`.
