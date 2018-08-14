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

/**
 * This class is a simple POJO that represents "Add Exports" and "Add Opens" in
 * the configuration file.
 * 
 * @author Mordechai Meisels
 *
 */
public class AddPackage {

	private String packageName;
	private String targetModule;

	/**
	 * Constructs a new {@link AddPackage} with the given source package name to be
	 * added to the given target module.
	 */
	public AddPackage(String pkg, String mod) {
		packageName = pkg;
		targetModule = mod;
	}

	/**
	 * Returns the package name that is being added.
	 * 
	 * @return The package name that is being added.
	 */
	public String getPackageName() {
		return packageName;
	}

	/**
	 * Returns the target module name that the package is added to.
	 * 
	 * @return The target module name that the package is added to.
	 */
	public String getTargetModule() {
		return targetModule;
	}
}
