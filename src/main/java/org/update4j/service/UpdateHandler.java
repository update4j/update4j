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

import org.update4j.FileMetadata;
import org.update4j.UpdateContext;

public interface UpdateHandler extends Service {

	void init(UpdateContext context) throws Throwable;
	
	void startCheckUpdates() throws Throwable;
	
	void startCheckUpdateLibrary(FileMetadata lib) throws Throwable;
	
	void doneCheckUpdateLibrary(FileMetadata lib, boolean requires) throws Throwable;
	
	// Based on bytes, not file count
	void updateCheckUpdatesProgress(float frac) throws Throwable;
	
	void doneCheckUpdates() throws Throwable;

	void startDownloads() throws Throwable;
	
	void startDownloadLibrary(FileMetadata lib) throws Throwable;
	
	void updateDownloadLibraryProgress(FileMetadata lib, float frac) throws Throwable;
	
	void updateDownloadProgress(float frac) throws Throwable;
	
	void verifyingLibrary(FileMetadata lib) throws Throwable;
	
	void doneDownloadLibrary(FileMetadata lib) throws Throwable;
	
	void doneDownloads() throws Throwable;

	void failed(Throwable t);

	void succeeded();
	
	void stop();
}
