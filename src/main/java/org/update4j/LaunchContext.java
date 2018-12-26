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

import java.util.Objects;

/**
 * A class that contain details of the launch.
 * 
 * @author Mordechai Meisels
 *
 */
public class LaunchContext {

	private ModuleLayer layer;
	private ClassLoader classLoader;
	private Configuration config;

	LaunchContext(ModuleLayer layer, ClassLoader classLoader, Configuration config) {
		this.layer = Objects.requireNonNull(layer);
		this.classLoader = Objects.requireNonNull(classLoader);
		this.config = Objects.requireNonNull(config);
	}

	/**
	 * Returns the {@link ModuleLayer} where modules in the config that were marked
	 * with @{code modulepath} are dynamically loaded.
	 * 
	 * @return The dynamic module layer.
	 */
	public ModuleLayer getModuleLayer() {
		return layer;
	}

	/**
	 * Returns the class loader that classes in the dynamic classpath or modulepath
	 * are loaded with. Use this to access dynamic classes in the bootstrap:
	 * 
	 * <pre>
	 * Class&lt;?&gt; clazz = Class.forName("MyBusinessClass", true, ctx.getClassLoader());
	 * </pre>
	 * 
	 * Once the class was loaded, the class itself has access to the dynamic
	 * classpath in natural Java.
	 * 
	 * @return The dynamic class loader.
	 */
	public ClassLoader getClassLoader() {
		return classLoader;
	}

	/**
	 * Returns the configuration used for this launch.
	 * 
	 * @return The configuration used for this launch.
	 */
	public Configuration getConfiguration() {
		return config;
	}

}
