* **1.5.6**
  * Fix Elliptic Curve cipher missed in [#89](https://github.com/update4j/update4j/issues/89).
  * Workaround JDK bug where `Files.walk` would enumerate absolute paths. [#113](https://github.com/update4j/update4j/issues/113), [#116](https://github.com/update4j/update4j/issues/116)
* **1.5.5**
  * Fix dynamic properties not working with archive installations. [#110](https://github.com/update4j/update4j/issues/110)
* **1.5.4**
  * Fix archive not properly resolving UNIX paths. [#109](https://github.com/update4j/update4j/issues/109)
* **1.5.3**
  * Clamp update handler `frac` values between 0 and 1. [#106](https://github.com/update4j/update4j/issues/106)
  * Bug fix in `DefaultBootstrap` causing command-line args not to be passed. [#107](https://github.com/update4j/update4j/issues/107)
* **1.5.2**
  * Bug fixes in `DefaultBootstrap`. [#105](https://github.com/update4j/update4j/issues/105)
* **1.5.1**
  * New update model `Configuration.Update(ArchiveUpdateOptions)`, using an `Archive` to store update files, it can then be 'installed' (calling `Archive::install`). [#76](https://github.com/update4j/update4j/issues/76)
  * Deprecated previous update models, but still available for smooth migration
  * Improved update return value `UpdateResult`. [#87](https://github.com/update4j/update4j/issues/87)
  * Not passing `default.launcher.main.class` will run the command-line arguments as a script. [#88](https://github.com/update4j/update4j/issues/88)
  * `ignoreBootConflict` no longer required if there are no user modules on the boot module layer.
  * `DefaultBootstrap::updateFirst` now performs update in parallel while launching the business app. [#104](https://github.com/update4j/update4j/issues/104)
  * Support Elliptic Curve cipher. [#89](https://github.com/update4j/update4j/issues/89)
* **1.4.5**
  * Added `DynamicClassLoader` and the new [Classloading Model](https://github.com/update4j/update4j/wiki/Documentation#classloading-model). [#75](https://github.com/update4j/update4j/issues/75)
  * New design reporting download progress in `DefaultUpdateHandler`.
  * Added `SingleInstanceManager::tryExecute` to handle second instance instead of automatic shutdown. [#85](https://github.com/update4j/update4j/issues/85)
  * Added `FileMapper::getChecksum` and `FileMapper::getSignature`
  * `user.home` and `user.dir` will only be automatically replaced when matched to the beginning of a path. [#73](https://github.com/update4j/update4j/issues/73)
  * Improved file accessibility check when copying new files to its final location. [#76](https://github.com/update4j/update4j/issues/76)
* **1.4.4**
  * Fixed fatal bug if an exception was thrown in `UpdateHander.doneDownloads()` or `UpdateHanlder.succeeded()`.
  * Changed warning suppression system property key from `suppress.warning` to `update4j.suppress.warning`.
  * Restricted passing the same command to `DefaultBootstrap` twice.
* **1.4.3**
  * Don't throw exception on jar download that `static requires` missing system module *— [@ChristianCiach](https://github.com/ChristianCiach)*
* **1.4.2**
  * Increased download byte buffer to 8kb *— suggested by [@ChristianCiach](https://github.com/ChristianCiach)*
* **1.4.1**
  * Allow 2 files with same path in config if both target different operating systems.
  * Added `UpdateHandler.shouldCheckForUpdate()`. Returning `false` will skip that file from being updated.
  * Pass arguments and system properties from the config when using the `DefaultLauncher`.
  * `DefaultLauncher` is now aware of JavaFX and will start `javafx.application.Application` even if missing a main method in class defined in `default.launcher.main.class`.
  * Reduced system dependencies to `java.xml` and added warnings if system module is not properly resolved.
  * When validating configuration and checking if 2 files resolve to same path; it will now allow if the files target different operating systems.
* **1.4.0**
  * Added dependency injection framework to communicate between the bootstrap and service provider.
  * Consequently, removed provider consumers at update and launch, and passing args at launch.
  * Removed many confusing `getXxxProperty()` methods in `Configuration` class.
  * You can now add properties to a config dynamically at runtime.
  * Locate explicit service providers even if not properly registered as required by `ServiceLoader`.
  * Made many service methods `default`.
  * Added `osFromFilename()` method in `FileMetadata` builder.
  * Properly set encoding to Unicode when reading remote config in `DefaultBootstrap`.
  * Bug fix when local config file was missing in `DefaultBootstrap::launchFirst`.
  * Changed how `--delegate` argument in `Bootstrap` works.
  * Fixed bug in `deleteOldFiles()` where it would try to compare files even if it belongs to different OS.
  * `Configuration.getTimestamp()` now returns `null` if not present in the XML instead of time of object creation.
* **1.3.3**
  * Properly escape special chars in output XML. Allow control chars in properties.
  * Renamed `UpdateHandler::connect` to `UpdateHandler::openDownloadStream`
  * Added more `Configuration.Builder::signer` overloads.
  * Default bootstrap now deletes old files if `--syncLocal` was used.
* **1.3.2** *— Tested on Linux by [@ChristianCiach](https://github.com/ChristianCiach)*
  * Control how files are downloaded with `UpdateHandler::connect`.
  * Delete old files with `Configuration::deleteOldFiles`.
* **1.3.1**
  * `DefaultBootstrap` sync local now creates directories to write local config.
  * `DefaultBootstrap` remote signature failure would not fall back to local.
* **1.3.0**
  * Rewrite of `DefaultBootstrap`.
  * You can now sign the configuration itself to ensure integrity of non-file elements, as paths and properties.
  * `FileMetadata.getSignature()` now returns a `String` instead of `byte[]` to avoid modification.
  * Java 11 compatibility: Removed JavaFX modules.
  * Fixed single instance bug on Linux.
  * Connection/read timeouts at 10 seconds.
  * Safer file overriding by properly handling file locks.
  * `ConfigMapper` and `FileMapper` lists are now `final`, to prevent accidental `NPE`.
  * `FileMetadata::streamDirectory` now automatically presets `path` attribute to actual filename _relative to_ the streaming directory, instead of absolute source path.
* **1.2.2**
  * Removed `DefaultUpdateHandler` percentage output to avoid problems with Eclipse console.
  * Added `DefaultBootstrap` with a straightforward CLI, and `DefaultLauncher`.
  * Added `Configuration.sync()` methods.
  * Additional file validation on update and renaming `UpdateManager.verifyingFileSignature()` to `validatingFile()` to include all of them.
  * To avoid version inconsistencies, all downloads are now atomic; if one download fails all previous are rolled back. Updated `UpdateHandler.doneDownloadFile()` to add awareness of this.
  * Fixed bug when downloading automatic module with no `Automatic-Module-Name` in `META-INF/MANIFEST.MF`.
* **1.2.1**
  * _Retracted, didn't correctly set main class in module descriptor._  
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

