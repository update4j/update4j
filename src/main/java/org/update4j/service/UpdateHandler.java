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
package org.update4j.service;

import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Path;

import org.update4j.FileMetadata;
import org.update4j.UpdateContext;

public interface UpdateHandler extends Service {

	default void init(UpdateContext context) throws Throwable {
	}

	default void startCheckUpdates() throws Throwable {
	}

	default boolean shouldCheckForUpdate(FileMetadata file) {
		return true;
	}

	default void startCheckUpdateFile(FileMetadata file) throws Throwable {
	}

	default void doneCheckUpdateFile(FileMetadata file, boolean requires) throws Throwable {
	}

	/**
	 * Updates the 'check update' task progress.
	 * 
	 * <p>
	 * The value is based on bytes of all files in the config, not file count.
	 * 
	 * @param frac
	 * @throws Throwable
	 */
	default void updateCheckUpdatesProgress(float frac) throws Throwable {
	}

	default void doneCheckUpdates() throws Throwable {
	}

	default void startDownloads() throws Throwable {
	}

	default InputStream openDownloadStream(FileMetadata file) throws Throwable {

		URLConnection connection = file.getUri().toURL().openConnection();

		// Some downloads may fail with HTTP/403, this may solve it
		connection.addRequestProperty("User-Agent", "Mozilla/5.0");
		// Set a connection timeout of 10 seconds
		connection.setConnectTimeout(10 * 1000);
		// Set a read timeout of 10 seconds
		connection.setReadTimeout(10 * 1000);

		return connection.getInputStream();

	}

	default void startDownloadFile(FileMetadata file) throws Throwable {
	}

	default void updateDownloadFileProgress(FileMetadata file, float frac) throws Throwable {
	}

	default void updateDownloadProgress(float frac) throws Throwable {
	}

	default void validatingFile(FileMetadata file, Path tempFile) throws Throwable {
	}

	default void doneDownloadFile(FileMetadata file, Path tempFile) throws Throwable {
	}

	default void doneDownloads() throws Throwable {
	}

	default void failed(Throwable t) {
	}

	default void succeeded() {
	}

	default void stop() {
	}
}
