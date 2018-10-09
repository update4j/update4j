package org.update4j.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;
import org.update4j.Bootstrap;
import org.update4j.Configuration;
import org.update4j.FileMetadata;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(TempDirectory.class)
public class SigningTest {

	static PrivateKey privateKey=null;
	static Certificate publicKey=null;


	final static String keypw = "aaaaaa";
	final static String storepw = "aaaaaa";
	final static String keystoreFile= "keystore.jks";
	final static String keyalias = "codeKey";

	final static String jarFile = "gradleExample-1.2.2.jar";
	final static String mainClass = "org.update4jGradleExample.HelloWorldConsole";

	Path local = null;
	Path remote = null;

	@BeforeAll
	public static void beforeAll() throws Exception{
		InputStream storeIs = null;
		try {
			KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
			storeIs=SigningTest.class.getResourceAsStream("/"+keystoreFile);
			keystore.load(storeIs, storepw.toCharArray());
			privateKey = (PrivateKey)keystore.getKey(keyalias, keypw.toCharArray());
			publicKey = keystore.getCertificate(keyalias);
		} finally { //just throw the exception, so build fails
			if(storeIs!=null)
				try{storeIs.close();}catch(Exception e){}
		}
		if(privateKey == null){
			throw new RuntimeException("Private key is null");
		}
		if(publicKey == null){
			throw new RuntimeException("Public key is null");
		}
	}

	@BeforeEach
	public void setup(@TempDirectory.TempDir Path temp){
		local=temp.resolve("local");
		local.toFile().mkdirs();
		remote=temp.resolve("remote");
		remote.toFile().mkdirs();
	}



	@Test
	public void localCertificateWithSignatureTest() throws Throwable{ //everything should work
		//given
		File f=local.resolve("config.xml").toFile();
		assertTrue(!f.exists(),"Directory not cleaned up before test");
		//jar file should have been updated/downloaded
		f=local.resolve(jarFile).toFile();
		assertTrue(!f.exists(),"Directory not cleaned up before test");

		//when

		//create remote with signatures
		//copy a file to remote directory
		Files.copy(Paths.get(SigningTest.class.getResource("/"+jarFile).toURI()),remote.resolve(jarFile));
		//create configuration
		Configuration config = createTestConfig(true,local,remote,remote.resolve("config.xml"));
		//copy certificate
		byte[] key = publicKey.getEncoded();
		FileOutputStream keyfos = new FileOutputStream(local.toString()+"/app.crt");
		keyfos.write(key);
		keyfos.close();

		//run Bootstrap without certificate - should fail and neither download file nor configuration
			Bootstrap.main(new String[]{
					"--syncLocal",
					"--local=" + local.toAbsolutePath() + "/config.xml",
					"--remote=file://" + remote.toAbsolutePath() + "/config.xml",
					"--cert="+local.toString()+"/app.crt"
			});

		//check
		//config should have been updated/downloaded
		f=local.resolve("config.xml").toFile();
		assertTrue(f.exists(),"Config has not been downloaded");
		//jar file should have been updated/downloaded
		f=local.resolve(jarFile).toFile();
		assertTrue(f.exists(),"Jar File has not been downloaded");
	}

