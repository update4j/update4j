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

import org.update4j.LaunchContext;

/**
 * An implementation of this interface can be used as an entry point to the
 * business application.
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
	 * Called when the business application should be launched.
	 * 
	 * @param context Context information about the launch.
	 */
	void run(LaunchContext context);

}
