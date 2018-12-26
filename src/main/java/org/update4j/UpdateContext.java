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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.update4j.service.UpdateHandler;

/**
 * A class that contain details of the update.
 * 
 * @author Mordechai Meisels
 *
 */
public class UpdateContext {

	private Configuration configuration;

	private List<FileMetadata> requiresUpdate;
	private List<FileMetadata> updated;
	private Path tempDir;

	private PublicKey key;

	UpdateContext(Configuration config, List<FileMetadata> requiresUpdate, List<FileMetadata> updated, Path tempDir,
					PublicKey key) {
		configuration = Objects.requireNonNull(config);

		this.requiresUpdate = Collections.unmodifiableList(requiresUpdate);
		this.updated = Collections.unmodifiableList(updated);
		this.tempDir = tempDir;
		this.key = key;
	}

	/**
	 * Returns the configuration used for this update.
	 * 
	 * @return The configuration used for this update.
	 */
	public Configuration getConfiguration() {
		return configuration;
	}

	/**
	 * Returns an unmodifiable list of files that requires an update. This list is
	 * updated live together with calls to
	 * {@link UpdateHandler#doneCheckUpdateFile(FileMetadata, boolean)}.The list
	 * gets updated just before the aforementioned method is called.
	 * 
	 * @return An unmodifiable list of files currently known to require an update.
	 */
	public List<FileMetadata> getRequiresUpdate() {
		return requiresUpdate;
	}

	/**
	 * Returns an unmodifiable list of files that were successfully downloaded. This
	 * list is updated live together with calls to
	 * {@link UpdateHandler#doneDownloadFile(FileMetadata, Path)}. The list gets
	 * updated just before the aforementioned method is called.
	 * 
	 * <p>
	 * The files in this list are not actually in their final location expressed in
	 * {@link FileMetadata#getPath()}, instead they reside in the same directory
	 * with a temporary filename that is passed as the second argument of
	 * {@link UpdateHandler#doneDownloadFile(FileMetadata, Path)}. The file goes
	 * into the final location once {@link UpdateHandler#doneDownloads()} is called.
	 * 
	 * @return An unmodifiable list of file currently updated.
	 */
	public List<FileMetadata> getUpdated() {
		return updated;
	}

	/**
	 * Returns the temporary location of the update if
	 * {@link Configuration#updateTemp(Path)} or other any overload was used, or
	 * {@code null} otherwise.
	 * 
	 * @return The temporary location of the update if
	 *         {@link Configuration#updateTemp(Path)} was used, or {@code null}
	 *         otherwise.
	 */
	public Path getTempDirectory() {
		return tempDir;
	}

	/**
	 * Returns the public key used to validate the update if
	 * {@link Configuration#update(PublicKey)} or any other overload was used, or
	 * {@code null} otherwise.
	 * 
	 * @return The public key used to validate the update if
	 *         {@link Configuration#update(PublicKey)} was used, or {@code null}
	 *         otherwise.
	 */
	public PublicKey getPublicKey() {
		return key;
	}
}
