package org.update4j.service;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.update4j.Configuration;
import org.update4j.FileMetadata;
import org.update4j.util.Update4jVersion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.*;

public class TestDefaultBootstrapRun {

    public static final String TEST_CLASS = "a.b.c.TestMain";
    public static final String TEST_APPLICATION_MODULE_NAME = "test-launch-classes";
    public static final String BASE_URI = "http://localhost:8888";
    private Path moduleBaseDir;
    private String update4jBasePath;
    private Configuration.Builder configurationBuilder;


    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().port(8888));
    private Path jarToDownloadPath;

    @Before
    public void before() throws IOException {
        moduleBaseDir = getModuleBaseDir(getClass());
        update4jBasePath = moduleBaseDir.toAbsolutePath()
                                        .toString() + "/target/blaj";
        deleteAll(update4jBasePath);

        jarToDownloadPath = Paths.get(moduleBaseDir.toAbsolutePath()
                                                   .toString(),
                                      "../" + TEST_APPLICATION_MODULE_NAME + "/target",
                                      TEST_APPLICATION_MODULE_NAME + "-" + Update4jVersion.VERSION + ".jar");


        configurationBuilder = Configuration
                .builder()
                .baseUri(BASE_URI)
                .basePath(update4jBasePath)
                .launcher(TEST_CLASS)
                .files(Stream.of(FileMetadata
                                         .readFrom(Paths.get("../" + TEST_APPLICATION_MODULE_NAME + "/target",
                                                             TEST_APPLICATION_MODULE_NAME + "-" + Update4jVersion.VERSION + ".jar"))
                                         .uri("jars/" + TEST_APPLICATION_MODULE_NAME + "-" + Update4jVersion.VERSION + ".jar")
                                         .ignoreBootConflict(true)
                                         .classpath()))
        ;


    }


    @Test
    public void testBootstrap() throws Throwable {
        stubFor(get(urlEqualTo("/jars/" + TEST_APPLICATION_MODULE_NAME + "-" + Update4jVersion.VERSION + ".jar"))
                        .willReturn(aResponse()
                                            .withBody(Files.readAllBytes(jarToDownloadPath))));

        Configuration configuration = configurationBuilder.build();
        stubFor(get(urlEqualTo("/update4j.xml"))
                        .willReturn(aResponse()
                                            .withBody(configuration.toString())));

        System.getProperties()
              .setProperty(TEST_CLASS, "false");
        (new DefaultBootstrapEx(System.out)).main(Lists.newArrayList("--remote",
                                                                   BASE_URI + "/update4j.xml"));
        assertEquals(System.getProperty(TEST_CLASS), "true");
    }

    @Test
    public void testConfigurationSlow() throws Throwable {
        stubFor(get(urlEqualTo("/jars/" + TEST_APPLICATION_MODULE_NAME + "-" + Update4jVersion.VERSION + ".jar"))
                        .willReturn(aResponse()
                                            .withFixedDelay(1000)
                                            .withBody(Files.readAllBytes(jarToDownloadPath))));


        Configuration configuration = configurationBuilder.build();
        stubFor(get(urlEqualTo("/update4j.xml"))
                        .willReturn(aResponse()
                                            .withBody(configuration.toString())
                        ));

        System.getProperties()
              .setProperty(TEST_CLASS, "false");
        DefaultUpdateHandler handler = new DefaultUpdateHandler(){
            @Override
            public InputStream openDownloadStream(FileMetadata file) throws Throwable {
                URLConnection connection = file.getUri().toURL().openConnection();
                connection.addRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setConnectTimeout(100);
                connection.setReadTimeout(100);
                return connection.getInputStream();
            }
        };

        assertTrue(configuration.requiresUpdate());

        boolean update = configuration.update(handler);
        assertFalse(update);
        assertEquals(System.getProperty(TEST_CLASS), "false");
    }


    private void deleteAll(String update4jBasePath) {
        try {
            Files.walk(Paths.get(update4jBasePath))
                 .filter(Files::isRegularFile)
                 .map(Path::toFile)
                 .forEach(File::delete);
        } catch (IOException e) {
            //nada
        }
    }

    private Path getModuleBaseDir(Class aClass) {
        Path path = Paths.get(aClass.getProtectionDomain()
                                    .getCodeSource()
                                    .getLocation()
                                    .getPath());
        while (!path.resolve("pom.xml")
                    .toFile()
                    .exists()) {
            path = path.getParent();
        }
        return path;
    }

}
