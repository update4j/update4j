package org.update4j;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.update4j.service.Delegate;
import org.update4j.service.Service;

public class Main {

	public static String CLASS_REGEX = "\\Q--delegate\\E\\s*?=?\\s*?(([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*)";

	public static void main(String[] args) throws Throwable {
		String overrideClass = null;

		Pattern pattern = Pattern.compile(CLASS_REGEX);
		for (String s : args) {
			Matcher matcher = pattern.matcher(s);
			if (matcher.matches()) {
				overrideClass = matcher.group(1);
				break;
			}
		}

		Delegate delegate = Service.loadService(Delegate.class, overrideClass);
		delegate.main(List.of(args));
	}

}