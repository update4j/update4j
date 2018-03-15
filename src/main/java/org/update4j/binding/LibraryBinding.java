package org.update4j.binding;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

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
	public Boolean modulepath;

	@XmlAttribute
	public String comment;

	@XmlAttribute
	public String signature;
}