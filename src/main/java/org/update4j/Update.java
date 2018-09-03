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

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import org.update4j.util.FileUtils;
import org.update4j.util.Warning;

public class Update {

	public static final Path UPDATE_DATA = Paths.get(".update");

	public static boolean containsUpdate(Path tempDir) {
		return Files.isRegularFile(tempDir.resolve(UPDATE_DATA));
	}

	@SuppressWarnings("unchecked")
	public static boolean finalizeUpdate(Path tempDir) throws IOException {
		if (!containsUpdate(tempDir)) {
			return false;
		}

		Path updateData = tempDir.resolve(UPDATE_DATA);

		Map<Path, Path> files = new HashMap<>();

		try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(updateData))) {
			Map<File, File> map = (Map<File, File>) in.readObject();

			boolean missing = false;
			for (Map.Entry<File, File> e : map.entrySet()) {
				if (Files.isRegularFile(e.getKey().toPath())) {
					files.put(e.getKey().toPath(), e.getValue().toPath());
				} else {
					missing = true;
				}
			}

			if (missing) {
				Files.deleteIfExists(updateData);
				for (Map.Entry<Path, Path> e : files.entrySet()) {
					Files.deleteIfExists(e.getKey());
				}
				if (FileUtils.isEmptyDirectory(tempDir)) {
					Files.deleteIfExists(tempDir);
				}

				throw new IllegalStateException(
								"Files in the update had been tampered and finalize could not be completed.");
			}

		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		}

		if (files.isEmpty())
			return false;

		try {
			for (Map.Entry<Path, Path> e : files.entrySet()) {
				FileUtils.verifyNotLocked(e.getValue());
			}
		} catch (FileSystemException fse) {
			String msg = fse.getMessage();
			if (msg.contains("another process") || msg.contains("lock") || msg.contains("use")) {
				Warning.lockFinalize(fse.getFile());
			}

			throw fse;
		}

		for (Map.Entry<Path, Path> e : files.entrySet()) {
			Files.move(e.getKey(), e.getValue(), StandardCopyOption.REPLACE_EXISTING);
		}

		Files.deleteIfExists(updateData);

		if (FileUtils.isEmptyDirectory(tempDir)) {
			Files.deleteIfExists(tempDir);
		}

		return true;
	}

}
