/*
 * Copyright 2018 Mordechai Meisels
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
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

            if (cl instanceof DynamicClassLoader)
                return (DynamicClassLoader) cl;

            cl = cl.getParent();
        } while (cl != null);

        return null;
    }

    /*
     *  Required for Java Agents when this classloader is used as the system classloader
     */
    @SuppressWarnings("unused")
    private void appendToClassPathForInstrumentation(String jarfile) throws IOException {
        add(Paths.get(jarfile).toRealPath().toUri().toURL());
    }
}
