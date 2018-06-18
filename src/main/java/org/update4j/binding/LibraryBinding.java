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

import org.update4j.AddPackage;
import org.update4j.OS;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class LibraryBinding {

	@XmlAttribute
	public String uri;

	@XmlAttribute
	public String path;

	@XmlAttribute
	public String checksum;

	@XmlAttribute
	public Long size;

	@XmlAttribute
	public OS os;
	
	@XmlAttribute
	public Boolean classpath;

	@XmlAttribute
	public Boolean modulepath;

	@XmlAttribute
	public String comment;

	@XmlAttribute
	public String signature;
	
	@XmlElementWrapper
	@XmlElement(name = "exports")
	public List<AddPackage> addExports;

	@XmlElementWrapper
	@XmlElement(name = "opens")
	public List<AddPackage> addOpens;

	@XmlElementWrapper
	@XmlElement(name = "reads")
	public List<ReadsBinding> addReads;
}