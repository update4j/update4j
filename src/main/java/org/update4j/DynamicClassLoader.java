package org.update4j;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

public final class DynamicClassLoader extends URLClassLoader {

	static {
		registerAsParallelCapable();
	}
	
	public DynamicClassLoader(String name, ClassLoader parent) {
		super(name, new URL[0], parent);
	}
	
	/*
	 * Required when this classloader is used as the system classloader
	 */
	public DynamicClassLoader(ClassLoader parent) {
		this("classpath", parent);
	}
	
	public DynamicClassLoader() {
		this(Thread.currentThread().getContextClassLoader());
	}
	
	void add(URL url) {
		addURL(url);
	}
	
	public static DynamicClassLoader findAncestor(ClassLoader cl) {
		do {
			
			if(cl instanceof DynamicClassLoader)
				return (DynamicClassLoader) cl;
			
			cl = cl.getParent();
		} while(cl != null);
		
		return null;
	}
	
	/*
	 *  Required for Java Agents when this classloader is used as the system classloader
	 */
	@SuppressWarnings("unused")
	private void appendToClassPathForInstrumentation(String jarfile) {
		try {
			add(Paths.get(jarfile).toRealPath().toUri().toURL());
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
}
