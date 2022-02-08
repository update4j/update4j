# [![update4j-logo][3]][3]

[![Build Status](https://travis-ci.org/update4j/update4j.svg?branch=master)](https://travis-ci.org/update4j/update4j)   [![Apache License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)   ![Java-9+](https://img.shields.io/badge/java-9%2B-orange.svg)   [![Maven Release](https://img.shields.io/badge/maven%20central-v1.5.9-yellow.svg)](https://search.maven.org/search?q=org.update4j)    [![Gitter](https://badges.gitter.im/update4j/update4j.svg)](https://gitter.im/update4j/update4j?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)


**Read the [documentation](https://github.com/update4j/update4j/wiki/Documentation), explore the [JavaDoc](http://docs.update4j.org/javadoc/update4j/index.html), or [see it in action](https://github.com/update4j/update4j/wiki/Demo-Application)**

_Create a framework_: design the environment and lifecycle (&mdash;bootstrap) to make your own auto-update framework and hack it to the core, or use the built-in default bootstrap.

## Screenshots

### Headless

Using the default bootstrap, downloads 4 files then launches `hello-world.jar`. You can see that subsequent runs won't download again.

[![headless][2]][2]

### JavaFX

Using a custom bootstrap implemented to report progress in JavaFX, downloads 4 files then launches `hello-world.jar`.

[![javafx][1]][1]


## Overview

Update4j is the first auto-update and launcher library designed for Java 9+. Easily host your application files anywhere (even Google Drive, Dropbox, Amazon S3, or Maven Central) and you can synchronize them with all your distributed applications. You can use [any protocol you wish](https://gitter.im/update4j/update4j?at=5c7067c1a378ef11f6236c86) to retrieve those files and may be protected under authenticated API.

In update4j _you_ have ultimate control of every process, from startup - update - launch - shutdown, since it's a library (you call the 3rd party code) not a framework (3rd party calls your code outside your control). In addition, every single piece of code is completely updatable; [even update4j itself](https://github.com/update4j/update4j/wiki/Documentation#updating-update4j-itself), once a new version is released! (Well, if you properly [set up the environment](https://www.reddit.com/r/java/comments/ih0vcu/comment/g4apb68).)



## Installation & Usage

You can [download](https://repo1.maven.org/maven2/org/update4j/update4j/1.5.9/update4j-1.5.9.jar) or install using Maven:

```xml
<dependency>
    <groupId>org.update4j</groupId>
    <artifactId>update4j</artifactId>
    <version>1.5.9</version>
</dependency>
```

You can use it as a regular dependency, or you may run it as a runnable JAR file. 

To run it in the modulepath, use either of:

```shell
$ java -p update4j-1.5.9.jar -m org.update4j
$ java -p . -m org.update4j

```

To run it in the classpath, use either of:

```shell
$ java -jar update4j-1.5.9.jar
$ java -cp * org.update4j.Bootstrap
```

For more information refer to [Starting the Application](https://github.com/update4j/update4j/wiki/Documentation#starting-the-application) in the wiki.


## What's New in 1.5.x — [Migration Guide](https://github.com/update4j/update4j/wiki/Migration-to-1.5.x)
  * New update model `Configuration.update(ArchiveUpdateOptions)`, using an `Archive` to store update files, it can then be 'installed' (calling `Archive::install`). [#76](https://github.com/update4j/update4j/issues/76)
  * Deprecated previous update models, but still available for smooth migration.
  * Improved update return value as `UpdateResult`. [#87](https://github.com/update4j/update4j/issues/87)
  * Using the `DefaultLauncher`, not passing `default.launcher.main.class` will run the command-line arguments as a script. [#88](https://github.com/update4j/update4j/issues/88)
  * `ignoreBootConflict` no longer required if there are no user modules on the boot module layer.
  * `DefaultBootstrap::updateFirst` now performs update in parallel while launching the business app. [#104](https://github.com/update4j/update4j/issues/104)
  * Support Elliptic Curve cipher. [#89](https://github.com/update4j/update4j/issues/89)
  * Clamp update handler `frac` values between 0 and 1. [#106](https://github.com/update4j/update4j/issues/106)

## Sponsers

[![docsolver](https://avatars.githubusercontent.com/u/1634032?s=75)](https://github.com/docsolver)

## License

This project is licensed under the [Apache Software License 2.0](http://www.apache.org/licenses/LICENSE-2.0)


  [1]: https://i.stack.imgur.com/Hz1G7.gif
  [2]: https://i.stack.imgur.com/Ttf8Z.gif
  [3]: https://i.stack.imgur.com/L6WAF.jpg
