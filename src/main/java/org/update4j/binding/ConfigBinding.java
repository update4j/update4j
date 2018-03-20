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
package org.update4j.binding;

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.update4j.Property;

/*
 * Everything that can be replaced by a property should be stored as strings.
 * Resolve them in Configuration::read.
 */
@XmlRootElement(name = "configuration")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConfigBinding {

	/*
	 * I can use an XmlAdapter, but anyway I parse everything in
	 * Configuration::read, so no need.
	 */
	@XmlAttribute
	public String timestamp;

	@XmlElement
	public BaseBinding base;

	@XmlElement
	public ProviderBinding provider;

	@XmlElementWrapper
	@XmlElement(name = "property")
	public List<Property> properties;

	@XmlElementWrapper
	@XmlElement(name = "library")
	public List<LibraryBinding> libraries;

}
