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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.update4j.OS;
import org.update4j.Property;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/*
 * Everything that can be replaced by a property should be stored as strings.
 * Resolve them in Configuration::read.
 *
 */
public class ConfigMapper extends XmlMapper {

	public String timestamp;
	public String baseUri;
	public String basePath;
	public String updateHandler;
	public String launcher;
	public List<Property> properties;
	public List<FileMapper> files;

	public ConfigMapper() {
	}

	public ConfigMapper(Node node) {
		parse(node);
	}

	public ConfigMapper(ConfigMapper copy) {
		timestamp = copy.timestamp;
		baseUri = copy.baseUri;
		basePath = copy.basePath;
		updateHandler = copy.updateHandler;
		launcher = copy.launcher;
		properties = new ArrayList<>(copy.properties);
		files = copy.files.stream()
						.map(FileMapper::new)
						.collect(Collectors.toList());
	}

	@Override
	public void parse(Node node) {
		if (!"configuration".equals(node.getNodeName()))
			return;

		timestamp = getAttributeValue(node, "timestamp");

		NodeList children = node.getChildNodes();

		for (int i = 0; i < children.getLength(); i++) {
			Node n = children.item(i);
			if ("base".equals(n.getNodeName())) {
				baseUri = getAttributeValue(n, "uri");
				basePath = getAttributeValue(n, "path");
			} else if ("provider".equals(n.getNodeName())) {
				updateHandler = getAttributeValue(n, "updateHandler");
				launcher = getAttributeValue(n, "launcher");
			} else if ("properties".equals(n.getNodeName())) {
				parseProperties(n.getChildNodes()); // FIXME for removal 
			} else if ("files".equals(n.getNodeName()) || "libraries".equals(n.getNodeName())) {
				parseFiles(n.getChildNodes());
			}
		}

	}

	private void parseProperties(NodeList list) {
		properties = new ArrayList<>();

		for (int i = 0; i < list.getLength(); i++) {
			Node n = list.item(i);
			if ("property".equals(n.getNodeName())) {
				String key = getAttributeValue(n, "key");
				String value = getAttributeValue(n, "value");
				String os = getAttributeValue(n, "os");

				OS osEnum = null;
				if (os != null)
					osEnum = OS.fromShortName(os);

				if (key != null && value != null) {
					properties.add(new Property(key, value, osEnum));
				}
			}
		}
	}

	private void parseFiles(NodeList list) {
		files = new ArrayList<>();

		for (int i = 0; i < list.getLength(); i++) {
			Node n = list.item(i);
			if ("file".equals(n.getNodeName()) || "library".equals(n.getNodeName())) {
				files.add(new FileMapper(n));
			}
		}
	}

	@Override
	public String toXml() {
		StringBuilder builder = new StringBuilder();

		builder.append("<configuration");

		if (timestamp != null) {
			builder.append(" timestamp=\"" + timestamp + "\"");
		}
		builder.append(">\n");

		if (baseUri != null || basePath != null) {
			builder.append("    <base");

			if (baseUri != null) {
				builder.append(" uri=\"" + baseUri + "\"");
			}
			if (basePath != null) {
				builder.append(" path=\"" + basePath + "\"");
			}

			builder.append("/>\n");
		}
		if (updateHandler != null || launcher != null) {
			builder.append("    <provider");

			if (updateHandler != null) {
				builder.append(" updateHandler=\"" + updateHandler + "\"");
			}
			if (launcher != null) {
				builder.append(" launcher=\"" + launcher + "\"");
			}

			builder.append("/>\n");
		}

		if (properties != null && properties.size() > 0) {
			builder.append("    <properties>\n");

			for (Property p : properties) {
				builder.append("        <property");

				builder.append(" key=\"" + p.getKey() + "\"");
				builder.append(" value=\"" + p.getValue() + "\"");

				if (p.getOs() != null)
					builder.append(" os=\"" + p.getOs()
									.getShortName() + "\"");

				builder.append("/>\n");
			}

			builder.append("    </properties>\n");
		}

		if (files != null && files.size() > 0) {
			builder.append("    <files>\n");

			for (FileMapper fm : files) {
				builder.append(fm.toXml());
			}

			builder.append("    </files>\n");
		}

		builder.append("</configuration>");

		return builder.toString();
	}

	//	@Override
	//	public Node toNode(Document doc) {
	//		Element e = doc.createElement("configuration");
	//
	//		if (timestamp != null)
	//			e.setAttribute("timestamp", timestamp);
	//
	//		if (baseUri != null && basePath != null) {
	//			Element base = doc.createElement("base");
	//
	//			if (baseUri != null)
	//				base.setAttribute("uri", baseUri);
	//			if (basePath != null)
	//				base.setAttribute("path", basePath);
	//
	//			e.appendChild(base);
	//		}
	//
	//		if (updateHandler != null && launcher != null) {
	//			Element provider = doc.createElement("provider");
	//
	//			if (updateHandler != null)
	//				provider.setAttribute("updateHandler", updateHandler);
	//			if (launcher != null)
	//				provider.setAttribute("launcher", launcher);
	//
	//			e.appendChild(provider);
	//		}
	//
	//		if (properties != null && properties.size() > 0) {
	//
	//			Element props = doc.createElement("properties");
	//
	//			for (Property p : properties) {
	//				Element prop = doc.createElement("property");
	//				prop.setAttribute("key", p.getKey());
	//				prop.setAttribute("value", p.getValue());
	//
	//				if (p.getOs() != null) {
	//					prop.setAttribute("os", p.getOs()
	//									.getShortName());
	//				}
	//
	//				props.appendChild(prop);
	//			}
	//
	//			e.appendChild(props);
	//		}
	//
	//		if (files != null && files.size() > 0) {
	//
	//			Element f = doc.createElement("files");
	//
	//			for (FileMapper fm : files) {
	//				f.appendChild(fm.toNode(doc));
	//			}
	//
	//			e.appendChild(f);
	//		}
	//
	//		return e;
	//	}

	public static ConfigMapper read(Reader reader) throws IOException {
		try {
			Document doc = DocumentBuilderFactory.newInstance()
							.newDocumentBuilder()
							.parse(new InputSource(reader));
			NodeList list = doc.getChildNodes();
			for (int i = 0; i < list.getLength(); i++) {
				Node n = list.item(i);
				if ("configuration".equals(n.getNodeName())) {
					return new ConfigMapper(n);
				}
			}

			return new ConfigMapper();
		} catch (SAXException | ParserConfigurationException e) {
			throw new IOException(e);
		}
	}

	public void write(Writer writer) throws IOException {
		write(writer, true);
	}

	public void write(Writer writer, boolean header) throws IOException {
		if (header) {
			writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
			writer.write("\n");
			writer.write("<!-- Generated by update4j. Licensed under Apache Software License 2.0 -->\n");
		}

		writer.write(toXml());
	}

}
