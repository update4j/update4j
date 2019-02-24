* *Upcoming Release*
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
* **1.3.2** *— h/t [@ChristianCiach](https://github.com/ChristianCiach)*
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

