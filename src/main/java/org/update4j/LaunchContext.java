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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class LaunchContext {

	private ModuleLayer layer;
	private ClassLoader classLoader;
	private Configuration config;
	private List<String> args;

	LaunchContext(ModuleLayer layer, ClassLoader classLoader, Configuration config, List<String> args) {
		this.layer = Objects.requireNonNull(layer);
		this.classLoader = Objects.requireNonNull(classLoader);
		this.config = Objects.requireNonNull(config);

		this.args = args == null ? List.of() : Collections.unmodifiableList(args);
	}

	public ModuleLayer getModuleLayer() {
		return layer;
	}

	public ClassLoader getClassLoader() {
		return classLoader;
	}

	public Configuration getConfiguration() {
		return config;
	}

	public List<String> getArgs() {
		return args;
	}

}
