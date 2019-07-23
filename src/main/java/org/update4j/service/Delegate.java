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
 * An implementation of this interface can be used as a delegate for a bootstrap application to be located by {@link Bootstrap}.
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

	void main(List<String> args) throws Throwable;
}
