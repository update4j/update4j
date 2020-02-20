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
package org.update4j.service;

import java.util.List;

import org.update4j.Bootstrap;

/**
 * An implementation of this interface can be used as a delegate for a bootstrap
 * application to be located by {@link Bootstrap}.
 * 
 * <p>
 * For more info how to use services, check out the <a href=
 * "https://github.com/update4j/update4j/wiki/Documentation#dealing-with-providers">GitHub
 * Wiki</a>.
 * 
 * @author Mordechai Meisels
 *
 */
@FunctionalInterface
public interface Delegate extends Service {

    /**
     * The bootstrap's main method, if you start it via the {@link Bootstrap} class
     * or run update4j as a jar file.
     * 
     * <p>
     * This method is called after dependency injection was performed using any
     * overload of {@code Bootstrap.start()} that takes an {@link Injectable}.
     * 
     * <p>
     * To do any initialization before injection, do it in the constructor, but be
     * aware that unless you specify the delegate with the {@code --delegate} flag,
     * the constructor might be called even if this provider will not be used in the
     * end. This happens as the service loading mechanism first loads all providers
     * and then compares versions to use the one with the highest version.
     * 
     * @param args
     *            Command line arguments
     * 
     * @throws Throwable
     */
    void main(List<String> args) throws Throwable;
}
