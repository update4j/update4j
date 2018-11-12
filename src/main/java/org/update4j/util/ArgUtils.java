package org.update4j.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArgUtils {

	private ArgUtils() {

	}

	public static Map<String, String> parseArgs(List<String> list) {
		Pattern pattern = Pattern.compile("--(.+?)(?:\\s*=\\s*|\\s+)(.+)");
		Map<String, String> map = new LinkedHashMap<>();

		for (int i = 0; i < list.size(); i++) {
			String arg = list.get(i).trim();

			if (arg.startsWith("--")) {
				Matcher m = pattern.matcher(arg);
				if (m.find()) {
					map.put(m.group(1), m.group(2));
				} else {
					arg = arg.substring(2);

					if (i + 1 < list.size()) {
						String value = list.get(i + 1).trim();
						if (!value.startsWith("--")) {
							map.put(arg, value);
							i++;
							continue;
						}
					}

					map.put(arg, null);
				}

			} else {
				throw new IllegalArgumentException("Unknown command '" + arg + "'.");
			}
		}

		return map;
	}

	public static List<String> beforeSeparator(List<String> args) {
		int separatorIdx = args.indexOf("--");
		if (separatorIdx < 0) {
			return args;
		} else {
			return args.subList(0, separatorIdx);
		}
	}

	public static List<String> afterSeparator(List<String> args) {
		int separatorIdx = args.indexOf("--");
		if (separatorIdx < 0) {
			return List.of();
		} else {
			return args.subList(separatorIdx + 1, args.size());
		}
	}


	public static void validateNoValue(Map.Entry<String, String> e) {
		if (e.getValue() != null) {
			throw new IllegalArgumentException("Unknown command '" + e.getKey() + "'.");
		}
	}

	public static void validateHasValue(Map.Entry<String, String> e) {
		if (e.getValue() == null) {
			throw new IllegalArgumentException("Missing value for '--" + e.getKey() + "'.");

		}
	}
}
