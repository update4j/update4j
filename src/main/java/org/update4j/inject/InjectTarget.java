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

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * A field in an {@link Injectable} with this annotation will receive instances
 * of fields annotated with {@link InjectSource} when
 * {@link Injectable#injectUnidirectional(Injectable, Injectable)} or
 * {@link Injectable#injectBidirectional(Injectable, Injectable)} were invoked,
 * if it successfully matches.
 * 
 * 
 * @author Mordechai Meisels
 *
 */
@Retention(RUNTIME)
@Target(FIELD)
@Documented
public @interface InjectTarget {

	/**
	 * Set whether it should fail if the field has no match.
	 * 
	 * <p>
	 * If an injector was not passed, the fields in a service provider will remain
	 * {@code null} without any errors thrown.
	 * 
	 * @return Whether it should fail if a match could not be found.
	 */
	boolean required() default true;
}
