# update4j <sup><sup>beta</sup></sup> &nbsp; &nbsp; &nbsp; [![Build Status](https://travis-ci.org/update4j/update4j.svg?branch=master)](https://travis-ci.org/update4j/update4j)   [![Apache License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)   ![Java-9+](https://img.shields.io/badge/java-9%2B-orange.svg)   [![Maven Release](https://img.shields.io/badge/maven%20central-v1.2.0-yellow.svg)](https://search.maven.org/search?q=org.update4j)

**Documentation available at the [wiki](https://github.com/update4j/update4j/wiki/Documentation), or explore the [JavaDoc](http://docs.update4j.org/javadoc/update4j/overview-summary.html)**

Auto-updater and launcher for your distributed applications. Built with Java 9's module system in mind.





## Screenshots

### Headless
<sup>Downloads 4 files then launches `hello-world.jar`. You can see that subsequent runs won't download again.</sup>
[![headless][2]][2]

### JavaFX

<sup>Downloads 4 files then launches `hello-world.jar`</sup>
[![javafx][1]][1]



## Overview

The update4j framework is the first auto-update and launcher framework completely compatible with Java 9. Easily host your application
files anywhere in the cloud accesible via a URL (even Google Drive, Dropbox, Amazon S3, or Maven Central)
and you can synchronize them with all your distributed applications.

Update4j has made security its priority. Signing your files is as easy as providing your private key to the framework on your dev machine,
and it will do the job itself. On the client side, you should load the public key into the framework and it will automatically verify 
each and every downloaded file. It will forcefully reject any files without or with invalid signatures. This is an optional feature.

As a side feature, update4j allows you to make your application running as a single instance. Any new instance of
the application would pass its command-line arguments to the existing running instance and shut down.

## Installation & Usage

Install using Maven:

```xml
<dependency>
    <groupId>org.update4j</groupId>
    <artifactId>update4j</artifactId>
    <version>1.2.0</version>
</dependency>
```

You can use it as a regular dependency, or you may run it as a runnable JAR file. In the latter case you must also provide a [`Delegate`](https://github.com/update4j/update4j/wiki/Documentation#dealing-with-providers) in the classpath or modulepath.

To run it as a runnable JAR as a module:

```shell
$ java --module-path . --module org.update4j
```

Or in shorthand:

```shell
$ java -p . -m org.update4j
```

You can also start it the conventional way on the classpath using the `-cp` or `-jar` flags.

For more information refer to [Starting the Application](https://github.com/update4j/update4j/wiki/Documentation#starting-the-application) in the wiki.


## Change Log

* **1.2.0**
  * Bug fixes.
  * Major redo of Configuration builder API.
  * Reduced dependencies to zero.
  * Renamed `Library` to `FileMetadata` and `<library>` XML element to `<file>`.
  * Warnings if files were incorrectly added to the boot classpath.
  * Reject download if module name or package name conflicts with a module on the boot modulepath and additional `ignoreBootConflict` attribute to `<file>` to override it.
  * Directly pass instances of service providers instead of locating providers.
  * Removed deprecated methods.
* **1.1.3-beta**
  * `Library.Reference.Builder` no longer automatically sets `modulepath` to true.
  * Warn on `Configuration::launch` if there is no library that has set either `classpath` or `modulepath` to true.
* **1.1.2-beta**
  * Added `LaunchContext.getClassLoader()` method.
* **1.1.0-beta**
  * Added full support of loading jars into the classpath.
  * Added "add exports/opens/reads" support.
  * Renamed `ImplicationType` to `PlaceholderMatchType`.
* **1.0.0-beta.1**
  * Deprecated verification via a `Certificate`, use `PublicKey` instead. Will be removed completely once it graduates beta.
* **1.0.0-beta**
  * Initial release.


## Attribution

This project was highly influenced by [edvin/fxlauncher](https://github.com/edvin/fxlauncher/). Thanks for the insights
that made this possible.

## License

This project is licensed under the [Apache Software License 2.0](http://www.apache.org/licenses/LICENSE-2.0)


  [1]: https://i.stack.imgur.com/Hz1G7.gif
  [2]: https://i.stack.imgur.com/Ttf8Z.gif
