package org.update4j.util;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.List;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.Adler32;
import java.util.zip.ZipFile;

import org.update4j.OS;
import org.update4j.service.Service;

public class FileUtils {

	private FileUtils() {
	}

	public static long getChecksum(Path path) throws IOException {
		try (InputStream input = Files.newInputStream(path)) {
			Adler32 checksum = new Adler32();
			byte[] buf = new byte[1024];

			int read;
			while ((read = input.read(buf, 0, buf.length)) > -1)
				checksum.update(buf, 0, read);

			return checksum.getValue();
		}
	}

	public static boolean isJarFile(Path path) throws IOException {
		if (!isZipFile(path)) {
			return false;
		}

		try (ZipFile zip = new ZipFile(path.toFile())) {
			return zip.getEntry("META-INF/MANIFEST.MF") != null;
		}
	}

	public static boolean isZipFile(Path path) throws IOException {
		if (Files.isDirectory(path)) {
			return false;
		}
		if (!Files.isReadable(path)) {
			throw new IOException("Cannot read file " + path.toAbsolutePath());
		}
		if (Files.size(path) < 4) {
			return false;
		}

		try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
			int test = in.readInt();
			return test == 0x504b0304;
		}
	}

	public static byte[] sign(Path path, PrivateKey key) throws IOException {
		try {
			Signature sign = Signature.getInstance("SHA256with" + key.getAlgorithm());
			sign.initSign(key);

			try (InputStream input = Files.newInputStream(path)) {
				byte[] buf = new byte[1024];
				int len;
				while ((len = input.read(buf, 0, buf.length)) > 0)
					sign.update(buf, 0, len);
			}

			return sign.sign();
		} catch (InvalidKeyException | SignatureException e) {
			throw new IOException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}

	public static Path fromUri(URI uri) {
		String path = uri.getPath();

		if (uri.isAbsolute()) {
			path = path.substring(path.lastIndexOf("/") + 1);
		}

		return Paths.get(path);

	}

	public static URI fromPath(Path path) {
		if (path.isAbsolute()) {
			Path filename = path.getFileName();

			return fromPath(filename);
		}

		try {
			String uri = URLEncoder.encode(path.toString().replace("\\", "/"), "UTF-8");
			
			uri = uri.replace("%2F", "/") // We still need directory structure
							.replace("+", "%20"); // "+" only means space in queries, not in paths
			return URI.create(uri);
		} catch (UnsupportedEncodingException e) {
			throw new AssertionError(e);
		}

	}

	public static OS fromFilename(String filename) {
		Pattern osPattern = Pattern.compile(".+-(linux|win|mac)\\.[^.]+");
		Matcher osMatcher = osPattern.matcher(filename);

		if (osMatcher.matches()) {
			return OS.valueOf(osMatcher.group(1));
		}

		return null;
	}

	public static void windowsHide(Path file) {
		try {
			Files.setAttribute(file, "dos:hidden", Boolean.TRUE);
		} catch (Exception e) {
		}
	}
}
