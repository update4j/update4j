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
package org.update4j;

public enum OS {
	WINDOWS("win"), MAC("mac"), LINUX("linux"), OTHER("other");

	public static final OS CURRENT;

	static {
		String os = System.getProperty("os.name", "generic")
						.toLowerCase();

		if ((os.contains("mac")) || (os.contains("darwin")))
			CURRENT = MAC;
		else if (os.contains("win"))
			CURRENT = WINDOWS;
		else if (os.contains("nux"))
			CURRENT = LINUX;
		else
			CURRENT = OTHER;
	}

	private String name;

	OS(String name) {
		this.name = name;
	}

	public String getShortName() {
		return name;
	}

	public static OS fromShortName(String name) {
		switch (name) {
		case "win":
			return WINDOWS;
		case "mac":
			return MAC;
		case "linux":
			return LINUX;
		case "other":
			return OTHER;
		default:
			throw new IllegalArgumentException("Unknown type: " + name);
		}
	}
}