package uptodate;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uptodate.util.FileUtils;

public class Library {

	private final URI uri;
	private final Path path;
	private final OS os;
	private final long checksum;
	private final long size;
	private final boolean modulepath;
	private final String comment;
	private final byte[] signature;

	private Library(URI uri, Path path, OS os, long checksum, long size, boolean modulepath, String comment,
					byte[] signature) {
		this.uri = Objects.requireNonNull(uri, "uri");

		if (!uri.isAbsolute()) {
			throw new IllegalArgumentException("Absolute uri required: " + uri);
		}

		this.path = Objects.requireNonNull(path, "path");

		if (!path.isAbsolute()) {
			throw new IllegalArgumentException("Absolute path required: " + path);
		}

		this.os = os;

		if (checksum < 0)
			throw new IllegalArgumentException("Negetive checksum: " + checksum);

		this.checksum = checksum;

		if (size < 0)
			throw new IllegalArgumentException("Negetive file size: " + size);

		this.size = size;
		this.modulepath = modulepath;
		this.comment = comment;
		this.signature = signature;
	}

	public URI getUri() {
		return uri;
	}

	public Path getPath() {
		return path;
	}

	public OS getOs() {
		return os;
	}

	public long getChecksum() {
		return checksum;
	}

	public long getSize() {
		return size;
	}

	public boolean isModulepath() {
		return modulepath;
	}

	public String getComment() {
		return comment;
	}

	public byte[] getSignature() {
		return signature;
	}

	public boolean requiresUpdate() throws IOException {
		if (getOs() != null && getOs() != OS.CURRENT)
			return false;

		return Files.notExists(getPath()) || Files.size(getPath()) != getSize()
						|| FileUtils.getChecksum(getPath()) != getChecksum();
	}

	public static class Reference {
		private Path location;
		private URI uri;
		private Path path;
		private OS os;
		private Boolean modulepath;
		private String comment;

		private Reference(Path location, URI uri, Path path, OS os, Boolean modulepath, String comment) {
			this.location = location;
			this.uri = uri;
			this.path = path;
			this.os = os;
			this.modulepath = modulepath;
			this.comment = comment;
		}

		public Path getLocation() {
			return location;
		}

		public URI getUri() {
			return uri;
		}

		public Path getPath() {
			return path;
		}

		public OS getOs() {
			return os;
		}

		public String getComment() {
			return comment;
		}

		public long getChecksum() throws IOException {
			return FileUtils.getChecksum(location);
		}

		public long getSize() throws IOException {
			return Files.size(location);
		}

		public boolean isModulepath() throws IOException {
			// Non-null and set to false
			if (Boolean.FALSE.equals(modulepath)) {
				return false;
			}

			// modulepath can only be null or true at this point
			// If not a jar, completely ignore modulepath
			return FileUtils.isJarFile(getLocation());
		}

		public byte[] getSignature(PrivateKey key) throws IOException {
			if (key == null)
				return null;

			return FileUtils.sign(location, key);
		}

		Library getLibrary(URI baseUri, Path basePath, PrivateKey key) throws IOException {

			if (getUri() == null && getPath() == null) {
				path = location;
			}

			return Library.withBase(baseUri, basePath)
							.uri(getUri())
							.path(getPath())
							.os(getOs())
							.size(getSize())
							.checksum(getChecksum())
							.modulepath(isModulepath())
							.signature(getSignature(key))
							.comment(getComment())
							.build();

		}

		public static Builder at(Path location) {
			return new Builder(location);
		}

		public static class Builder {
			private Path location;
			private Path path;
			private URI uri;
			private OS os;
			private Boolean modulepath;
			private String comment;

			private Builder(Path location) {
				this.location = location;
			}

			public Builder uri(URI uri) {
				this.uri = uri;

				return this;
			}

			public Builder path(Path path) {
				this.path = path;

				return this;
			}

			public Builder os(OS os) {
				this.os = os;

				return this;
			}

			public Builder modulepath(Boolean mp) {
				this.modulepath = mp;

				return this;
			}

			public Builder comment(String c) {
				comment = c;

				return this;
			}

			public Reference build() {
				return new Reference(location, uri, path, os, modulepath, comment);
			}
		}
	}

	static Builder absolute() {
		return withBase(null, null);
	}

	static Builder withBase(Path path) {
		return withBase(null, path);
	}

	static Builder withBase(URI uri) {
		return withBase(uri, null);
	}

	static Builder withBase(URI uri, Path path) {
		return new Builder(uri, path);
	}

	static class Builder {
		private URI baseUri;
		private Path basePath;

		private URI uri;
		private Path path;
		private OS os;
		private long checksum;
		private long size;
		private boolean modulepath;
		private String comment;
		private byte[] signature;

		private Builder(URI uri, Path path) {
			this.baseUri = uri;
			this.basePath = path;
		}

		Builder uri(URI uri) {
			this.uri = uri;

			return this;
		}

		Builder path(Path path) {
			this.path = path;

			return this;
		}

		Builder os(OS os) {
			this.os = os;

			return this;
		}

		Builder checksum(long checksum) {
			this.checksum = checksum;

			return this;
		}

		Builder checksum(String checksum) {
			return checksum(Long.parseLong(checksum, 16));
		}

		Builder size(long size) {
			this.size = size;

			return this;
		}

		Builder modulepath(boolean mp) {
			this.modulepath = mp;

			return this;
		}

		Builder comment(String comment) {
			this.comment = comment;

			return this;
		}

		Builder signature(byte[] signature) {
			this.signature = signature;

			return this;
		}

		Builder signature(String signature) {
			return signature(Base64.getDecoder().decode(signature));
		}

		Library build() {
			if (path == null && uri != null) {
				path(FileUtils.fromUri(uri));
			}
			if (uri == null && path != null) {
				uri(FileUtils.fromPath(path));
			}

			// relativization gets messed up if relative path has a leading slash
			if (uri != null && !uri.isAbsolute() && uri.getPath().startsWith("/")) {
				uri = URI.create("/").relativize(uri);
			}

			if (path != null && !path.isAbsolute() && path.startsWith("/")) {
				path = Paths.get("/").relativize(path);
			}

			if (os == null && path != null) {
				os(FileUtils.fromFilename(path.toString()));
			}

			if (baseUri != null && uri != null) {
				this.uri = baseUri.resolve(uri);
			}

			if (basePath != null && path != null) {
				this.path = basePath.resolve(path);
			}

			return new Library(uri, path, os, checksum, size, modulepath, comment, signature);
		}
	}
}
