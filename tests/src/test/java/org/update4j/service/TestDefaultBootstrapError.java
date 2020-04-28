package org.update4j.service;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.Lists;
import org.junit.Rule;
import org.junit.Test;
import org.update4j.exc.ConnectionException;
import org.update4j.exc.InvalidXmlException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class TestDefaultBootstrapError {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().port(8888));

    @Test(expected = InvalidXmlException.class)
    public void testRemoteNoXml() throws Throwable {
        stubFor(get(urlEqualTo("/update4j.xml"))
                        .willReturn(aResponse()
                                            .withBody("not a xml")));
        (new DefaultBootstrap()).main(Lists.newArrayList("--remote", "http://localhost:8888/update4j.xml"));
    }
    @Test(expected = ConnectionException.class)
    public void testHttp500() throws Throwable {
        stubFor(get(urlEqualTo("/update4j.xml"))
                        .willReturn(aResponse().withStatus(500)));
        (new DefaultBootstrap()).main(Lists.newArrayList("--remote", "http://localhost:8888/update4j.xml"));
    }
    @Test(expected = ConnectionException.class)
    public void testNoConnection() throws Throwable {
        stubFor(get(urlEqualTo("/update4j.xml"))
                        .willReturn(aResponse().withStatus(500)));
        (new DefaultBootstrap()).main(Lists.newArrayList("--remote", "http://localhost:666/update4j.xml"));
    }

}
