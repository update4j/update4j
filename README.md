# [![update4j-logo][3]][3]

[![Build Status](https://travis-ci.org/update4j/update4j.svg?branch=master)](https://travis-ci.org/update4j/update4j)   [![Apache License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)   ![Java-9+](https://img.shields.io/badge/java-9%2B-orange.svg)   [![Maven Release](https://img.shields.io/badge/maven%20central-v1.3.3-yellow.svg)](https://search.maven.org/search?q=org.update4j)    [![Gitter](https://badges.gitter.im/update4j/update4j.svg)](https://gitter.im/update4j/update4j?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)


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

The update4j framework is the first auto-update and launcher framework completely compatible with Java 9 and up. Easily host your application files anywhere in the cloud accessible via a URL (even Google Drive, Dropbox, Amazon S3, or Maven Central)
and you can synchronize them with all your distributed applications.

In update4j _you_ have ultimate control of every process, from startup - update - launch - shutdown; unlike other auto-update frameworks that yields over the control only once the business application was launched. In addition, every single piece of code is completely updatable; [even the framework itself](https://github.com/update4j/update4j/wiki/Documentation#updating-update4j-itself), once a new version is released!

## Installation & Usage

YOu can [download](https://repo1.maven.org/maven2/org/update4j/update4j/1.3.3/update4j-1.3.3.jar) or install using Maven:

```xml
<dependency>
    <groupId>org.update4j</groupId>
    <artifactId>update4j</artifactId>
    <version>1.3.3</version>
</dependency>
```

You can use it as a regular dependency, or you may run it as a runnable JAR file. 

To run it in the modulepath, use either of:

```shell
$ java -p update4j-1.3.3.jar -m org.update4j
$ java -p . -m org.update4j

```

To run it in the classpath, use either of:

```shell
$ java -jar update4j-1.3.3.jar
$ java -cp * org.update4j.Bootstrap
```

For more information refer to [Starting the Application](https://github.com/update4j/update4j/wiki/Documentation#starting-the-application) in the wiki.


## What's New in 1.3.x
  * Properly escape special chars in output XML. Allow control chars in properties.
  * Added 2 more `Configuration.Builder::signer` overloads.
  * Control how files are downloaded with `UpdateHandler::openDownloadStream`.
  * Delete old files with `Configuration::deleteOldFiles`.
  * Rewrite of `DefaultBootstrap`.
  * You can now sign the configuration itself to ensure integrity of non-file elements, as paths and properties.
  * `FileMetadata.getSignature()` now returns a `String` instead of `byte[]` to avoid modification.
  * Java 11 compatibility: Removed JavaFX modules.
  * Fixed single instance bug on Linux.
  * Connection/read timeouts at 10 seconds in default download implementation.
  * Safer file overriding by properly handling file locks.
  * `ConfigMapper` and `FileMapper` lists are now `final`, to prevent accidental `NPE`.
  * `FileMetadata::streamDirectory` now automatically presets `path` attribute to actual filename _relative to_ the streaming directory, instead of absolute source path.


## Attribution

This project was highly influenced by [edvin/fxlauncher](https://github.com/edvin/fxlauncher/). Thanks for the insights
that made this possible.

## License

This project is licensed under the [Apache Software License 2.0](http://www.apache.org/licenses/LICENSE-2.0)


  [1]: https://i.stack.imgur.com/Hz1G7.gif
  [2]: https://i.stack.imgur.com/Ttf8Z.gif
  [3]: https://i.stack.imgur.com/L6WAF.jpg
