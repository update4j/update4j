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

import java.nio.file.Path;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class UpdateContext {

	private Configuration configuration;

	private List<Library> requiresUpdate;
	private List<Library> updated;
	private Path tempDir;

	private PublicKey key;

	UpdateContext(Configuration config, List<Library> requiresUpdate, List<Library> updated, Path tempDir,
					PublicKey key) {
		configuration = Objects.requireNonNull(config);

		this.requiresUpdate = Collections.unmodifiableList(requiresUpdate);
		this.updated = Collections.unmodifiableList(updated);
		this.tempDir = tempDir;
		this.key = key;
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

	public PublicKey getPublicKey() {
		return key;
	}

	/**
	 * @deprecated In favor of {@link #getPublicKey()}. Calling this method always
	 *             returns {@code null}.
	 */
	@Deprecated(forRemoval = true)
	public Certificate getCertificate() {
		return null;
	}
}
