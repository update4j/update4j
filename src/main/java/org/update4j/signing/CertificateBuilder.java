package org.update4j.signing;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.logging.Logger;

/**
 * This class provides some methods to derive Certificates from different sources used as public keys.
 */
public class CertificateBuilder {

	private final static Logger log = Logger.getLogger(CertificateBuilder.class.getName());

	/**
	 * Loads a certificate for the given commandline parameter.
	 * The following forms are supported:
	 * <ul>
	 *     <li><code>path</code>: the path to a certificate on the file system</li>
	 *     <li><code>res:resourcepath</code>: the path to a resource derived from the classloader</li>
	 * </ul>
	 * @param parameter the path to the certificate
	 * @return the certificate
	 * @throws Exception
	 */
	public static Certificate from(String parameter) throws Exception{
		if(parameter == null){
			throw new IllegalArgumentException("Can't read certificate from null");
		}
		if(parameter.indexOf("res:")==0){
			return fromResource(parameter.substring(4));
		}
		return fromFile(Paths.get(parameter));
	}

	/**
	 * Loads a certificate from a file
	 * @param file the path of the file
	 * @return the certificate
	 * @throws Exception
	 */
	public static Certificate fromFile(Path file) throws Exception{
		try (InputStream in = Files.newInputStream(file)) {
			return fromStream(in);
		}
	}

	/**
	 * Loads a certificate as a resource by the classloader. Remember to add a leading / for the root of the jar file.
	 * @param resourceName the resource path
	 * @return the certificate
	 * @throws Exception
	 */
	public static Certificate fromResource(String resourceName) throws Exception{
		try (InputStream in = CertificateBuilder.class.getResourceAsStream(resourceName)) {
			return fromStream(in);
		}
	}

	/**
	 * Loads a certificate from an inputstream.
	 * @param input the inputstream
	 * @return the certificate
	 * @throws Exception
	 */
	public static Certificate fromStream(InputStream input) throws Exception{
		java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
		return cf.generateCertificate(input);
	}

	/**
	 * Loads a Certificate from a keystore file in jks format
	 * @param keystoreFile the path to the file
	 * @param keyalias the alias of the key to retrieve the certificate for
	 * @param storepw the password of the keystore
	 * @return the certificate
	 * @throws Exception
	 */
	public static Certificate fromStoreFile(Path keystoreFile, String keyalias, String storepw) throws Exception{
		try (InputStream storeIs = Files.newInputStream(keystoreFile)){
			return fromStoreStream(storeIs, keyalias, storepw);
		}
	}

	/**
	 * Loads a Certificate from a keystore in jks format using the classloader to derive the resource.
	 * Remember to add a leading / for the root of the jar file.
	 * @param resourceName the path to the file
	 * @param keyalias the alias of the key to retrieve the certificate for
	 * @param storepw the password of the keystore
	 * @return the certificate
	 * @throws Exception
	 */
	public static Certificate fromStoreResource(String resourceName, String keyalias, String storepw) throws Exception{
		try (InputStream storeIs = CertificateBuilder.class.getResourceAsStream(resourceName)){
			return fromStoreStream(storeIs, keyalias, storepw);
		}
	}

	/**
	 * Loads a Certificate from a keystore in jks format as input stream.
	 * @param input the input stream of the keystore
	 * @param keyalias the alias of the key to retrieve the certificate for
	 * @param storepw the password of the keystore
	 * @return the certificate
	 * @throws Exception
	 */
	public static Certificate fromStoreStream(InputStream input, String keyalias, String storepw) throws Exception{
		KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
		keystore.load(input, storepw.toCharArray());
		return keystore.getCertificate(keyalias);
	}
}
