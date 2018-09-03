/*
 * Copyright 2018 Mordechai Meisels
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.update4j.util;

import org.update4j.service.Launcher;

public class Warning {

	private static final String PREFIX = "suppress.warning";

	public static void lock(String filename) {
		if (shouldWarn("lock")) {
			System.err.println("WARNING: '" + filename
							+ "' is locked by another process, there are a few common causes for this:\n"
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
							+ "\t\tloaded in the boot, the JVM locks them and prevents them from being updated.\n");
		}
	}

	public static void lockFinalize(String filename) {
		if (shouldWarn("lock")) {
			System.err.println("WARNING: '" + filename
							+ "' is locked by another process, there are a few common causes for this:\n"
							+ "\t- Another application accesses this file:\n"
							+ "\t\tNothing you can do about it, it's out of your control.\n"
							+ "\t- 2 instances of this application run simultaneously:\n"
							+ "\t\tUse SingleInstanceManager to restrict running more than one instance.\n"
							+ "\t- You are calling Update.finalizeUpdate() after launch() and files are already loaded onto JVM:\n"
							+ "\t\tCall Update.finalizeUpdate() before launch to complete the update process.\n"
							+ "\t- You are attempting to update a file that runs in the bootstrap application:\n"
							+ "\t\tBootstrap dependencies cannot typically be updated. For services, leave\n"
							+ "\t\tthe old in place, just release a newer version with a higher version\n"
							+ "\t\tnumber and make it available to the boot classpath or modulepath.\n"
							+ "\t- A file that's required in the business application was added to the boot "
							+ "classpath or modulepath:\n"
							+ "\t\tMuch care must be taken that business application files should NOT be\n"
							+ "\t\tloaded in the boot, the JVM locks them and prevents them from being updated.\n");
		}
	}

	public static void access(Launcher launcher) {
		if (shouldWarn("access")) {
			System.err.println("WARNING: '" + launcher.getClass().getCanonicalName()
							+ "' was loaded with the boot class loader.\n"
							+ "\tThis may prevent accessing classes in the business application, and will\n"
							+ "\tthrow NoClassDefFoundErrors.\n"
							+ "\tTo prevent this, make sure the launcher is NOT loaded onto the boot\n"
							+ "\tclasspath and mark it with 'classpath=\"true\"' or 'modulepath=\"true\"' in the\n"
							+ "\tconfiguration file.\n"
							+ "\tYou may still leave it like this and access the business application by\n"
							+ "\treflecting against \"context.getClassLoader()\".\n"
							+ "\tPlease refer to: https://github.com/update4j/update4j/wiki/Documentation"
							+ "#classpath-and-modulepath\n");
		}
	}

	public static void reflectiveAccess(Launcher launcher) {
		if (shouldWarn("access")) {
			System.err.println("WARNING: '" + launcher.getClass().getCanonicalName()
							+ "' was not loaded using the Service Provider Interface.\n"
							+ "\tLaunchers like these only have reflective access to the business\n"
							+ "\tapplication. You must reflect using 'context.getClassLoader()'.\n");
		}
	}

	public static void path() {
		if (shouldWarn("path")) {
			System.err.println("WARNING: No files were found that are set with 'classpath' or 'modulepath' to true;\n"
							+ "\talthough perfectly valid it's rarely what you want.\n"
							+ "\tPlease refer to: https://github.com/update4j/update4j/wiki/Documentation#classpath-and-modulepath\n");
		}
	}

	public static void nonZip(String filename) {
		if (shouldWarn("bootConflict")) {
			System.err.println("WARNING: File '" + filename + "' has the \".jar\" file extension but is not a\n"
							+ "\tvalid zip file and if present in the boot modulepath it will prevent JVM startup.\n"
							+ "\tIn order to prevent accidental breakage of your application among\n"
							+ "\tall your clients, the download was rejected.\n"
							+ "\tIf this is ONLY loaded on the boot CLASSPATH or the business application,\n"
							+ "\tand great caution was taken it should not be included in the boot modulepath, you may\n"
							+ "\toverride this restriction by setting 'ignoreBootConflict=\"true\"' in\n"
							+ "\tthe configuration.\n");
		}
	}

	public static void illegalAutomaticModule(String moduleName, String filename) {
		if (shouldWarn("bootConflict")) {
			System.err.println("WARNING: Automatic module '" + moduleName + "' for file '" + filename
							+ "' is not a valid Java identifier.\n"
							+ "\tIn order to prevent accidental breakage of your application among\n"
							+ "\tall your clients, the download was rejected.\n"
							+ "\tIf this is ONLY loaded on the boot CLASSPATH or the business application,\n"
							+ "\tand great caution was taken it should not be included in the boot modulepath, you may\n"
							+ "\toverride this restriction by setting 'ignoreBootConflict=\"true\"' in\n"
							+ "\tthe configuration.\n");
		}

	}

	public static void moduleConflict(String moduleName) {
		if (shouldWarn("bootConflict")) {
			System.err.println("WARNING: Module '" + moduleName + "' already exists in the boot modulepath.\n"
							+ "\tIn order to prevent accidental breakage of your application among\n"
							+ "\tall your clients, the download was rejected.\n"
							+ "\tIf this is ONLY loaded on the boot CLASSPATH or the business application,\n"
							+ "\tand great caution was taken it should not be included in the boot modulepath, you may\n"
							+ "\toverride this restriction by setting 'ignoreBootConflict=\"true\"' in\n"
							+ "\tthe configuration.\n");
		}
	}

	public static void packageConflict(String packageName) {
		if (shouldWarn("bootConflict")) {
			System.err.println("WARNING: Package '" + packageName + "' already exists in the boot modulepath.\n"
							+ "\tIn order to prevent accidental breakage of your application among\n"
							+ "\tall your clients, the download was rejected.\n"
							+ "\tIf this is ONLY loaded on the boot CLASSPATH or the business application,\n"
							+ "\tand great caution was taken it should not be included in the boot modulepath, you may\n"
							+ "\toverride this restriction by setting 'ignoreBootConflict=\"true\"' in\n"
							+ "\tthe configuration.\n");
		}
	}

	private static boolean shouldWarn(String key) {
		return !"true".equals(System.getProperty(PREFIX, System.getProperty(PREFIX + "." + key)));
	}

}
