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

import org.update4j.service.Service;

/**
 * A class implementing this type can be scanned to send and receive fields to and
 * from a service provider.
 * A field annotated with {@link InjectSource} will send its value to
 * a matching field annotated with {@link InjectTarget}.
 * 
 * <p>
 * An inject target field that has not set {@code required} to {@code false}
 * must find a matching inject source, otherwise an
 * {@link UnsatisfiedInjectionException} will be thrown. An inject source that
 * doesn't have a matching target will not fail.
 * 
 * <p>
 * A target is matched with a source by matching the field names. You may also
 * explicitly set the target field name with {@code target} in
 * {@link InjectSource}. It will never look in the class hierarchy, only the
 * object type class itself. It is illegal for 2 fields to map to the same
 * target.
 * 
 * <p>
 * Fields containing both {@link InjectSource} and {@link InjectTarget} at once
 * is valid, and can be used to swap values between the injector and service
 * provider.
 * 
 * <p>
 * Injection is always done immediately after the service provider constructor
 * returned. When injection completes, the {@link #injectComplete(Service)}
 * method is called.
 * 
 * 
 * @author Mordechai Meisels
 *
 */
public interface Injector {

	default void injectComplete(Service provider) {
	}

}
