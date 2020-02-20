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
import java.util.HashMap;
import java.util.Map;

import org.update4j.util.FileUtils;
import org.update4j.util.Warning;

/**
 * This class contains method to complete an update when
 * {@link Configuration#updateTemp(Path)} was used.
 * 
 * @author Mordechai Meisels
 *
 */
public class Update {

    public static final Path UPDATE_DATA = Paths.get(".update");

    /**
     * Returns whether the given directory has an update ready for finalization.
     * 
     * <p>
     * This method does not guarantee that files were not tempered or that the
     * update can actually be finalized in general.
     * 
     * @param tempDir
     *            The location to check for a temporary update.
     * @return Whether the directory contains an update.
     */
    public static boolean containsUpdate(Path tempDir) {
        return Files.isRegularFile(tempDir.resolve(UPDATE_DATA));
    }

    /**
     * Finalizes an update that resides in the provided directory. This is typically
     * called in the beginning of a bootstrap to complete updates from a previous
     * run.
     * 
     * 
     * @param tempDir
     *            The location to look for the update.
     * @return Whether directory actually contained an update instead of an empty
     *         {@code .update} file.
     * @throws IOException
     *             If the update was tempered or any file system operation during
     *             finalization failed.
     */
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
                FileUtils.verifyAccessible(e.getValue());
            }
        } catch (FileSystemException fse) {
            Warning.lockFinalize(fse);
            throw fse;
        }

        for (Map.Entry<Path, Path> e : files.entrySet()) {
            if (e.getValue().getParent() != null) {
                Files.createDirectories(e.getValue().getParent());
            }

            FileUtils.secureMoveFile(e.getKey(), e.getValue());
        }

        Files.deleteIfExists(updateData);

        if (FileUtils.isEmptyDirectory(tempDir)) {
            Files.deleteIfExists(tempDir);
        }

        return true;
    }

}
