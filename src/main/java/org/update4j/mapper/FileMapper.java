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
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import org.update4j.AddPackage;
import org.update4j.OS;
import org.update4j.util.FileUtils;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class FileMapper extends XmlMapper {

    public String uri;
    public String path;
    public String checksum;
    public Long size;
    public OS os;
    public Boolean classpath;
    public Boolean modulepath;
    public String comment;
    public Boolean ignoreBootConflict;
    public String signature;
    public final List<AddPackage> addExports;
    public final List<AddPackage> addOpens;
    public final List<String> addReads;

    public FileMapper() {
        addExports = new ArrayList<>();
        addOpens = new ArrayList<>();
        addReads = new ArrayList<>();
    }

    public FileMapper(Node node) {
        this();
        parse(node);
    }

    public FileMapper(FileMapper copy) {
        this();
        uri = copy.uri;
        path = copy.path;
        checksum = copy.checksum;
        size = copy.size;
        os = copy.os;
        classpath = copy.classpath;
        modulepath = copy.modulepath;
        comment = copy.comment;
        ignoreBootConflict = copy.ignoreBootConflict;
        signature = copy.signature;

        addExports.addAll(copy.addExports);
        addOpens.addAll(copy.addOpens);
        addReads.addAll(copy.addReads);
    }

    @Override
    public void parse(Node node) {
        if (!"file".equals(node.getNodeName()))
            return;

        uri = getAttributeValue(node, "uri");
        path = getAttributeValue(node, "path");
        checksum = getAttributeValue(node, "checksum");

        String size = getAttributeValue(node, "size");
        if (size != null) {
            this.size = Long.parseLong(size);
        }

        String os = getAttributeValue(node, "os");
        if (os != null) {
            this.os = OS.fromShortName(os);
        }

        String classpath = getAttributeValue(node, "classpath");
        if (classpath != null) {
            this.classpath = Boolean.parseBoolean(classpath);
        }

        String modulepath = getAttributeValue(node, "modulepath");
        if (modulepath != null) {
            this.modulepath = Boolean.parseBoolean(modulepath);
        }

        comment = getAttributeValue(node, "comment");

        String ignoreBootConflict = getAttributeValue(node, "ignoreBootConflict");
        if (ignoreBootConflict != null) {
            this.ignoreBootConflict = Boolean.parseBoolean(ignoreBootConflict);
        }

        signature = getAttributeValue(node, "signature");

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if ("addExports".equals(n.getNodeName())) {
                parseExports(n.getChildNodes());
            } else if ("addOpens".equals(n.getNodeName())) {
                parseOpens(n.getChildNodes());
            } else if ("addReads".equals(n.getNodeName())) {
                parseReads(n.getChildNodes());
            }
        }
    }

    private void parseExports(NodeList list) {
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if ("exports".equals(n.getNodeName())) {
                String packageName = getAttributeValue(n, "package");
                String targetModule = getAttributeValue(n, "target");
                if (packageName != null && targetModule != null) {
                    addExports.add(new AddPackage(packageName, targetModule));
                }
            }
        }
    }

    private void parseOpens(NodeList list) {
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if ("opens".equals(n.getNodeName())) {
                String packageName = getAttributeValue(n, "package");
                String targetModule = getAttributeValue(n, "target");
                if (packageName != null && targetModule != null) {
                    addOpens.add(new AddPackage(packageName, targetModule));
                }
            }
        }
    }

    private void parseReads(NodeList list) {
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if ("reads".equals(n.getNodeName())) {
                String module = getAttributeValue(n, "module");
                if (module != null) {
                    addReads.add(module);
                }
            }
        }
    }

    @Override
    public String toXml() {
        StringBuilder builder = new StringBuilder();
        builder.append("        <file");

        if (uri != null) {
            builder.append(" uri=\"" + escape(uri) + "\"");
        }
        if (path != null) {
            builder.append(" path=\"" + escape(path) + "\"");
        }
        if (size != null) {
            builder.append(" size=\"" + size + "\"");
        }
        if (checksum != null) {
            builder.append(" checksum=\"" + escape(checksum) + "\"");
        }
        if (os != null) {
            builder.append(" os=\"" + os.getShortName() + "\"");
        }
        if (classpath != null && classpath) {
            builder.append(" classpath=\"true\"");
        }
        if (modulepath != null && modulepath) {
            builder.append(" modulepath=\"true\"");
        }
        if (comment != null) {
            builder.append(" comment=\"" + escape(comment) + "\"");
        }
        if (ignoreBootConflict != null && ignoreBootConflict) {
            builder.append(" ignoreBootConflict=\"true\"");
        }
        if (signature != null) {
            builder.append(" signature=\"" + escape(signature) + "\"");
        }

        if (!addExports.isEmpty() || !addOpens.isEmpty() || !addReads.isEmpty()) {

            builder.append(">\n");

            if (!addExports.isEmpty()) {
                builder.append("            <addExports>\n");

                for (AddPackage ap : addExports) {
                    builder.append("                <exports");
                    builder.append(" package=\"" + escape(ap.getPackageName()) + "\"");
                    builder.append(" target=\"" + escape(ap.getTargetModule()) + "\"/>\n");
                }

                builder.append("            </addExports>\n");
            }

            if (!addOpens.isEmpty()) {
                builder.append("            <addOpens>\n");

                for (AddPackage ap : addOpens) {
                    builder.append("                <opens");
                    builder.append(" package=\"" + escape(ap.getPackageName()) + "\"");
                    builder.append(" target=\"" + escape(ap.getTargetModule()) + "\"/>\n");
                }

                builder.append("            </addOpens>\n");
            }

            if (!addReads.isEmpty()) {
                builder.append("            <addReads>\n");

                for (String r : addReads) {
                    builder.append("                <reads");
                    builder.append(" module=\"" + escape(r) + "\"/>\n");
                }

                builder.append("            </addReads>\n");
            }

            builder.append("        </file>\n");
        } else {
            builder.append("/>\n");
        }

        return builder.toString();
    }

    public static long getChecksum(Path path) throws IOException {
        return FileUtils.getChecksum(path);
    }

    public static String getChecksumHex(Path path) throws IOException {
        return FileUtils.getChecksumString(path);
    }

    public static byte[] sign(Path path, PrivateKey key) throws IOException {
        return FileUtils.sign(path, key);
    }

    public static String signAndEncode(Path path, PrivateKey key) throws IOException {
        return FileUtils.signAndEncode(path, key);
    }
}