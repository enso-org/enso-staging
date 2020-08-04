---
layout: style-guide
title: Markdown Style Guide
category: style-guide
tags: [style-guide]
order: 5
---

# Markdown Style Guide

Markdown's primary strength is that it's readable in its source form as well as
its rendered form. In the team we seem to have our reading time split evenly
between the two, and so creating a readable format for our markdown
documentation is very important.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Automated Formatting](#automated-formatting)
- [Manual Formatting](#manual-formatting)
  - [Title Case](#title-case)
  - [Table of Contents](#table-of-contents)
- [In the Future](#in-the-future)

<!-- /MarkdownTOC -->

## Automated Formatting

The bulk of the heavy lifting for formatting our markdown is done by the
formatter [prettier](https://prettier.io). This formatter should be run on the
entire repository using `npx prettier --write .` before every pull request.

For instructions on how to install it, please see our
[contributing guidelines](../CONTRIBUTING.md#getting-set-up-documentation).

If you notice files in generated code being formatted by prettier, please add
them to the [`.prettierignore`](../../.prettierignore) file.

When bumping the version of prettier, please commit the resultant documentation
formatting changes along with the bump as a separate PR from any functional code
changes.

## Manual Formatting

Unfortunately, however, prettier doesn't cope with _all_ of the requirements
that we place on our markdown source files to ensure that they're both easy to
read and easy to navigate.

The following elements require manual formatting.

### Title Case

All section headings should use
[Title Case](https://en.wikipedia.org/wiki/Letter_case#Title_case).

The only exceptions to this rule are where the title refers to the name of an
in-language construct. In these situations, the capitalisation should match that
of the language construct, and it should be surrounded in backticks (`\``).

### Table of Contents

All files should contain a table of contents that shows headings at levels two
and three. This should be placed _directly before_ the first level-two heading.

It should be formatted as follows, with the key elements being:

- Each entry should be a bullet point, with level 2 entries against the baseline
  and level 3 entries indented by two spaces.
- The entries should be internal-document links to each of the sections.
- The title of the entry should match the title of the section to which it is
  linking.

```md
<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Level Two](#level-two)
- [Level Two Again](#level-two-again)
  - [Level Three](#level-three)

<!-- /MarkdownTOC -->
```

These table of contents can be automatically generated by plugins to common
editors.

A document needing its table of contents to show level four entries is usually
indicative of portions of the document needing to be extracted. If you feel that
you have a situation where this is not the case, please talk to a documentation
code owner.

## In the Future

As prettier doesn't enforce _all_ of our markdown style guidelines, in the
future a new formatter will be written that will enforce all of our guidelines
in one go.