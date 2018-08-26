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

import java.util.List;

public class StringUtils {

	private StringUtils() {
	}

	public static final String CLASS_REGEX = "([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*";

	private static final List<String> keywords;
	private static final List<String> moduleKeywords;

	static {
		keywords = List.of("abstract", "continue", "for", "new", "switch", "assert", "default", "goto", "package",
						"synchronized", "boolean", "do", "if", "private", "this", "break", "double", "implements",
						"protected", "throw", "byte", "else", "import", "public", "throws", "case", "enum",
						"instanceof", "return", "transient", "catch", "extends", "int", "short", "try", "char", "final",
						"interface", "static", "void", "class", "finally", "long", "strictfp", "volatile", "const",
						"float", "native", "super", "while");
		moduleKeywords = List.of("module", "open", "opens", "exports", "requires", "transitive", "to", "with",
						"provides", "uses");
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
		if(!isClassName(name)) {
			return false;
		}
		
		String[] tokens = name.split("\\.");
		for (String t : tokens) {
			if (moduleKeywords.contains(t))
				return false;
		}

		return true;
	}
}
