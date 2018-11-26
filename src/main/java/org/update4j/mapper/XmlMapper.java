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
package org.update4j.mapper;

import org.w3c.dom.Node;

public abstract class XmlMapper {

	public abstract void parse(Node node);

//	public abstract Node toNode(Document doc);

	public abstract String toXml();

	public static String getAttributeValue(Node node, String key) {
		Node n = node.getAttributes().getNamedItem(key);

		if (n != null)
			return n.getNodeValue();

		return null;
	}

	public static String escape(String orig) {
		StringBuilder builder = new StringBuilder();

		for (char c : orig.toCharArray()) {
			if (c == '\0' || c == 0xfffe || c == 0xffff) {
				continue;
			} else if (c == '\'') {
				builder.append("&apos;");
			} else if (c == '"') {
				builder.append("&quot;");
			} else if (c == '&') {
				builder.append("&amp;");
			} else if (c == '<') {
				builder.append("&lt;");
			} else if (c == '>') {
				builder.append("&gt;");
			}

			else if (c <= 0x1f || c >= 0x7f) {
				builder.append("&#" + ((int) c) + ";");
			} else {
				builder.append(c);
			}
		}

		return builder.toString();
	}
}
