# UpToDate Project

Auto-updater and launcher for your distributed applications. Built on top of Java 9's module system.

[![Build Status](https://travis-ci.org/uptodate-project/uptodate.svg?branch=master)](https://travis-ci.org/uptodate-project/uptodate)
[![Apache License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Release](https://img.shields.io/badge/release-v1.0--beta-yellow.svg)](https://github.com/uptodate-project/uptodate/releases/tag/v1.0-beta)


### Screenshots

#### JavaFX
[![javafx][1]][1]

#### Headless
[![headless][2]][2]


### Overview

The UpToDate Project is the first auto-update and launcher framework completely compatible with Java 9. Easily host your application
files anywhere in the cloud accesible via a URL (even Google Drive, Dropbox, Amazon S3, or Maven Central)
and you can synchronize them with all your distributed applications.

You can either do a preliminary check-for-updates and always launch the latest version on the current VM, or you might let the user do some action
that triggers an update after your application has been launched. In the latter case, the existing files are locked and thus cannot be
updated outright. UpToDate provides a mechanism for downloading your new files into some temporary directory and finalize them upon
restart.

UpToDate has made security its priority. Signing your files is as easy as providing your private key to the framework on your dev machine,
and it will do the job itself. On the client side, you should load the public key into the framework and it will automatically verify 
each and every downloaded file. It will forcefully reject any files without or with invalid signatures. This is an optional feature.

As a side feature, UpToDate allows you to make your application running as a single instance. Any new instance of
the application would pass its command-line arguments to the existing running instance and shut down.

Although this has been designed with JavaFX in mind, we went out of the way to make it work on every environment.
There are not even one reference to JavaFX in the code, but every change to the update/launch state will trigger a callback.

### Using this Project

At the core, is the `Configuration` class. Here are some useful methods:

|Method|Purpose|
|---|---|
|`read(Reader)`| Read a configuration from a stream, in xml.|
|`write(Writer)`| Write the configuration to an xml file.|
|`update()`|Check if any file is outdated and update it.|
|`update(Certificate)`|Same as above but will check the signature before saving the file.|
|`updateTemp(Path)`|Updates are saved in a temporary directory. Finalize them with `UpToDate::finalizeUpdates(Path)`|
|`updateTemp(Path,Certificate)`|Same, but with signature verification.|
|`launch()`|Launches the files listed in the configuration dynamically on the same Virtual Machine.|
|`launch(List<String>)`|Same as above but will pass command-line arguments to the newly launched modules.|

You could create a `Configuration` in code with its builder, by calling `Configuration::withBase` or `Configuration::absolute`.

For a complete reference, please refer to the [wiki](https://github.com/uptodate-project/uptodate/wiki).

### Limitations

This Project loads modules on a new `ModuleLayer` when `Configuration::launch` is called. This implies 2 limitations.

- `Class::forName` will not always locate classes loaded in the new layer. Using reflection will work as follows:
  ```java
  Class<?> clazz = moduleLayer.findLoader("mymodule").loadClass("com.example.MyClass");
  ```
  `moduleLayer` is injected into `LaunchContext` when the launching is complete.
  
  Also note that JavaFX's `Application.launch(String... args)` uses reflection to get the caller class. In order
  to make it work, call the other overload `Application.launch(Class<? extends Application> clazz, String... args)`.
  
- Layers cannot load system modules into the module graph, regerdless of its module descriptor. For simplicity the boot layer
  automatically resolves all modules in the `java`, `javax`, and `javafx` namespace.
  
  If you want to use system modules in the `jdk` namespace (as `jdk.incubator.httpclient`) you should either 
  require them in one of the service handlers (more info in the [wiki](https://github.com/uptodate-project/uptodate/wiki))
  or start the VM with `--add-modules jdk.icubator.httpsclient`,
  or -- to always get it right -- `--add-modules ALL-SYSTEM`.
  

### Attribution

This project was highly influenced by [edvin/fxlauncher](https://github.com/edvin/fxlauncher/). Thanks for the insights
that made this possible.

### License

This project is licensed under the Apache Software License 2.0


  [1]: https://i.stack.imgur.com/bi9gL.gif
  [2]: https://i.stack.imgur.com/ca9rT.gif
