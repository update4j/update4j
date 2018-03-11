package uptodate;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uptodate.service.Delegate;
import uptodate.service.Service;

public class UpToDate {

	public static final Path UPDATE_DATA = Paths.get(".update");

	public static void main(String[] args) throws Throwable {
		String overrideClass = null;

		Pattern pattern = Pattern.compile(
						"\\Q--delegate\\E\\s*?=?\\s*?(([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*)");
		for (String s : args) {
			Matcher matcher = pattern.matcher(s);
			if (matcher.find()) {
				overrideClass = matcher.group(1);
				break;
			}
		}

		Delegate delegate = Service.loadService(Delegate.class, overrideClass);
		delegate.main(List.of(args));
	}

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
			map.forEach((k, v) -> {
				if (Files.isRegularFile(k.toPath()))
					files.put(k.toPath(), v.toPath());
			});

		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		}

		if (files.isEmpty())
			return false;

		for (Map.Entry<Path, Path> e : files.entrySet()) {
			Files.move(e.getKey(), e.getValue(), StandardCopyOption.REPLACE_EXISTING);
		}

		Files.deleteIfExists(updateData);

		try (DirectoryStream<Path> dir = Files.newDirectoryStream(tempDir)) {
			if (!dir.iterator().hasNext()) {
				Files.deleteIfExists(tempDir);
			}
		}

		return true;
	}
}