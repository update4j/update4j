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
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Base64;
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
	public String signature;
	public String baseUri;
	public String basePath;
	public String updateHandler;
	public String launcher;
	public final List<Property> properties;
	public final List<FileMapper> files;

	public ConfigMapper() {
		properties = new ArrayList<>();
		files = new ArrayList<>();
	}

	public ConfigMapper(Node node) {
		this();
		parse(node);
	}

	public ConfigMapper(ConfigMapper copy) {
		this();
		timestamp = copy.timestamp;
		signature = copy.signature;
		baseUri = copy.baseUri;
		basePath = copy.basePath;
		updateHandler = copy.updateHandler;
		launcher = copy.launcher;

		properties.addAll(copy.properties);
		files.addAll(copy.files.stream().map(FileMapper::new).collect(Collectors.toList()));
	}

	@Override
	public void parse(Node node) {
		if (!"configuration".equals(node.getNodeName()))
			return;

		timestamp = getAttributeValue(node, "timestamp");
		signature = getAttributeValue(node, "signature");

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
				parseProperties(n.getChildNodes());
			} else if ("files".equals(n.getNodeName())) {
				parseFiles(n.getChildNodes());
			}
		}

	}

	private void parseProperties(NodeList list) {
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
		for (int i = 0; i < list.getLength(); i++) {
			Node n = list.item(i);
			if ("file".equals(n.getNodeName())) {
				files.add(new FileMapper(n));
			}
		}
	}

	@Override
	public String toXml() {
		StringBuilder builder = new StringBuilder();

		builder.append("<configuration");

		// Since anybody can modify these fields, we don't take chances and escape them
		if (timestamp != null) {
			builder.append(" timestamp=\"" + escape(timestamp) + "\"");
		}
		if (signature != null) {
			builder.append(" signature=\"" + escape(signature) + "\"");
		}

		String children = getChildrenXml();

		if (!children.isEmpty()) {
			builder.append(">\n");
			builder.append(children);
			builder.append("</configuration>");
		} else {
			builder.append("/>\n");
		}

		return builder.toString();
	}

	private String getChildrenXml() {

		// no children
		if (baseUri == null && basePath == null && updateHandler == null && launcher == null && properties.isEmpty()
						&& files.isEmpty()) {
			return "";
		}

		StringBuilder builder = new StringBuilder();

		if (baseUri != null || basePath != null) {
			builder.append("    <base");

			if (baseUri != null) {
				builder.append(" uri=\"" + escape(baseUri) + "\"");
			}
			if (basePath != null) {
				builder.append(" path=\"" + escape(basePath) + "\"");
			}

			builder.append("/>\n");
		}
		if (updateHandler != null || launcher != null) {
			builder.append("    <provider");

			if (updateHandler != null) {
				builder.append(" updateHandler=\"" + escape(updateHandler) + "\"");
			}
			if (launcher != null) {
				builder.append(" launcher=\"" + escape(launcher) + "\"");
			}

			builder.append("/>\n");
		}

		if (!properties.isEmpty()) {
			builder.append("    <properties>\n");

			for (Property p : properties) {
				builder.append("        <property");

				builder.append(" key=\"" + escape(p.getKey()) + "\"");
				builder.append(" value=\"" + escape(p.getValue()) + "\"");

				if (p.getOs() != null)
					builder.append(" os=\"" + p.getOs().getShortName() + "\"");

				builder.append("/>\n");
			}

			builder.append("    </properties>\n");
		}

		if (!files.isEmpty()) {
			builder.append("    <files>\n");

			for (FileMapper fm : files) {
				builder.append(fm.toXml());
			}

			builder.append("    </files>\n");
		}

		return builder.toString();
	}

	public String sign(PrivateKey key) {
		try {
			Signature sign = Signature.getInstance("SHA256with" + key.getAlgorithm());
			sign.initSign(key);
			sign.update(getChildrenXml().getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(sign.sign());
		} catch (InvalidKeyException | SignatureException e) {
			throw new RuntimeException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}

	public void verifySignature(PublicKey key) {
		if (signature == null) {
			throw new SecurityException("No signature in configuration root node.");
		}

		try {
			Signature sign = Signature.getInstance("SHA256with" + key.getAlgorithm());
			sign.initVerify(key);
			sign.update(getChildrenXml().getBytes(StandardCharsets.UTF_8));

			if (!sign.verify(Base64.getDecoder().decode(signature))) {
				throw new SecurityException("Signature verification failed.");
			}

		} catch (InvalidKeyException | SignatureException | NoSuchAlgorithmException e) {
			throw new SecurityException(e);
		}
	}

	// @Override
	// public Node toNode(Document doc) {
	// Element e = doc.createElement("configuration");
	//
	// if (timestamp != null)
	// e.setAttribute("timestamp", timestamp);
	//
	// if (baseUri != null && basePath != null) {
	// Element base = doc.createElement("base");
	//
	// if (baseUri != null)
	// base.setAttribute("uri", baseUri);
	// if (basePath != null)
	// base.setAttribute("path", basePath);
	//
	// e.appendChild(base);
	// }
	//
	// if (updateHandler != null && launcher != null) {
	// Element provider = doc.createElement("provider");
	//
	// if (updateHandler != null)
	// provider.setAttribute("updateHandler", updateHandler);
	// if (launcher != null)
	// provider.setAttribute("launcher", launcher);
	//
	// e.appendChild(provider);
	// }
	//
	// if (properties != null && properties.size() > 0) {
	//
	// Element props = doc.createElement("properties");
	//
	// for (Property p : properties) {
	// Element prop = doc.createElement("property");
	// prop.setAttribute("key", p.getKey());
	// prop.setAttribute("value", p.getValue());
	//
	// if (p.getOs() != null) {
	// prop.setAttribute("os", p.getOs()
	// .getShortName());
	// }
	//
	// props.appendChild(prop);
	// }
	//
	// e.appendChild(props);
	// }
	//
	// if (files != null && files.size() > 0) {
	//
	// Element f = doc.createElement("files");
	//
	// for (FileMapper fm : files) {
	// f.appendChild(fm.toNode(doc));
	// }
	//
	// e.appendChild(f);
	// }
	//
	// return e;
	// }

	public static ConfigMapper read(Reader reader) throws IOException {
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(reader));
			NodeList list = doc.getChildNodes();
			for (int i = 0; i < list.getLength(); i++) {
				Node n = list.item(i);
				if ("configuration".equals(n.getNodeName())) {
					return new ConfigMapper(n);
				}
			}

			throw new IllegalStateException("Root element must be 'configuration'.");
		} catch (SAXException | ParserConfigurationException e) {
			throw new IOException(e);
		}
	}

	public void write(Writer writer) throws IOException {
		write(writer, true);
	}

	public void write(Writer writer, boolean header) throws IOException {
		if (header) {
			writer.write("<?xml version=\"1.1\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
			writer.write("\n");
			writer.write("<!-- Generated by update4j. Licensed under Apache Software License 2.0 -->\n");
		}

		writer.write(toXml());
	}

}
