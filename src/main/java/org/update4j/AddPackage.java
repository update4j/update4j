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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class AddPackage {

	@XmlAttribute(name = "package")
	private String packageName;
	
	@XmlAttribute(name = "target")
	private String targetModule;
	
	/*
	 * Used by JAXB
	 */
	@SuppressWarnings("unused")
	private AddPackage() {
	}
	
	public AddPackage(String pkg, String mod) {
		packageName = pkg;
		targetModule = mod;
	}
	
	public String getPackageName() {
		return packageName;
	}
	
	public String getTargetModule() {
		return targetModule;
	}
}
