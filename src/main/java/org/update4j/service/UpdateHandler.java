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

import org.update4j.Configuration;
import org.update4j.FileMetadata;
import org.update4j.UpdateContext;
import org.update4j.inject.Injectable;

/**
 * An implementation of this interface can be used to get callbacks on all
 * stages of the update process.
 * 
 * <p>
 * The order of the method invocation are precisely as follows:
 * 
 * <p>
 * <ul>
 * <li>{@link #init(UpdateContext)}</li>
 * <li>{@link #startCheckUpdates()}</li>
 * <li>{@link #updateCheckUpdatesProgress(float)} -- set to
 * {@code 0f}</li>
 * <p>
 * For each file in the config:
 * <ul>
 * <li>{@link #shouldCheckForUpdate(FileMetadata)}</li>
 * <p>
 * If previous call returned {@code true}:
 * <ul>
 * <li>{@link #startCheckUpdateFile(FileMetadata)}</li>
 * <li>{@link #doneCheckUpdateFile(FileMetadata, boolean)}</li>
 * </ul>
 * <li>{@link #updateCheckUpdatesProgress(float)}</li>
 * </ul>
 * <li>{@link #doneCheckUpdates()}</li>
 * <p>
 * If there are any files that need an update:
 * <ul>
 * <li>{@link #startDownloads()}</li>
 * <p>
 * For each file requiring an update:
 * <ul>
 * <li>{@link #startDownloadFile(FileMetadata)}</li>
 * <li>{@link #updateDownloadProgress(float)} -- on the first file
 * only, set to {@code 0f}</li>
 * <li>{@link #updateDownloadFileProgress(FileMetadata, float)} --
 * set to {@code 0f}. You can observe the remote server latency between
 * {@code startDownloadFile()} and this</li>
 * <p>
 * Repeatedly, until file download completes:
 * <ul>
 * <li>{@link #updateDownloadFileProgress(FileMetadata, float)} --
 * updates the fraction of {@code 1f}</li>
 * <li>{@link #updateDownloadProgress(float)}</li>
 * </ul>
 * <li>{@link #validatingFile(FileMetadata, Path)}</li>
 * <li>{@link #doneDownloadFile(FileMetadata, Path)}</li>
 * </ul>
 * <li>{@link #doneDownloads()}</li>
 * </ul>
 * <p>
 * If successfully updated (or no updates were required, successfully):
 * <ul>
 * <li>{@link #succeeded()}</li>
 * </ul>
 * <p>
 * Otherwise, for any exception, even if thrown by the update handler:
 * <ul>
 * <li>{@link #failed(Throwable)}</li>
 * </ul>
 * <li>{@link #stop()}</li>
 * </ul>
 * 
 * <p>
 * For more info how to use services, check out the <a href=
 * "https://github.com/update4j/update4j/wiki/Documentation#dealing-with-providers">GitHub
 * Wiki</a>.
 * 
 * @author Mordechai Meisels
 *
 */
public interface UpdateHandler extends Service {

	/**
	 * Called after injection (see {@link Configuration#update(Injectable)}) but
	 * before anything update related has started. To do any initialization before
	 * injection, do it in the constructor.
	 * 
	 * <p>
	 * You can use the {@link UpdateContext} to get information or the current state
	 * of the update process, any time. Its values are updated along the process to
	 * reflect any change.
	 * 
	 * @param context Check the state of the update with this object.
	 * @throws Throwable Freely throw any exception, it will gracefully terminate
	 *                   the update process and revert any file changes.
	 */
	default void init(UpdateContext context) throws Throwable {
	}

	/**
	 * Called after {@link #init()}, just before starting to check for updates.
	 * 
	 * @throws Throwable Freely throw any exception, it will gracefully terminate
	 *                   the update process and revert any file changes.
	 */
	default void startCheckUpdates() throws Throwable {
	}

	/**
	 * Do your own logic here to decide whether a particular file should be updated.
	 * The default implementation returns {@code true} for every file.
	 * 
	 * <p>
	 * This method is called <em>before</em> checking if the file is outdated.
	 * 
	 * @param file The file to probe if it should be checked for updates.
	 * @throws Throwable Freely throw any exception, it will gracefully terminate
	 *                   the update process and revert any file changes.
	 */
	default boolean shouldCheckForUpdate(FileMetadata file) {
		return true;
	}

	/**
	 * Called before this individual file is checked if it is outdated.
	 * 
	 * @param file The file that will soon be checked if outdated.
	 * @throws Throwable Freely throw any exception, it will gracefully terminate
	 *                   the update process and revert any file changes.
	 */
	default void startCheckUpdateFile(FileMetadata file) throws Throwable {
	}

	/**
	 * Checking if this file requires an update is complete, and
	 * {@link UpdateContext#getRequiresUpdate()} was updated to include this file.
	 * 
	 * @param file     The file that was just checked.
	 * @param requires Whether this file in fact requires an update.
	 * @throws Throwable Freely throw any exception, it will gracefully terminate
	 *                   the update process and revert any file changes.
	 */
	default void doneCheckUpdateFile(FileMetadata file, boolean requires) throws Throwable {
	}

	/**
	 * Updates the 'check update' task progress. Called once for every file in the
	 * config. It will be called the first time with the value {@code 0f}.
	 * 
	 * <p>
	 * The value is based on bytes of all files in the config, not file count.
	 * 
	 * @param frac A value from {@code 0f} to {@code 1f} representing the percent of
	 *             the job done.
	 * @throws Throwable Freely throw any exception, it will gracefully terminate
	 *                   the update process and revert any file changes.
	 */
	default void updateCheckUpdatesProgress(float frac) throws Throwable {
	}

