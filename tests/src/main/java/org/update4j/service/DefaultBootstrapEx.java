package org.update4j.service;

import org.update4j.Configuration;
import org.update4j.exc.ConnectionException;
import org.update4j.exc.ExceptionUtils;
import org.update4j.exc.InvalidXmlException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.PrintStream;
import java.security.PublicKey;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultBootstrapEx extends DefaultBootstrap {

    final private PrintStream out;

    public DefaultBootstrapEx() {
        out = null;
    }

    public DefaultBootstrapEx(PrintStream out) {
        this.out = out;
    }

    @Override
    protected void launchFirst() throws Throwable {
        log("launching application...");
        super.launchFirst();
    }

    @Override
    protected void updateFirst() throws Throwable {
        log("will update application...");
        super.updateFirst();
    }

    @Override
    protected Configuration getRemoteConfig() {
        log("getting remote config from " + getRemote());
        Configuration remoteConfig = super.getRemoteConfig();
        return remoteConfig;
    }

    @Override
    protected Configuration getLocalConfig(boolean ignoreFileNotFound) {
        log("reading local config from " + getLocal().toString());
        return super.getLocalConfig(ignoreFileNotFound);
    }

    @Override
    protected boolean configUpdate(Configuration config, PublicKey publicKey) {
        log("requires update ...");
        boolean success = super.configUpdate(config, publicKey);
        if (!success) {
            log("remote update failed ...");
        } else {
            log("remote update succeeded ...");
        }
        return success;
    }

    @Override
    protected void configLaunch(Configuration config) {
        log("launching application from " + config.getFiles()
                                                  .stream()
                                                  .map(f -> f.getPath()
                                                             .toString())
                                                  .collect(Collectors.joining(", ")) + "...");
        log("using bootstrapping from version %s", getVersion());
        log("starting with properties: " + config.getProperties()
                                                 .stream()
                                                 .map(p -> "[" + p.getKey() + "," + p.getValue() + "]")
                                                 .collect(
                                                         Collectors.joining(";\n", "{", "}")));
        super.configLaunch(config);
    }

    @Override
    protected void syncLocal(Configuration remoteConfig) {
        log("syncing local with remote config " + getLocal());
        super.syncLocal(remoteConfig);
    }

    protected void log(String str) {
        if (out != null) {
            out.println(str);
        }
    }

    protected void log(String str, String... args) {
        if (out != null) {
            out.println(String.format(str, args));
        }
    }

}
