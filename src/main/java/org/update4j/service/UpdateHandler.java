package org.update4j.service;

import org.update4j.Library;
import org.update4j.UpdateContext;

public interface UpdateHandler extends Service {

	void init(UpdateContext context) throws Throwable;
	
	void startCheckUpdates() throws Throwable;
	
	void startCheckUpdateLibrary(Library lib) throws Throwable;
	
	void doneCheckUpdateLibrary(Library lib, boolean requires) throws Throwable;
	
	// Based on bytes, not file count
	void updateCheckUpdatesProgress(float frac) throws Throwable;
	
	void doneCheckUpdates() throws Throwable;

	void startDownloads() throws Throwable;
	
	void startDownloadLibrary(Library lib) throws Throwable;
	
	void updateDownloadLibraryProgress(Library lib, float frac) throws Throwable;
	
	void updateDownloadProgress(float frac) throws Throwable;
	
	void verifyingLibrary(Library lib) throws Throwable;
	
	void doneDownloadLibrary(Library lib) throws Throwable;
	
	void doneDownloads() throws Throwable;

	void failed(Throwable t);

	void succedded();
	
	void stop();
}
