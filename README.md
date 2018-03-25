# update4j &nbsp; &nbsp; &nbsp; [![Build Status](https://travis-ci.org/update4j/update4j.svg?branch=master)](https://travis-ci.org/update4j/update4j)   [![Apache License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)   ![Java-9](https://img.shields.io/badge/java-9%2B-orange.svg)   [![Release](https://img.shields.io/badge/release-v1.0--beta-yellow.svg)](https://github.com/update4j/update4j/releases/tag/v1.0-beta)

**Documentation available at the [wiki](https://github.com/update4j/update4j/wiki/Documentation).**

Auto-updater and launcher for your distributed applications. Built on top of Java 9's module system.





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

## Installation

Download the JAR file from the latest [release](https://github.com/update4j/update4j/releases).

You can use it as a regular dependency, or you may run it as a runnable JAR file in modular mode. In the latter case you must also provide a [`Delegate`](https://github.com/update4j/update4j/wiki/Documentation#dealing-with-providers) in the module-path.

To run it as a runnable JAR as a module:

```shell
$ java --module-path . --module org.update4j
```

Or in shorthand:

```shell
$ java -p . -m org.update4j
```


## Attribution

This project was highly influenced by [edvin/fxlauncher](https://github.com/edvin/fxlauncher/). Thanks for the insights
that made this possible.

## License

This project is licensed under the [Apache Software License 2.0](http://www.apache.org/licenses/LICENSE-2.0)


  [1]: https://i.stack.imgur.com/Hz1G7.gif
  [2]: https://i.stack.imgur.com/Ttf8Z.gif
