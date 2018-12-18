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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.update4j.inject.InjectSource;
import org.update4j.inject.InjectTarget;
import org.update4j.inject.UnsatisfiedInjectionException;

public class InjectUtils {

	private InjectUtils() {
	}

	public static void inject(Object obj1, Object obj2) throws UnsatisfiedInjectionException, IllegalAccessException {
		Map<String, Object> sources1 = getSourceObjects(obj1);
		Map<String, Object> sources2 = getSourceObjects(obj2);
		
		injectValues(obj2, sources1);
		injectValues(obj1, sources2);
		
	}

	private static Map<String, Object> getSourceObjects(Object obj) throws IllegalAccessException {
		Map<String, Object> map = new HashMap<>();

		for (Field f : obj.getClass().getDeclaredFields()) {
			InjectSource annotation = f.getAnnotation(InjectSource.class);

			if (annotation != null) {
				String key = annotation.target();
				key = key.isEmpty() ? f.getName() : key;

				f.setAccessible(true);
				Object value = f.get(obj);

				Object old = map.put(key, value);

				if (old != null) {
					throw new IllegalArgumentException("Two fields with '" + key + "' target.");
				}
			}
		}

		return map;
	}

	private static void injectValues(Object obj, Map<String, Object> map)
					throws UnsatisfiedInjectionException, IllegalAccessException {
		for (Field f : obj.getClass().getDeclaredFields()) {
			InjectTarget annotation = f.getAnnotation(InjectTarget.class);

			if (annotation != null) {
				Object value = map.get(f.getName());
				if (value == null) {
					if (annotation.required()) {
						throw new UnsatisfiedInjectionException(f);
					}
				} else {
					f.setAccessible(true);
					f.set(obj, value);
				}
			}
		}
	}
}
