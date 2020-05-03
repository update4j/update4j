package org.update4j.service;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.update4j.Configuration;
import org.update4j.FileMetadata;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.*;

public class TestDefaultBootstrapRun {

    public static final String TEST_APPLICATION_MODULE_NAME = "test-classes-to-launch";
    public static final String TEST_APPLICATION_JAR_NAME_1 = "test-classes-to-launch-1.jar";
    public static final String BASE_URI = "http://localhost:8888";
    private Configuration.Builder configurationBuilder;

    private List<Throwable> uncaughtExceptions = Lists.newArrayList();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().port(8888));
    private Path jarToDownloadPath;

    @Before
    public void before()  {
        Path moduleBaseDir = getModuleBaseDir(getClass());
        String update4jBasePath = moduleBaseDir.toAbsolutePath()
                                               .toString() + "/target/blaj";
        //clean local basepath to trigger update
        deleteAll(update4jBasePath);

        //path to jar in test-classes-to-launch
        jarToDownloadPath = Paths.get(moduleBaseDir.toAbsolutePath()
                                                   .toString(),
                                      "../" + TEST_APPLICATION_MODULE_NAME + "/target",
                                      TEST_APPLICATION_JAR_NAME_1);


        configurationBuilder = Configuration
                .builder()
                .baseUri(BASE_URI)
                .basePath(update4jBasePath)
                .files(Stream.of(FileMetadata
                                         .readFrom(Paths.get("../" + TEST_APPLICATION_MODULE_NAME + "/target",
                                                             TEST_APPLICATION_JAR_NAME_1))
                                         .uri("jars/" + TEST_APPLICATION_JAR_NAME_1)
                                         .ignoreBootConflict(true)
                                         .classpath()))
        ;

        //catch all uncaught exceptions
        uncaughtExceptions.clear();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                uncaughtExceptions.add(e);
            }
        });
    }


    @Test
    public void testBootstrap() throws Throwable {
        stubFor(get(urlEqualTo("/jars/" + TEST_APPLICATION_JAR_NAME_1))
                        .willReturn(aResponse()
                                            .withBody(Files.readAllBytes(jarToDownloadPath))));

        String testClass = "a.b.c.TestMain";
        Configuration configuration = configurationBuilder
                .property(DefaultLauncher.MAIN_CLASS_PROPERTY_KEY, testClass)
                .build();

        //stub config
        stubFor(get(urlEqualTo("/update4j.xml"))
                        .willReturn(aResponse()
                                            .withBody(configuration.toString())));

        System.getProperties()
              .setProperty(testClass, "false");
        (new DefaultBootstrap()).main(Lists.newArrayList("--remote",
                                                         BASE_URI + "/update4j.xml"));
        assertEquals("true", System.getProperty(testClass));
        assertEquals(0, uncaughtExceptions.size());
    }

    @Test
    public void testFailedBootstrap() throws Throwable {
        stubFor(get(urlEqualTo("/jars/" + TEST_APPLICATION_JAR_NAME_1))
                        .willReturn(aResponse()
                                            .withBody(Files.readAllBytes(jarToDownloadPath))));

        String testClass = "a.b.c.FailingTestMain";
        Configuration configuration = configurationBuilder
                .property(DefaultLauncher.MAIN_CLASS_PROPERTY_KEY, testClass)
                .build();
        stubFor(get(urlEqualTo("/update4j.xml"))
                        .willReturn(aResponse()
                                            .withBody(configuration.toString())));

        System.getProperties()
              .setProperty(testClass, "false");
        (new DefaultBootstrap()).main(Lists.newArrayList("--remote",
                                                         BASE_URI + "/update4j.xml"));
        assertEquals("true", System.getProperty(testClass));
        assertEquals(1, uncaughtExceptions.size());
    }

    @Test
    public void testUpdateSlowConnection() throws Throwable {
        stubFor(get(urlEqualTo("/jars/" + TEST_APPLICATION_JAR_NAME_1))
                        .willReturn(aResponse()
                                            .withFixedDelay(1000)
                                            .withBody(Files.readAllBytes(jarToDownloadPath))));


        String testClass = "a.b.c.TestMain";
        Configuration configuration = configurationBuilder
                .property(DefaultLauncher.MAIN_CLASS_PROPERTY_KEY, testClass)
                .build();
        stubFor(get(urlEqualTo("/update4j.xml"))
                        .willReturn(aResponse()
                                            .withBody(configuration.toString())
                        ));

        System.getProperties()
              .setProperty(testClass, "false");
        DefaultUpdateHandler handler = new DefaultUpdateHandler() {
            @Override
            public InputStream openDownloadStream(FileMetadata file) throws Throwable {
                URLConnection connection = file.getUri()
                                               .toURL()
                                               .openConnection();
                connection.addRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setConnectTimeout(100);
                connection.setReadTimeout(100);
                return connection.getInputStream();
            }
        };

        assertTrue(configuration.requiresUpdate());

        boolean update = configuration.update(handler);
        assertFalse(update);
        assertEquals("false", System.getProperty(testClass));
        assertEquals(0, uncaughtExceptions.size());
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
