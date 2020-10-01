package org.update4j.mapper;

import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class MapMapper extends XmlMapper {

    private Map<String, String> map;
    private String name;

    private MapMapper(Map<String, String> map, String name) {
        this.map = map;
        this.name = name;
    }

    private MapMapper(Node node, String name) {
        map = new HashMap<String, String>();
        this.name = name;
        parse(node);
    }
    
    @Override
    public void parse(Node node) {
        if (!node.getNodeName().equals(name))
            return;

        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if ("item".equals(n.getNodeName())) {
                String key = getAttributeValue(n, "key");
                String value = getAttributeValue(n, "value");

                if (key != null && value != null)
                    map.put(key, value);
            }
        }

    }

    @Override
    public String toXml() {
        if (map.isEmpty())
            return "<" + name + "/>";

        StringBuilder builder = new StringBuilder();
        builder.append("<" + name + ">\n");

        for (Map.Entry<String, String> item : map.entrySet()) {
            if (item.getKey() == null || item.getValue() == null)
                continue;

            builder.append("    <item key=\"" + escape(item.getKey()) + "\" value=\"" + escape(item.getValue())
                            + "\"/>\n");
        }

        builder.append("</" + name + ">");

        return builder.toString();
    }

    public static Map<String, String> read(Reader reader) throws IOException {
        return read(reader, "map");
    }

    public static Map<String, String> read(Reader reader, String name) throws IOException {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(reader));
            NodeList list = doc.getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node n = list.item(i);
                if (n.getNodeName().equals(name)) {
                    return new MapMapper(n, name).map;
                }
            }

            throw new IllegalStateException("Root element must be '" + name + "'.");
        } catch (SAXException | ParserConfigurationException e) {
            throw new IOException(e);
        }
    }

    public static void write(Writer writer, Map<String, String> map) throws IOException {
        write(writer, map, "map");
    }

    public static void write(Writer writer, Map<String, String> map, String name) throws IOException {
        MapMapper mapper = new MapMapper(map, name);
        writer.write(mapper.toXml());
    }

}
