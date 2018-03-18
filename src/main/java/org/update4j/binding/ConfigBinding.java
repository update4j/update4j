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
