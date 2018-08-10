package org.update4j.util;

import org.update4j.service.Launcher;

public class Warning {

	private static final String PREFIX = "suppress.warning.";

	public static void lock(String filename) {
		if (!"true".equals(System.getProperty(PREFIX + "lock"))) {
			System.err.println("WARNING: " + filename
							+ " is locked by another process, there are a few common causes for this:\n"
							+ "\t- Another application accesses this file:\n"
							+ "\t\tNothing you can do about it, it's out of your control.\n"
							+ "\t- 2 instances of this application run simultaneously:\n"
							+ "\t\tUse SingleInstanceManager to restrict running more than one instance.\n"
							+ "\t- You are calling update() after launch() and files are already loaded onto JVM:\n"
							+ "\t\tUse updateTemp() instead. Call Update.finalizeUpdate() upon next restart\n"
							+ "\t\tto complete the update process.\n"
							+ "\t- You are attempting to update a file that runs in the bootstrap application:\n"
							+ "\t\tBootstrap dependencies cannot typically be updated. For services, leave\n"
							+ "\t\tthe old in place, just release a newer version with a higher version\n"
							+ "\t\tnumber and make it available to the boot classpath or modulepath.\n"
							+ "\t- A file that's required in the business application was added to the boot classpath or modulepath:\n"
							+ "\t\tMuch care must be taken that business application files should NOT be\n"
							+ "\t\tloaded in the boot, the JVM locks them and prevents them from being updated.");
		}
	}

	public static void lockFinalize(String filename) {
		if (!"true".equals(System.getProperty(PREFIX + "lock"))) {
			System.err.println("WARNING: " + filename
							+ " is locked by another process, there are a few common causes for this:\n"
							+ "\t- Another application accesses this file:\n"
							+ "\t\tNothing you can do about it, it's out of your control.\n"
							+ "\t- 2 instances of this application run simultaneously:\n"
							+ "\t\tUse SingleInstanceManager to restrict running more than one instance.\n"
							+ "\t- You are calling Update.finalizeUpdate() after launch() and files are already loaded onto JVM:\n"
							+ "\t\tCall Update.finalizeUpdate() before launch.\n"
							+ "\t\tto complete the update process.\n"
							+ "\t- You are attempting to update a file that runs in the bootstrap application:\n"
							+ "\t\tBootstrap dependencies cannot typically be updated. For services, leave\n"
							+ "\t\tthe old in place, just release a newer version with a higher version\n"
							+ "\t\tnumber and make it available to the boot classpath or modulepath.\n"
							+ "\t- A file that's required in the business application was added to the boot "
							+ "classpath or modulepath:\n"
							+ "\t\tMuch care must be taken that business application files should NOT be\n"
							+ "\t\tloaded in the boot, the JVM locks them and prevents them from being updated.");
		}
	}

	public static void access(Launcher launcher) {
		if (!"true".equals(System.getProperty(PREFIX + "access"))) {
			System.err.println("WARNING: " + launcher.getClass().getCanonicalName()
							+ " was loaded with the boot class loader.\n"
							+ "\tThis may prevent accessing classes in the business application, and will\n"
							+ "\tthrow NoClassDefFoundErrors.\n"
							+ "\tTo prevent this, make sure the launcher is NOT loaded onto the boot\n"
							+ "\tclasspath and mark it with 'classpath=\"true\"' or 'modulepath=\"true\"' in the\n"
							+ "\tconfiguration file.\n"
							+ "\tYou may still leave it like this and access the business application by\n"
							+ "\treflecting against \"context.getClassLoader()\".\n"
							+ "\tPlease refer to: https://github.com/update4j/update4j/wiki/Documentation"
							+ "#classpath-and-modulepath");
		}
	}

	public static void path() {
		if (!"true".equals(System.getProperty(PREFIX + "path"))) {
			System.err.println(
							"WARNING: No libraries were found that are set with 'classpath' or 'modulepath' to true;\n"
											+ "although perfectly valid it's rarely what you want.\n"
											+ "Please refer to: https://github.com/update4j/update4j/wiki/Documentation#classpath-and-modulepath");
		}
	}

	public static void moduleConflict(String moduleName) {
		if (!"true".equals(System.getProperty(PREFIX + "moduleConflict"))) {
			System.err.println("WARNING: module '" + moduleName + "' already exists in the boot modulepath.\n"
							+ "\tIn order to prevent accidental breakage of your application among\n"
							+ "\tall your clients, the download was rejected.\n"
							+ "\tIf this is ONLY loaded on the business application, and great caution\n"
							+ "\twas taken it should not be included in the boot modulepath, you may\n"
							+ "\toverride this restriction by setting 'ignoreBootConflict=\"true\"' in\n"
							+ "\tthe configuration.");
		}
	}

	public static void packageConflict(String packageName) {
		if (!"true".equals(System.getProperty(PREFIX + "packageConflict"))) {
			System.err.println("WARNING: package '" + packageName + "' already exists in the boot modulepath.\n"
							+ "\tIn order to prevent accidental breakage of your application among\n"
							+ "\tall your clients, the download was rejected.\n"
							+ "\tIf this is ONLY loaded on the business application, and great caution\n"
							+ "\twas taken it should not be included in the boot modulepath, you may\n"
							+ "\toverride this restriction by setting 'ignoreBootConflict=\"true\"' in\n"
							+ "\tthe configuration.");
		}
	}
}
