package org.update4j.service;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.Lists;
import org.junit.Rule;
import org.junit.Test;
import org.update4j.Configuration;
import org.update4j.FileMetadata;
import org.update4j.util.Update4jVersion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;

public class TestDefaultBootstrapRun {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().port(8888));

    @Test
    public void testRemoteXml() throws Throwable {

        stubFor(get(urlEqualTo("/update4j.xml"))
                        .willReturn(aResponse()
                                            .withBody("not a xml")));


        Path path = Paths.get(getClass().getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .getPath());
        while (!path.resolve("pom.xml")
                    .toFile()
                    .exists()) {
            path = path.getParent();
        }

        Path jarToDownloadPath = Paths.get(path.toAbsolutePath()
                                               .toString(),
                                           "../test-launch-classes/target",
                                           "test-launch-classes-"+Update4jVersion.VERSION+".jar");


        Configuration configuration = Configuration
                .builder()
                .baseUri("http://localhost:8888/")
                .basePath(path.toAbsolutePath()
                              .toString() + "/target/blaj")
                .launcher("a.b.c.TestMain")
                .files(Stream.of(FileMetadata
                                         .readFrom(Paths.get("../test-launch-classes/target",
                                                             "test-launch-classes-"+Update4jVersion.VERSION+".jar"))
                                         .uri("jars/test-launch-classes-"+Update4jVersion.VERSION+".jar")
                                         .ignoreBootConflict(true)
                                         .classpath()))
                .build();

        stubFor(get(urlEqualTo("/jars/test-launch-classes-"+Update4jVersion.VERSION+".jar"))
                        .willReturn(aResponse()
                                            .withBody(Files.readAllBytes(jarToDownloadPath))));

        stubFor(get(urlEqualTo("/update4j.xml"))
                        .willReturn(aResponse()
                                            .withBody(configuration.toString())));

        System.getProperties().setProperty("a.b.c.TestMain","false");
        (new DefaultBootstrap(System.out)).main(Lists.newArrayList("--remote",
                                                                   "http://localhost:8888/update4j.xml"));
        assertEquals(System.getProperty("a.b.c.TestMain"),"true");
    }

}
