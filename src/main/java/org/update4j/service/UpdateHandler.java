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
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;

import org.update4j.FileMetadata;
import org.update4j.UpdateContext;

public interface UpdateHandler extends Service {

	void init(UpdateContext context) throws Throwable;
	
	void startCheckUpdates() throws Throwable;
	
	void startCheckUpdateFile(FileMetadata file) throws Throwable;
	
	void doneCheckUpdateFile(FileMetadata file, boolean requires) throws Throwable;
	
	// Based on bytes, not file count
	void updateCheckUpdatesProgress(float frac) throws Throwable;
	
	void doneCheckUpdates() throws Throwable;

	void startDownloads() throws Throwable;
	
	default InputStream connect(FileMetadata file, URL url) throws Throwable {

		URLConnection connection = url.openConnection();

		// Some downloads may fail with HTTP/403, this may solve it
		connection.addRequestProperty("User-Agent", "Mozilla/5.0");
		// Set a connection timeout of 10 seconds
		connection.setConnectTimeout(10 * 1000);
		// Set a read timeout of 10 seconds
		connection.setReadTimeout(10 * 1000);
		
		return connection.getInputStream();
		
	}
	
	void startDownloadFile(FileMetadata file) throws Throwable;
	
	void updateDownloadFileProgress(FileMetadata file, float frac) throws Throwable;
	
	void updateDownloadProgress(float frac) throws Throwable;
	
	void validatingFile(FileMetadata file, Path tempFile) throws Throwable;
	
	void doneDownloadFile(FileMetadata file, Path tempFile) throws Throwable;
	
	void doneDownloads() throws Throwable;

	void failed(Throwable t);

	void succeeded();
	
	void stop();
}
