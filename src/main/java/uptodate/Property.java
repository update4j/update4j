package uptodate;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Property {

	@XmlAttribute
	private String key;

	@XmlAttribute
	private String value;

	@XmlAttribute
	private OS os;

	/*
	 * Used by Jaxb
	 */
	@SuppressWarnings("unused")
	private Property() {
	}

	public Property(String key, String value, OS os) {
		this.key = key;
		this.value = value;
		this.os = os;
	}

	public Property(String key, String value) {
		this(key, value, null);
	}

	public String getKey() {
		return key;
	}

	public String getValue() {
		return value;
	}

	public OS getOs() {
		return os;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((key == null) ? 0 : key.hashCode());
		result = prime * result + ((os == null) ? 0 : os.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Property other = (Property) obj;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		if (os != other.os)
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}
}
