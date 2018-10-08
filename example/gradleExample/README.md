# Gradle Example

An example using update4j in a java gradle project. The source code of the gradle project implements the business logic.
There is no update4j specific java source code. Update4j is only used to generate the required distribution files.

Compatibility: This example works only for update4j versions above 1.2.2, to use the jar file of the root project, 
follow the instructions in the ``buildscript`` section.

## Instructions

There are three update4j specific gradle targets:

- update4jDeploy: Builds the deployment directory. The deployment directory contains all files, that should be stored on
  the server (e.g. the application jar, all dependencies and the update4j configuration file). The directory is located 
  at ``build/distribution/update4j/deploy``
- update4jBoot: Builds a package for the local execution of the example application. The package contains the update4j 
  jar file, the certificate used to sign the application files and script files with the right parameters to execute the 
  jar file. The required application files will be downloaded on the first execution. The directory is located  at 
  ``build/distribution/update4j/boot/package``
- update4jInstall: Builds a native installer for the boot files. These will be downloaded on first execution. The installer 
  will be available in ``build/distribution/update4j/boot/install``



### Default configuration

- Files will be downloaded in a os specific directory (can be changed by modifying the variable ``defaultAppData``):
  - Mac: ``/Users/<username>/Library/Application Support/<appname>``)
  - Windows: ``%LOCALAPPDATA%/<appname>``)
  - Linux: ``/home/<username>/.appData/<appname>``)
  
- Modules will be added as modules, non modules will be added to the classpath. You can change this behaviour with the 
  variables ``modulesOnly`` and ``classpathOnly``

- As only update4j and the JDK are packed into the installer, all JDK modules are included per default. This makes the 
  package quite huge, but the risk of missing modules (e.g. SSL ciphers) is reduced. You may override this behaviour 
  through the variable ``allModules``. If set to false, only the required modules of update4j will be included.

## Tested

On Mac only

## TODO

- [ ] signing?!

