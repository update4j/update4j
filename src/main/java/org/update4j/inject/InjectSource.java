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
package org.update4j.inject;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * A field marked with this annotation will be used as an injection source for
 * {@link UpdateHandler} or {@link Launcher} fields that contain the
 * {@link InjectTarget} annotation.
 * 
 * @author Mordechai Meisels
 *
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface InjectSource {

	/**
	 * The target field name. If empty, the source field name will be used.
	 * 
	 * @return The target field name, or empty.
	 */
	String target() default "";
}
