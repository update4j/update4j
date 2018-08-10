/*
 * Copyright 2018 Mordechai Meisels
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.update4j;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.update4j.util.FileUtils;

public class Library {

	private final URI uri;
	private final Path path;
	private final OS os;
	private final long checksum;
	private final long size;
	private final boolean classpath;
	private final boolean modulepath;
	private final String comment;
	private final boolean ignoreBootConflict;
	private final byte[] signature;

	private final List<AddPackage> addExports;
	private final List<AddPackage> addOpens;
	private final List<String> addReads;

	private Library(URI uri, Path path, OS os, long checksum, long size, boolean classpath, boolean modulepath,
					String comment, boolean ignoreBootConflict, byte[] signature, List<AddPackage> addExports,
					List<AddPackage> addOpens, List<String> addReads, boolean fromFile) {

		this.uri = uri;

		// parsing properties might fail sometimes when not on current os, so let it through
		if (!fromFile || os == null || os == OS.CURRENT) {
			Objects.requireNonNull(uri, "uri");

			if (!uri.isAbsolute()) {
				throw new IllegalArgumentException("Absolute uri required: " + uri);
			}
		}

		this.path = path;

		if (!fromFile || os == null || os == OS.CURRENT) {
			Objects.requireNonNull(path, "path");

			if (!path.isAbsolute()) {
				throw new IllegalArgumentException("Absolute path required: " + path);
			}
		}

		this.os = os;

		if (checksum < 0)
			throw new IllegalArgumentException("Negative checksum: " + checksum);

		this.checksum = checksum;

		if (size < 0)
			throw new IllegalArgumentException("Negative file size: " + size);

		this.size = size;
		this.classpath = classpath;
		this.modulepath = modulepath;
		this.comment = comment;
		this.ignoreBootConflict = ignoreBootConflict;
		this.signature = signature;

		this.addExports = Collections.unmodifiableList(addExports);
		this.addOpens = Collections.unmodifiableList(addOpens);
		this.addReads = Collections.unmodifiableList(addReads);
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

	public boolean isClasspath() {
		return classpath;
	}

	public boolean isModulepath() {
		return modulepath;
	}

	public String getComment() {
		return comment;
	}

	public boolean isIgnoreBootConflict() {
		return ignoreBootConflict;
	}

	public byte[] getSignature() {
		return signature;
	}

	public List<AddPackage> getAddExports() {
		return addExports;
	}

	public List<AddPackage> getAddOpens() {
		return addOpens;
	}

	public List<String> getAddReads() {
		return addReads;
	}

	public boolean requiresUpdate() throws IOException {
		if (getOs() != null && getOs() != OS.CURRENT)
			return false;

		return Files.notExists(getPath()) || Files.size(getPath()) != getSize()
						|| FileUtils.getChecksum(getPath()) != getChecksum();
	}

	public static Reference.Builder at(Path location) {
		return Library.Reference.at(location);
	}

	public static Reference.Builder at(String location) {
		return Library.Reference.at(location);
	}

	public static class Reference {
		private Path location;
		private String uri;
		private String path;
		private OS os;
		private Boolean classpath;
		private Boolean modulepath;
		private String comment;
		private Boolean ignoreBootConflict;

		private List<AddPackage> addExports;
		private List<AddPackage> addOpens;
		private List<String> addReads;

		private Reference(Path location, String uri, String path, OS os, Boolean classpath, Boolean modulepath,
						String comment, Boolean ignoreBootConflict, List<AddPackage> addExports,
						List<AddPackage> addOpens, List<String> addReads) {
			this.location = location;
			this.uri = uri;
			this.path = path;
			this.os = os;
			this.classpath = classpath;
			this.modulepath = modulepath;
			this.comment = comment;
			this.ignoreBootConflict = ignoreBootConflict;

			this.addExports = addExports;
			this.addOpens = addOpens;
			this.addReads = addReads;
		}

		public Path getLocation() {
			return location;
		}
		
		public String getUri() {
			return uri;
		}

		public URI getUriResolved(Configuration config) {
			if (getUri() == null)
				return null;

			return URI.create(config.resolvePlaceholders(getUri(), true, os != null && os != OS.CURRENT));
		}
		
		public String getPath() {
			return path;
		}

		public Path getPathResolved(Configuration config) {
			if (getPath() == null)
				return null;

			return Paths.get(config.resolvePlaceholders(getPath(), true, os != null && os != OS.CURRENT));
		}

		public OS getOs() {
			return os;
		}
		
		public String getComment() {
			return comment;
		}

		public String getCommentResolved(Configuration config) {
			return config.resolvePlaceholders(getComment());
		}

		public long getChecksum() throws IOException {
			return FileUtils.getChecksum(location);
		}

		public long getSize() throws IOException {
			return Files.size(location);
		}

		public boolean isClasspath() {
			// Non-null and true
			// null defaults to false
			return Boolean.TRUE.equals(classpath);
		}

		public boolean isModulepath() throws IOException {
			if (modulepath == null || !modulepath) {
				return false;
			}

			// If not a jar, completely ignore modulepath
			return FileUtils.isJarFile(getLocation());
		}

		public boolean isIgnoreBootConflict() {
			return Boolean.TRUE.equals(ignoreBootConflict);
		}

		public byte[] getSignature(PrivateKey key) throws IOException {
			if (key == null)
				return null;

			return FileUtils.sign(location, key);
		}

		public List<AddPackage> getAddExports() {
			return addExports;
		}

		public List<AddPackage> getAddOpens() {
			return addOpens;
		}

		public List<String> getAddReads() {
			return addReads;
		}

		Library getLibrary(Configuration config, PrivateKey key) throws IOException {

			if (getUriResolved(config) == null && getPathResolved(config) == null) {
				path = location.toString();
			}

			return Library.withBase(config.getBaseUri(), config.getBasePath())
							.uri(getUriResolved(config))
							.path(getPathResolved(config))
							.os(getOs())
							.size(getSize())
							.checksum(getChecksum())
							.classpath(isClasspath())
							.modulepath(isModulepath())
							.ignoreBootConflict(isIgnoreBootConflict())
							.signature(getSignature(key))
							.comment(getCommentResolved(config))
							.exports(getAddExports())
							.opens(getAddOpens())
							.reads(getAddReads())
							.build(false);

		}

		public static Builder at(Path location) {
			return new Builder(location);
		}

		public static Builder at(String location) {
			return at(Paths.get(location));
		}

		public static class Builder {
			private Path location;
			private String path;
			private String uri;
			private OS os;
			private Boolean classpath;
			private Boolean modulepath;
			private String comment;
			private Boolean ignoreBootConflict;

			private List<AddPackage> addExports;
			private List<AddPackage> addOpens;
			private List<String> addReads;

			private Builder(Path location) {
				this.location = location;

				addExports = new ArrayList<>();
				addOpens = new ArrayList<>();
				addReads = new ArrayList<>();
			}

			public Builder uri(URI uri) {
				return uri(uri == null ? null : uri.toString());
			}

			public Builder uri(String uri) {
				this.uri = uri;

				return this;
			}

			public Builder path(Path path) {
				return path(path == null ? null : path.toString());
			}

			public Builder path(String path) {
				this.path = path;

				return this;
			}

			public Builder os(OS os) {
				this.os = os;

				return this;
			}

			public Builder classpath(boolean cp) {
				this.classpath = cp;

				return this;
			}

			public Builder classpath() {
				return classpath(true);
			}

			public Builder modulepath(boolean mp) {
				this.modulepath = mp;

				return this;
			}

			public Builder modulepath() {
				return modulepath(true);
			}

			public Builder comment(String c) {
				comment = c;

				return this;
			}

			public Builder ignoreBootConflict(boolean b) {
				ignoreBootConflict = b;

				return this;
			}

			public Builder ignoreBootConflict() {
				return ignoreBootConflict(true);
			}

			public Builder exports(String pkg, String targetModule) {
				addExports.add(new AddPackage(Objects.requireNonNull(pkg), Objects.requireNonNull(targetModule)));

				return this;
			}

			public Builder opens(String pkg, String targetModule) {
				addOpens.add(new AddPackage(Objects.requireNonNull(pkg), Objects.requireNonNull(targetModule)));

				return this;
			}

			public Builder reads(String module) {
				addReads.add(Objects.requireNonNull(module));

				return this;
			}

			public Reference build() {
				return new Reference(location, uri, path, os, classpath, modulepath, comment, ignoreBootConflict,
								addExports, addOpens, addReads);
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
		private boolean classpath;
		private boolean modulepath;
		private String comment;
		private boolean ignoreBootConflict;
		private byte[] signature;

		private List<AddPackage> addExports;
		private List<AddPackage> addOpens;
		private List<String> addReads;

		private Builder(URI uri, Path path) {
			this.baseUri = uri;
			this.basePath = path;

			addExports = new ArrayList<>();
			addOpens = new ArrayList<>();
			addReads = new ArrayList<>();
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

		Builder classpath(boolean cp) {
			this.classpath = cp;

			return this;
		}

		Builder modulepath(boolean mp) {
			this.modulepath = mp;

			return this;
		}

		Builder ignoreBootConflict(boolean b) {
			this.ignoreBootConflict = b;

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

		private void validateAddReads(List<String> list) {
			for (String read : list) {
				Objects.requireNonNull(read);
				if (read.isEmpty()) {
					throw new IllegalArgumentException("Missing module name.");
				}
			}
		}

		private void validateAddPackages(List<AddPackage> list) {
			for (AddPackage add : list) {
				Objects.requireNonNull(add);
				if (add.getPackageName() == null || add.getPackageName().isEmpty()) {
					throw new IllegalArgumentException("Missing package name.");
				}
				if (add.getTargetModule() == null || add.getTargetModule().isEmpty()) {
					throw new IllegalArgumentException("Missing module name.");
				}
			}
		}

		Builder exports(List<AddPackage> exports) {
			validateAddPackages(exports);

			addExports.addAll(exports);

			return this;
		}

		Builder opens(List<AddPackage> opens) {
			validateAddPackages(opens);

			addOpens.addAll(opens);

			return this;
		}

		Builder reads(List<String> reads) {
			validateAddReads(reads);

			addReads.addAll(reads);

			return this;
		}

		Library build(boolean fromFile) {
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

			return new Library(uri, path, os, checksum, size, classpath, modulepath, comment, ignoreBootConflict,
							signature, addExports, addOpens, addReads, fromFile);
		}
	}
}
