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

import java.nio.file.Path;

import org.update4j.FileMetadata;
import org.update4j.UpdateContext;
import org.update4j.util.FileUtils;

public class DefaultUpdateHandler implements UpdateHandler {

	@Override
	public long version() {
		return Long.MIN_VALUE;
	}

	private UpdateContext context;

	@Override
	public void init(UpdateContext context) {
		this.context = context;
	}

	@Override
	public void startCheckUpdates() throws Throwable {
	}

	@Override
	public void startCheckUpdateLibrary(FileMetadata lib) throws Throwable {
	}

	@Override
	public void doneCheckUpdateLibrary(FileMetadata lib, boolean requires) throws Throwable {
		System.out.print(compactName(context.getConfiguration().getBasePath(), lib.getPath()));
		if (requires) {
			System.out.println(":  UPDATE");
		} else {
			System.out.println(":  SYNCHRONIZED");
		}
	}

	@Override
	public void updateCheckUpdatesProgress(float frac) throws Throwable {
	}

	@Override
	public void doneCheckUpdates() throws Throwable {
	}

	@Override
	public void startDownloads() throws Throwable {
	}

	@Override
	public void startDownloadLibrary(FileMetadata lib) throws Throwable {
		System.out.print("Downloading: " + compactName(context.getConfiguration().getBasePath(), lib.getPath())
						+ " <" + lib.getUri() + "> ");
	}

	private String percent;

	@Override
	public void updateDownloadLibraryProgress(FileMetadata lib, float frac) throws InterruptedException {
		if (frac == 0) {
			System.out.print("(");
		}
		String percent = ((int) (frac * 100)) + "%)   ";
		percent = percent.substring(0, 5);

		if (!percent.equals(this.percent)) {
			this.percent = percent;
			if (frac != 0)
				System.out.print("\b\b\b\b\b");
			System.out.print(percent);
		}

		if (frac == 1) {
			System.out.println();
		}

	}

	@Override
	public void updateDownloadProgress(float frac) {
	}

	@Override
	public void verifyingLibrary(FileMetadata lib) throws Throwable {
	}

	@Override
	public void doneDownloadLibrary(FileMetadata lib) throws Throwable {
	}

	@Override
	public void doneDownloads() throws Throwable {
	}

	@Override
	public void failed(Throwable t) {
		System.out.println();

		t.printStackTrace();
	}

	@Override
	public void succeeded() {
	}

	@Override
	public void stop() {
	}

	private static String compactName(Path base, Path name) {
		Path relative = FileUtils.relativize(base, name);

		return relative.isAbsolute() ? relative.getFileName().toString() : relative.toString();
	}
}
