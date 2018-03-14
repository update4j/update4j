# UpToDate Project

Auto-updater and launcher for your distributed applications. Built on top of Java 9's module system.

[![Build Status](https://travis-ci.org/uptodate-project/uptodate.svg?branch=master)](https://travis-ci.org/uptodate-project/uptodate)
[![Apache License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
![Java-9](https://img.shields.io/badge/java-9%2B-orange.svg)
[![Release](https://img.shields.io/badge/release-v1.0--beta-yellow.svg)](https://github.com/uptodate-project/uptodate/releases/tag/v1.0-beta)



## Screenshots

### JavaFX

<sup>Downloads 3 files then launches `hello-world.jar`</sup>
[![javafx][1]][1]

### Headless
<sup>Downloads 3 files then launches `hello-world.jar`. You can see that subsequent runs won't download again.</sup>
[![headless][2]][2]


## Overview

The UpToDate Project is the first auto-update and launcher framework completely compatible with Java 9. Easily host your application
files anywhere in the cloud accesible via a URL (even Google Drive, Dropbox, Amazon S3, or Maven Central)
and you can synchronize them with all your distributed applications.

UpToDate has made security its priority. Signing your files is as easy as providing your private key to the framework on your dev machine,
and it will do the job itself. On the client side, you should load the public key into the framework and it will automatically verify 
each and every downloaded file. It will forcefully reject any files without or with invalid signatures. This is an optional feature.

As a side feature, UpToDate allows you to make your application running as a single instance. Any new instance of
the application would pass its command-line arguments to the existing running instance and shut down.

## Installation

Download the JAR file from the latest [release](https://github.com/uptodate-project/uptodate/releases).

You can use it as a regular dependency, or you may run it as a runnable JAR file in modular mode. In the latter case you must also provide a [`Delegate`](https://github.com/uptodate-project/uptodate/wiki/Documentation#handlers) in the module-path.

To run it as a runnable JAR as a module:

```batchfile
~$> java --module-path . --module uptodate
```

Or in shorthand:

```batchfile
~$> java -p . -m uptodate
```
  
## Documentation

Please refer to the [documentation](https://github.com/uptodate-project/uptodate/wiki/Documentation) for a full reference guide.

## Limitations

As this projects builds on top of the Module System we are limited to what it has to offer. Please refer to the [documentation](https://github.com/uptodate-project/uptodate/wiki/Documentation#limitations) for a comprehensive list of limitations.

## Attribution

This project was highly influenced by [edvin/fxlauncher](https://github.com/edvin/fxlauncher/). Thanks for the insights
that made this possible.

## License

This project is licensed under the [Apache Software License 2.0](http://www.apache.org/licenses/LICENSE-2.0)


  [1]: https://i.stack.imgur.com/bi9gL.gif
  [2]: https://i.stack.imgur.com/ca9rT.gif
