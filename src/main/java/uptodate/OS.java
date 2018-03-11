package uptodate;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;

@XmlEnum
public enum OS {
	@XmlEnumValue("win")
	WINDOWS, @XmlEnumValue("mac")
	MAC, @XmlEnumValue("linux")
	LINUX, @XmlEnumValue("other")
	OTHER;

	public static final OS CURRENT;

	static {
		String os = System.getProperty("os.name", "generic").toLowerCase();

		if ((os.contains("mac")) || (os.contains("darwin")))
			CURRENT = MAC;
		else if (os.contains("win"))
			CURRENT = WINDOWS;
		else if (os.contains("nux"))
			CURRENT = LINUX;
		else
			CURRENT = OTHER;
	}
}