# badgerutils

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://travis-ci.org/badgerwithagun/badgerutils.svg?branch=master)](https://travis-ci.org/badgerwithagun/badgerutils)

A **very much under construction** suite of vaguely-related utilities for functional programming in Java 8.  The APIs are not even remotely stable at the moment and may be subject to change, reversal of said changes, re-instatement of the reversals, bugs, harebrained schemes and semi-ironic anti-patterns.

At the moment, I'm working on:

* Self-balancing parallelisation of multiple data pipelines within fixed thread pools without risk of thread starvation
* Concurrency, buffering, batching and multicasting of `Stream`s
* Dealing with checked exceptions in lambdas and `AutoCloseable`s

Some of this work may already have been well covered elsewhere (although I can't find anything that _quite_ matches what I'm after), so please, if it looks familiar, let me know so I can Do The Right Thing and just help out with the existing project(s).

No documentation, builds or anything else remotely helpful yet.

Pull requests, issues and comments welcome.