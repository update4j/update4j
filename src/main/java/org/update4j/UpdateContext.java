package org.update4j;

import java.nio.file.Path;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class UpdateContext {

	private Configuration configuration;

	private List<Library> requiresUpdate;
	private List<Library> updated;
	private Path tempDir;

	private Certificate certificate;

	UpdateContext(Configuration config, List<Library> requiresUpdate, List<Library> updated, Path tempDir,
					Certificate cert) {
		configuration = Objects.requireNonNull(config);

		this.requiresUpdate = Collections.unmodifiableList(requiresUpdate);
		this.updated = Collections.unmodifiableList(updated);
		this.tempDir = tempDir;

		certificate = cert;
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public List<Library> getRequiresUpdate() {
		return requiresUpdate;
	}

	public List<Library> getUpdated() {
		return updated;
	}

	public Path getTempDirectory() {
		return tempDir;
	}

	public Certificate getCertificate() {
		return certificate;
	}
}
