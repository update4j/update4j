package uptodate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import uptodate.util.FileUtils;

public class SingleInstanceManager {

	private SingleInstanceManager() {
	}

	public static void execute() {
		execute(null, null);
	}

	public static void execute(List<String> args, Consumer<? super List<String>> onNewInstance) {
		execute(args, onNewInstance, null);
	}

	public static void execute(List<String> args, Consumer<? super List<String>> onNewInstance, Path lockFileDir) {

		if (args == null) {
			args = List.of();
		}

		if (lockFileDir == null) {
			lockFileDir = Paths.get(System.getProperty("user.dir"));
		}

		Path lockFile = lockFileDir.resolve(".lock");
		Path portFile = lockFileDir.resolve(".port");

		try {
			RandomAccessFile randomAccess = new RandomAccessFile(lockFile.toFile(), "rw");
			FileLock lock = randomAccess.getChannel().tryLock();

			if (lock != null) {
				FileUtils.windowsHide(lockFile);

				ServerSocket server = new ServerSocket(0, 0, InetAddress.getByName(null));

				Runtime.getRuntime().addShutdownHook(new Thread() {
					public void run() {
						try {
							lock.release();
							server.close();
							randomAccess.close();

							Files.delete(lockFile);
							Files.delete(portFile);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				});

				try (BufferedWriter out = Files.newBufferedWriter(portFile, StandardOpenOption.CREATE)) {
					out.write("" + server.getLocalPort());
				}

				FileUtils.windowsHide(portFile);

				Thread listen = new Thread(() -> {
					while (!server.isClosed()) {
						try (Socket socket = server.accept();
										BufferedReader in = new BufferedReader(
														new InputStreamReader(socket.getInputStream()))) {

							List<String> input = in.lines().collect(Collectors.toList());

							if (onNewInstance != null) {
								onNewInstance.accept(input);
							}
						} catch (SocketException e) {
							if (!server.isClosed()) // otherwise it's normal on shutdown
								e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				});

				listen.setDaemon(true);
				listen.start();
			} else {

				try (BufferedReader in = Files.newBufferedReader(portFile)) {
					randomAccess.close();

					int port = Integer.parseInt(in.readLine());

					try (Socket signal = new Socket("localhost", port);
									BufferedWriter out = new BufferedWriter(
													new OutputStreamWriter(signal.getOutputStream()))) {

						for (String s : args) {
							out.write(s + "\n");
						}
					}

				} catch (IOException e1) {
					e1.printStackTrace();
				}

				System.exit(0);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
