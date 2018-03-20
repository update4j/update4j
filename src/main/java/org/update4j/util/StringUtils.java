package org.update4j.util;

public class StringUtils {

	private StringUtils() {
	}

	public static String CLASS_REGEX = "([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*";
	
	public static boolean isClassName(String name) {
		return name.matches(CLASS_REGEX);
	}
}
