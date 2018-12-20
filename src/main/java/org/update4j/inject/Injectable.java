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
package org.update4j.inject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * A class implementing this type can be scanned to send and receive fields to
 * and from another injectable. A field annotated with {@link InjectSource} will
 * send its value to a matching field annotated with {@link InjectTarget}.
 * 
 * <p>
 * An inject target field that has not set {@code required} to {@code false}
 * must find a matching inject source, otherwise an
 * {@link UnsatisfiedInjectionException} will be thrown. An inject source that
 * doesn't have a matching target will not fail.
 * 
 * <p>
 * A target is matched with a source by matching the field names. You may also
 * explicitly set the target field name with {@code target} in
 * {@code InjectSource}. It will not look in the class hierarchy, only the
 * object type class itself. It is illegal for 2 fields to map to the same
 * target.
 * 
 * <p>
 * Fields containing both {@code InjectSource} and {@code InjectTarget} at once
 * is valid, and can be used to swap values.
 * 
 * <p>
 * You can annotate a method with {@link PostInject} to register a callback once
 * injection completes. Optionally, if the first parameter type is assignable to
 * the other injectable, it will be passed.
 * 
 * 
 * @author Mordechai Meisels
 *
 */
public interface Injectable {

	public static void injectBidirectional(Injectable obj1, Injectable obj2)
					throws UnsatisfiedInjectionException, IllegalAccessException, InvocationTargetException {
		Map<String, Object> sources1 = getSourceObjects(obj1);
		Map<String, Object> sources2 = getSourceObjects(obj2);

		injectValues(obj2, sources1);
		injectValues(obj1, sources2);
		
		notifyPostInject(obj1, obj2);
		notifyPostInject(obj2, obj1);

	}

	public static void injectUnidirectional(Injectable source, Injectable target)
					throws IllegalAccessException, UnsatisfiedInjectionException, InvocationTargetException {
		Map<String, Object> sourceObjects = getSourceObjects(source);
		injectValues(target, sourceObjects);
		
		notifyPostInject(source, target);
		notifyPostInject(target, source);
	}

	private static Map<String, Object> getSourceObjects(Injectable obj) throws IllegalAccessException {
		Map<String, Object> map = new HashMap<>();

		for (Field f : obj.getClass().getDeclaredFields()) {
			InjectSource annotation = f.getAnnotation(InjectSource.class);

			if (annotation != null) {
				String key = annotation.target();
				key = key.isEmpty() ? f.getName() : key;
				
				if (map.containsKey(key)) {
					throw new IllegalArgumentException("Two fields with '" + key + "' target.");
				}
				

				f.setAccessible(true);
				Object value = f.get(obj);
				map.put(key, value);

			}
		}

		return map;
	}

	private static void injectValues(Injectable obj, Map<String, Object> map)
					throws UnsatisfiedInjectionException, IllegalAccessException {
		for (Field f : obj.getClass().getDeclaredFields()) {
			InjectTarget annotation = f.getAnnotation(InjectTarget.class);

			if (annotation != null) {
				if (!map.containsKey(f.getName())) {
					if (annotation.required()) {
						throw new UnsatisfiedInjectionException(f);
					}
				} else {
					Object value = map.get(f.getName());
					
					f.setAccessible(true);
					f.set(obj, value);
				}
			}
		}
	}

	private static void notifyPostInject(Injectable callback, Injectable parameter)
					throws IllegalAccessException, InvocationTargetException {
		for (Method m : callback.getClass().getDeclaredMethods()) {
			PostInject annotation = m.getAnnotation(PostInject.class);

			if (annotation != null) {
				m.setAccessible(true);

				Object[] params = new Object[m.getParameterCount()];
				if (m.getParameterCount() > 0 && m.getParameterTypes()[0].isInstance(parameter)) {
					params[0] = parameter;
				}
				
				m.invoke(callback, params);
			}
		}
	}
}