	/**
	 * All files were passed for an update check (or not, if
	 * {@link #shouldCheckForUpdate(FileMetadata)} returned {@code false}) and the
	 * {@link UpdateContext#getRequiresUpdate()} is up-to-date.
	 * 
	 * @throws Throwable Freely throw any exception, it will gracefully terminate
	 *                   the update process and revert any file changes.
	 */
	default void doneCheckUpdates() throws Throwable {
	}

	/**
	 * If there are any files that need an update, this method will get called once.
	 * 
	 * @throws Throwable Freely throw any exception, it will gracefully terminate
	 *                   the update process and revert any file changes.
	 */
	default void startDownloads() throws Throwable {
	}

	/**
	 * Do any logic to obtain an {@link InputStream} for this file. You can do all
	 * kinds of interesting stuff here, as accessing authenticated API, use
	 * different protocols (as old-fashioned sockets if you really prefer) etc.
	 * 
	 * <p>
	 * Do <em>not</em> read anything from the stream, just return a newly opened
	 * stream and let the framework do the rest.
	 * 
	 * <p>
	 * By default it will try to access the file from {@link FileMetadata#getUri()}
	 * assuming it is openly available without any authentication.
	 * 
	 * @param file The file to get an input stream for.
	 * @return The newly opened input stream, unread.
	 * @throws Throwable Freely throw any exception, it will gracefully terminate
	 *                   the update process and revert any file changes.
	 */
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

	/**
	 * Called once for every file that requires an update, just before the
	 * connection is established.
	 * 
	 * @param file The file that will now be downloaded.
	 * @throws Throwable Freely throw any exception, it will gracefully terminate
	 *                   the update process and revert any file changes.
	 */
	default void startDownloadFile(FileMetadata file) throws Throwable {
	}

	/**
	 * Called repeatedly, updating the {@code frac} value to reflect the state of
	 * the download of this individual file. It will be called the first time with
	 * the value {@code 0f}.
	 * 
	 * <p>
	 * You can observe the remote server latency, by comparing the time between
	 * {@link #startDownloadFile(FileMetadata)} and this method with the value of
	 * {@code 0f}.
	 * 
	 * @param file The file currently being downloaded.
	 * @param frac A value from {@code 0f} to {@code 1f} representing the percent of
	 *             the job done.
	 * @throws Throwable Freely throw any exception, it will gracefully terminate
	 *                   the update process and revert any file changes.
	 */
	default void updateDownloadFileProgress(FileMetadata file, float frac) throws Throwable {
	}

	/**
	 * Called repeatedly, updating the {@code frac} value to reflect the overall
	 * state of the download or all files. It will be called the first time with the
	 * value {@code 0f}.
	 * 
	 * 
	 * @param frac A value from {@code 0f} to {@code 1f} representing the percent of
	 *             the job done.
	 * @throws Throwable Freely throw any exception, it will gracefully terminate
	 *                   the update process and revert any file changes.
	 */
	default void updateDownloadProgress(float frac) throws Throwable {
	}

	/**
	 * The file was successfully downloaded and is now about to be passed throw a
	 * series of validations.
	 * 
	 * <p>
	 * You can do your own validations here using the {@code tempFile} to read the
	 * actual file. Throw an exception to fail the download in case of validation
	 * fail.
	 * 
	 * @param file     The file about to be passing through validations.
	 * @param tempFile The actual file, only moved to its final location once all
	 *                 downloads succeed.
	 * @throws Throwable Freely throw any exception, it will gracefully terminate
	 *                   the update process and revert any file changes.
	 */
	default void validatingFile(FileMetadata file, Path tempFile) throws Throwable {
	}

	/**
	 * The file was successfully downloaded to {@code tempFile} and
	 * {@link UpdateContext#getUpdated()} was already updated to reflect this.
	 * 
	 * <p>
	 * The file will only be placed in its final location once all files
	 * successfully download.
	 * 
	 * 
	 * @param file     The file that just completed download
	 * @param tempFile The temporary location of the file, only moved to its final
	 *                 location once all downloads succeed.
	 * @throws Throwable Freely throw any exception, it will gracefully terminate
	 *                   the update process and revert any file changes.
	 */
	default void doneDownloadFile(FileMetadata file, Path tempFile) throws Throwable {
	}

	/**
	 * All downloads were completed and all temporary files were moved to its final
	 * location. This method will only be called if there were actually files that
	 * required updates.
	 * 
	 * <p>
	 * If an exception arises in this method: If the update is an
	 * {@code updateTemp()}, the files will be deleted; otherwise the files will be
	 * untouched and {@link #failed(Throwable)} will be called.
	 * 
	 * @throws Throwable Any exception will be passed to {@link #failed(Throwable)}
	 *                   and the {@code update()} method will return false.
	 */
	default void doneDownloads() throws Throwable {
	}

	/**
	 * Called when the update process failed.
	 * 
	 * <p>
	 * If an exception arises in this method, the exception will bubble up to the {@code update()} method.
	 * 
	 * @param t The exception thrown that failed the update process.
	 */
	default void failed(Throwable t) {
	}

	/**
	 * Called when the update process is complete, even if no files actually required an update.
	 * 
	 * <p>
	 * If an exception arises in this method: If the update is an
	 * {@code updateTemp()}, the files will be deleted; otherwise the files will be
	 * untouched and {@link #failed(Throwable)} will be called.
	 * 
	 */
	default void succeeded() {
	}

	/**
	 * Called just before the {@code update()} method returns, regardless of the error state (unless an exception was thrown in {@code #failed(Throwable)}.
	 * 
	 * <p>
	 * If an exception arises in this method, the exception will bubble up to the {@code update()} method.
	 */
	default void stop() {
	}
}
