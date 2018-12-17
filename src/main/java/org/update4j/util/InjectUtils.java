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
package org.update4j.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.update4j.inject.InjectSource;
import org.update4j.inject.InjectTarget;
import org.update4j.inject.Injector;
import org.update4j.inject.UnsatisfiedInjectionException;

public class InjectUtils {

	private InjectUtils() {
	}

	public static void inject(Injector injector, Object obj) throws UnsatisfiedInjectionException, IllegalAccessException {
		Field[] fields = injector.getClass().getDeclaredFields();
		
		List<Field> sources = new ArrayList<>();
		Map<String, Field> targetNamesToSource = new HashMap<>();
		for(Field f : fields) {
			InjectSource annotation = f.getAnnotation(InjectSource.class);
			
			if(annotation != null) {
				sources.add(f);
				
				if(annotation.target().isEmpty()) {
					targetNamesToSource.put(f.getName(), f);
				} else {
					targetNamesToSource.put(annotation.target(), f);
				}
			}
		}
		
		fields = obj.getClass().getDeclaredFields();
		
		List<Field> targets = new ArrayList<>();
		for(Field f : fields) {
			if(f.getAnnotation(InjectTarget.class) != null) {
				targets.add(f);
			}
		}
		
		ListIterator<Field> iter = targets.listIterator();
		while(iter.hasNext()) {
			Field targetField = iter.next();
			
			Field sourceField = targetNamesToSource.get(targetField.getName());
			if(sourceField == null) {
				
				if(targetField.getAnnotation(InjectTarget.class).required()) {
					throw new UnsatisfiedInjectionException(targetField);
				}
				
				continue;
			}
			
			sourceField.setAccessible(true);
			targetField.setAccessible(true);
			
			Object value = sourceField.get(injector);
			targetField.set(obj, value);
		}
		
	}
}
