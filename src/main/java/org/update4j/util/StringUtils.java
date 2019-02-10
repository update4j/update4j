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

import java.lang.module.ModuleFinder;
import java.util.Set;
import java.util.stream.Collectors;

public class StringUtils {

	private StringUtils() {
	}

	public static final String CLASS_REGEX = "([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*";

	private static final Set<String> keywords;
	private static final Set<String> moduleKeywords;
	private static final Set<String> systemModules;

	static {
		keywords = Set.of("abstract", "continue", "for", "new", "switch", "assert", "default", "goto", "package",
						"synchronized", "boolean", "do", "if", "private", "this", "break", "double", "implements",
						"protected", "throw", "byte", "else", "import", "public", "throws", "case", "enum",
						"instanceof", "return", "transient", "catch", "extends", "int", "short", "try", "char", "final",
						"interface", "static", "void", "class", "finally", "long", "strictfp", "volatile", "const",
						"float", "native", "super", "while");

		moduleKeywords = Set.of("module", "open", "opens", "exports", "requires", "transitive", "to", "with",
						"provides", "uses");

		// Last updated JDK 11
		systemModules = Set.of("java.rmi", "jdk.management.jfr", "jdk.jdi", "jdk.charsets", "jdk.pack", "java.xml",
						"jdk.xml.dom", "jdk.rmic", "java.datatransfer", "jdk.jstatd", "jdk.httpserver", "jdk.jcmd",
						"java.desktop", "java.se", "java.security.sasl", "jdk.zipfs", "java.base", "jdk.crypto.ec",
						"jdk.javadoc", "jdk.management.agent", "jdk.jshell", "jdk.editpad", "java.sql.rowset",
						"jdk.sctp", "jdk.jsobject", "jdk.unsupported", "jdk.jlink", "java.smartcardio",
						"jdk.scripting.nashorn", "jdk.scripting.nashorn.shell", "java.security.jgss", "java.compiler",
						"jdk.dynalink", "jdk.unsupported.desktop", "jdk.accessibility", "jdk.security.jgss", "java.sql",
						"jdk.hotspot.agent", "java.transaction.xa", "java.xml.crypto", "java.logging", "jdk.jfr",
						"jdk.internal.vm.ci", "jdk.crypto.cryptoki", "jdk.net", "java.naming", "jdk.internal.ed",
						"java.prefs", "java.net.http", "jdk.compiler", "jdk.naming.rmi", "jdk.internal.opt",
						"jdk.jconsole", "jdk.attach", "jdk.crypto.mscapi", "jdk.internal.le", "java.management",
						"jdk.jdwp.agent", "jdk.internal.jvmstat", "java.instrument", "jdk.internal.vm.compiler",
						"jdk.internal.vm.compiler.management", "jdk.management", "jdk.security.auth", "java.scripting",
						"jdk.jdeps", "jdk.aot", "jdk.jartool", "java.management.rmi", "jdk.naming.dns",
						"jdk.localedata");
	}

	public static boolean isClassName(String name) {
		if (!name.matches(CLASS_REGEX))
			return false;

		String[] tokens = name.split("\\.");
		for (String t : tokens) {
			if (keywords.contains(t))
				return false;
		}

		return true;
	}

	public static boolean isModuleName(String name) {
		if (!isClassName(name)) {
			return false;
		}

		String[] tokens = name.split("\\.");
		for (String t : tokens) {
			if (moduleKeywords.contains(t))
				return false;
		}

		return true;
	}

	public static String deriveModuleName(String filename) {

		// strip ".jar" at the end
		filename = filename.replaceAll("\\.jar$", "");

		// drop everything after the version
		filename = filename.replaceAll("-\\d.*", "");

		// all non alphanumeric get's converted to "."
		filename = filename.replaceAll("[^A-Za-z0-9]", ".");

		// strip "." at beginning and end
		filename = filename.replaceAll("^\\.*|\\.*$", "");

		// all double "." stripped to single
		filename = filename.replaceAll("\\.{2,}", ".");

		return filename;
	}

	public static boolean isSystemModule(String str) {
		return systemModules.contains(str);
	}
}
