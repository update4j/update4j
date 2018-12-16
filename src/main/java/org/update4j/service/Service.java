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
package org.update4j.service;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.stream.Collectors;

import org.update4j.util.StringUtils;

public interface Service {

	default long version() {
		return 0L;
	}

	public static <T extends Service> T loadService(ModuleLayer layer, ClassLoader classLoader, Class<T> type,
					String classname) {
		if (classname != null && !StringUtils.isClassName(classname)) {
			throw new IllegalArgumentException(classname + " is not a valid Java class name.");
		}

		if (classLoader == null) {
			classLoader = Thread.currentThread().getContextClassLoader();
		}

		ServiceLoader<T> loader;
		List<Provider<T>> providers = new ArrayList<>();

		if (layer != null) {
			loader = ServiceLoader.load(layer, type);
			providers.addAll(loader.stream().collect(Collectors.toList()));
		}

		loader = ServiceLoader.load(type, classLoader);
		providers.addAll(loader.stream().collect(Collectors.toList()));

		if (classname != null) {
			// an explicit class name is used
			// first lets look at providers, to locate in closed modules
			for (Provider<T> p : providers) {
				if (p.type().getCanonicalName().equals(classname))
					return p.get();
			}

			// nothing found, lets load with reflection
			try {
				Class<?> clazz = classLoader.loadClass(classname);

				if (type.isAssignableFrom(clazz)) {
					
					// What do you mean?? look 1 line above
					@SuppressWarnings("unchecked")
					T value = (T) clazz.getConstructor().newInstance();
					return value;

				} else {
					// wrong type
					throw new IllegalArgumentException(classname + " is not of type " + type.getCanonicalName());
				}
			} catch (RuntimeException e) {
				throw e; // avoid unnecessary wrapping
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

		} else {

			if (providers.isEmpty()) {
				throw new IllegalStateException("No provider found for " + type.getCanonicalName());
			}

			List<T> values = providers.stream().map(Provider::get).collect(Collectors.toList());

			long maxVersion = Long.MIN_VALUE;
			T maxValue = null;
			for (T t : values) {
				long version = t.version();
				if (maxVersion <= version) {
					maxVersion = version;
					maxValue = t;
				}
			}

			return maxValue;
		}
	}

	public static <T extends Service> T loadService(ModuleLayer layer, ClassLoader classLoader, Class<T> type) {
		return loadService(layer, classLoader, type, null);
	}

	public static <T extends Service> T loadService(ModuleLayer layer, Class<T> type, String classname) {
		return loadService(layer, null, type, classname);
	}

	public static <T extends Service> T loadService(ModuleLayer layer, Class<T> type) {
		return loadService(layer, null, type, null);
	}

	public static <T extends Service> T loadService(ClassLoader classLoader, Class<T> type, String classname) {
		return loadService(null, classLoader, type, classname);
	}

	public static <T extends Service> T loadService(ClassLoader classLoader, Class<T> type) {
		return loadService(null, classLoader, type, null);
	}

	public static <T extends Service> T loadService(Class<T> type, String classname) {
		return loadService(null, null, type, classname);
	}

	public static <T extends Service> T loadService(Class<T> type) {
		return loadService(type, null);
	}

}
