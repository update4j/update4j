package org.update4j.util;

import org.junit.jupiter.api.Test;
import org.update4j.util.CertificateBuilder;

import java.nio.file.Paths;
import java.security.cert.Certificate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class CertificateBuilderTest {

	@Test
	public void fromTest() throws Exception{
		Certificate c = CertificateBuilder.from("res:/certificate.crt");

		assertTrue(c.getPublicKey()!=null);

		c = CertificateBuilder.from("src/test/resources/certificate.crt");

		assertTrue(c.getPublicKey()!=null);

		try{
			CertificateBuilder.from(null);
			fail("Should throw an Exception with null as parameter");
		} catch(IllegalArgumentException e){ }
	}

	@Test
	public void fromResourceTest() throws Exception{
		Certificate c = CertificateBuilder.fromResource("/certificate.crt");

		assertTrue(c.getPublicKey()!=null);
	}
	@Test
	public void fromFileTest() throws Exception{
		Certificate c = CertificateBuilder.from("src/test/resources/certificate.crt");

		assertTrue(c.getPublicKey()!=null);
	}


	@Test
	public void fromStoreResourceTest() throws Exception{
		Certificate c = CertificateBuilder.fromStoreResource("/keystore.jks", "codekey","aaaaaa");

		assertTrue(c.getPublicKey()!=null);
	}
	@Test
	public void fromStoreFileTest() throws Exception{
		Certificate c = CertificateBuilder.fromStoreFile(Paths.get("src/test/resources/keystore.jks"), "codekey","aaaaaa");

		assertTrue(c.getPublicKey()!=null);
	}

	@Test
	public void constructorTest(){
		//shall not explode
		new CertificateBuilder();
	}
}
