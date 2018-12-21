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
package org.update4j;

/**
 * The policy that should be used when
 * {@link Configuration#implyPlaceholders(String, PlaceholderMatchType)} is
 * called.
 * 
 * @author Mordechai Meisles
 *
 */
public enum PlaceholderMatchType {
	/**
	 * Will break words with placeholders if it finds a match.
	 */
	EVERY_OCCURRENCE,

	/**
	 * Will only replace with placeholders if the it doesn't break a word (using
	 * rexeg {@code \b} word boundary)
	 */
	WHOLE_WORD,

	/**
	 * Will only replace if the complete string matches with one placeholder.
	 */
	FULL_MATCH,

	/**
	 * Won't change the string.
	 */
	NONE;
}