	@Test
	public void localCertificateCorruptSignatureTest() throws Throwable{
		//given
		File f=local.resolve("config.xml").toFile();
		assertTrue(!f.exists(),"Directory not cleaned up before test");
		//jar file should have been updated/downloaded
		f=local.resolve(jarFile).toFile();
		assertTrue(!f.exists(),"Directory not cleaned up before test");

		//when
		//create remote with signatures
		//copy a file to remote directory
		Files.copy(Paths.get(SigningTest.class.getResource("/"+jarFile).toURI()),remote.resolve(jarFile));
		//create configuration
		Configuration config = createTestConfig(true,local,remote,remote.resolve("config.xml"));
		//copy certificate
		byte[] key = publicKey.getEncoded();
		FileOutputStream keyfos = new FileOutputStream(local.toString()+"/app.crt");
		keyfos.write(key);
		keyfos.close();

		//modify signature in remote config
		List<String> confStr=new ArrayList<>();
		try (BufferedReader r = Files.newBufferedReader(remote.resolve("config.xml"), StandardCharsets.UTF_8)) {
			r.lines().forEach(s->{
				if(s.indexOf("signature=") == -1){
					confStr.add(s);
				} else {
					confStr.add(s.replaceAll("signature=\".*\"","signature=\"MD0CHHPwYc5zGcTzk+uXMIFUgqlqDVsaQ2stPYvntZECHQCCCz3ZFjHV0/UyT6jPQ5Slq9kLSqtkbDFVgaqY\""));
				}
			});
		}
		try (BufferedWriter w = Files.newBufferedWriter(remote.resolve("config.xml"), StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
			for(String line: confStr){
				w.write(line +"\n");
			}
		}

		//run Bootstrap without certificate - should fail and neither download file nor configuration
		try {
			Bootstrap.main(new String[]{
					"--syncLocal",
					"--local=" + local.toAbsolutePath() + "/config.xml",
					"--remote=file://" + remote.toAbsolutePath() + "/config.xml",
					"--cert=" + local.toString() + "/app.crt"
			});
		} catch (ClassNotFoundException e) {}

		//check
		//config should not have been updated/downloaded
		f=local.resolve("config.xml").toFile();
		assertTrue(!f.exists(),"Config has been downloaded");
		//jar file should not have been updated/downloaded
		f=local.resolve(jarFile).toFile();
		assertTrue(!f.exists(),"Jar File has been downloaded");
	}

	@Test
	public void noCertificateWithSignatureTest() throws Throwable{
		//given
		File f=local.resolve("config.xml").toFile();
		assertTrue(!f.exists(),"Directory not cleaned up before test");
		//jar file should have been updated/downloaded
		f=local.resolve(jarFile).toFile();
		assertTrue(!f.exists(),"Directory not cleaned up before test");

		//when
		//create remote with signatures
		//copy a file to remote directory
		Files.copy(Paths.get(SigningTest.class.getResource("/"+jarFile).toURI()),remote.resolve(jarFile));
		//copy certificate
		byte[] key = publicKey.getEncoded();
		FileOutputStream keyfos = new FileOutputStream(local.toString()+"/app.crt");
		//create configuration
		Configuration config = createTestConfig(true,local,remote,remote.resolve("config.xml"));

		//run Bootstrap without certificate - should fail and neither download file nor configuration
		try {
			Bootstrap.main(new String[]{
					"--syncLocal",
					"--local=" + local.toAbsolutePath() + "/config.xml",
					"--remote=file://" + remote.toAbsolutePath() + "/config.xml"
			});
		} catch(Exception e){ }

		//check
		//config should not have been updated
		f=local.resolve("config.xml").toFile();
		assertTrue(!f.exists(),"Config has been downloaded, but no signatures were provided in the remote config");
		//jar file should not have been loaded
		f=local.resolve(jarFile).toFile();
		assertTrue(!f.exists(),"Jar File has been downloaded, but no signatures were provided in the remote config");
	}

	@Test
	public void localCertificateNoSignatureTest() throws Throwable{
		//given
		File f=local.resolve("config.xml").toFile();
		assertTrue(!f.exists(),"Directory not cleaned up before test");
		//jar file should have been updated/downloaded
		f=local.resolve(jarFile).toFile();
		assertTrue(!f.exists(),"Directory not cleaned up before test");

		//when
		//create remote with signatures
		//copy a file to remote directory
		Files.copy(Paths.get(SigningTest.class.getResource("/"+jarFile).toURI()),remote.resolve(jarFile));
		//create configuration
		Configuration config = createTestConfig(false,local,remote,remote.resolve("config.xml"));

		//run Bootstrap without certificate - should fail and neither download file nor configuration
		try {
			Bootstrap.main(new String[]{
					"--syncLocal",
					"--local=" + local.toAbsolutePath() + "/config.xml",
					"--remote=file://" + remote.toAbsolutePath() + "/config.xml",
					"--cert=" + local.toString() + "/app.crt"
			});
			//fail("Bootstrap did not fail, but no public key has been provided and Configuration includes Signatures");
		} catch(Exception e){ //which Exception should be thrown?!

		}

		//check
		//config should not have been updated
		f=local.resolve("config.xml").toFile();
		assertTrue(!f.exists(),"Config has been downloaded, but no public key has been provided and Configuration includes Signatures");
		//jar file should not have been loaded
		f=local.resolve(jarFile).toFile();
		assertTrue(!f.exists(),"Jar File has been downloaded, but no public key has been provided and Configuration includes Signatures");
	}

	public static Configuration createTestConfig(boolean sign, Path local, Path remote, Path store) throws Exception{

		Configuration.Builder cb = Configuration.builder();
		cb.baseUri("file://"+remote.toString()); //App dir
		cb.basePath(local.toString());
		cb.property("app.name", "UnitTestApp");
		cb.property("default.launcher.main.class", mainClass);
		Path file = remote.resolve(jarFile);
		FileMetadata.Reference fData = FileMetadata.readFrom(file);
		fData.path(jarFile);
		fData.classpath();
		cb.file(fData);
		if(sign)
			cb.signer(privateKey);

		Configuration config = cb.build();
		if(store!=null) {
			BufferedWriter out = null;
			try {
				out = Files.newBufferedWriter(store);
				config.write(out);
			} finally {
				if (out != null) {
					out.close();
				}
			}
		}
		return config;
	}


}
