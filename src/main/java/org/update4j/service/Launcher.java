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

import org.update4j.Configuration;
import org.update4j.LaunchContext;
import org.update4j.inject.Injectable;

/**
 * An implementation of this interface can be used as an entry point to the
 * business application.
 * 
 * <p>
 * If you wish to create your custom bootstrap but use the
 * {@link DefaultLauncher}, you might pass over a list of strings that will be
 * used as the arguments in {@code main}, by adding a field in the bootstrap:
 * 
 * <pre>
 * {@literal @InjectSource}
 * private List&lt;String&gt; args;
 * </pre>
 * 
 * that contains the arguments, and launch by using one of the overloads that
 * take an {@link Injectable}:
 * 
 * <pre>
 * config.launch(this);
 * </pre>
 * 
 * <p>
 * If the launcher is started by the {@link DefaultBootstrap} but you define
 * your own launcher, you can capture the business command-line arguments by
 * adding this field in the launcher:
 * 
 * <pre>
 * {@literal @InjectTarget}
 * private List&lt;String&gt; args;
 * </pre>
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
public interface Launcher extends Service {

	/**
	 * Called when the business application should be launched, after injection was
	 * performed (see {@link Configuration#launch(Injectable)}) .
	 * 
	 * <p>
	 * To do any initialization before injection, do it in the constructor, but be
	 * aware that unless you specify the launcher in the {@code launcher} section of
	 * the configuration, the constructor might be called even if this provider will
	 * not be used in the end. This happens as the service loading mechanism first
	 * loads all providers and then compares versions to use the one with the
	 * highest version.
	 * 
	 * @param context
	 *            Context information about the launch.
	 */
	void run(LaunchContext context);

}
