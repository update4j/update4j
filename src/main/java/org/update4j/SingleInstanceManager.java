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
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.update4j.util.FileUtils;

/**
 * A convenience class to force only a single instance of the code proceeding
 * {@link #execute()} until JVM shutdown. It also allows for newer instances to
 * send a list of strings to the original single instance.
 * 
 * @author Mordechai Meisels
 *
 */
public class SingleInstanceManager {

	private SingleInstanceManager() {
	}

	/**
	 * A call to this method will ensure that everything after that call will not be
	 * run by more than one instance of this application. The first will pass, but
	 * all others will shut down.
	 * 
	 * <p>
	 * The lock files (special files that this method uses to know that there is a
	 * running instance) will be placed in the current directory. It is highly
	 * discouraged to use the current directory, as one may start the application
	 * from a different directory and circumvent the single instance mechanism. Use
	 * {@link #execute(Path)} instead, for applications that the user may move
	 * around.
	 * 
	 * <p>
	 * You must never call this more than once in the whole application.
	 * 
	 * @throws OverlappingFileLockException
	 *             If this method is called more than once on the same JVM.
	 */
	public static void execute() {
		execute(null);
	}

	/**
	 * A call to this method will ensure that everything after that call will not be
	 * run by more than one instance of this application. The first will pass, but
	 * all others will shut down.
	 * 
	 * <p>
	 * You can specify the location where to place the lock files (special files
	 * that this method uses to know that there is a running instance). It is highly
	 * discouraged to use the current directory, as one may start the application
	 * from a different directory and circumvent the single instance mechanism.
	 * 
	 * <p>
	 * You must never call this more than once in the whole application.
	 * 
	 * @param lockFileDir
	 *            The location where to place the lock files. If {@code null}, it
	 *            will use the current directory.
	 * 
	 * @throws OverlappingFileLockException
	 *             If this method is called more than once on the same JVM.
	 */
	public static void execute(Path lockFileDir) {
		execute(null, null, lockFileDir);
	}

	/**
	 * A call to this method will ensure that everything after that call will not be
	 * run by more than one instance of this application. The first will pass, but
	 * all others will shut down.
	 * 
	 * <p>
	 * The first instance will receive the {@code args} list of strings of the new
	 * instance in the {@code onNewInstance} consumer (called in special instance
	 * message dispatching thread) whenever a new instance is created and
	 * successfully shut down.
	 * 
	 * <p>
	 * The lock files (special files that this method uses to know that there is a
	 * running instance) will be placed in the current directory. It is highly
	 * discouraged to use the current directory, as one may start the application
	 * from a different directory and circumvent the single instance mechanism. Use
	 * {@link #execute(List, Consumer, Path)} instead, for applications that the
	 * user may move around.
	 * 
	 * <p>
	 * You must never call this more than once in the whole application.
	 * 
	 * 
	 * @param args
	 *            The list of strings to pass to the single instance, if this is a
	 *            subsequent instance. {@code null} will be passed as an empty list.
	 * 
	 * @param onNewInstance
	 *            The receiver consumer of the passed list of strings, if this is
	 *            the initial instance. May be {@code null}.
	 * 
	 * @throws OverlappingFileLockException
	 *             If this method is called more than once on the same JVM.
	 */
	public static void execute(List<String> args, Consumer<? super List<String>> onNewInstance) {
		execute(args, onNewInstance, null);
	}

	/**
	 * A call to this method will ensure that everything after that call will not be
	 * run by more than one instance of this application. The first will pass, but
	 * all others will shut down.
	 * 
	 * <p>
	 * The first instance will receive the {@code args} list of strings of the new
	 * instance in the {@code onNewInstance} consumer (called in special instance
	 * message dispatching thread) whenever a new instance is created and
	 * successfully shut down.
	 * 
	 * <p>
	 * You can specify the location where to place the lock files (special files
	 * that this method uses to know that there is a running instance). It is highly
	 * discouraged to use the current directory, as one may start the application
	 * from a different directory and circumvent the single instance mechanism.
	 * 
	 * <p>
	 * You must never call this more than once in the whole application.
	 * 
	 * 
	 * @param args
	 *            The list of strings to pass to the single instance, if this is a
	 *            subsequent instance. {@code null} will be passed as an empty list.
	 * 
	 * @param onNewInstance
	 *            The receiver consumer of the passed list of strings, if this is
	 *            the initial instance. May be {@code null}.
	 * @param lockFileDir
	 *            The location where to place the lock files. If {@code null}, it
	 *            will use the current directory.
	 * 
	 * @throws OverlappingFileLockException
	 *             If this method is called more than once on the same JVM.
	 */
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
				FileUtils.windowsHidden(lockFile, true);

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

				FileUtils.windowsHidden(portFile, false);

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
				}, "Instance Message Dispatcher");

				listen.setDaemon(true);
				listen.start();
			} else {

				try (BufferedReader in = Files.newBufferedReader(portFile)) {
					randomAccess.close();

					int port = Integer.parseInt(in.readLine());

					try (Socket signal = new Socket("localhost", port)) {
						// set timeout in case of a messed up network interface
						signal.setSoTimeout(1000);

						try (BufferedWriter out = new BufferedWriter(
								new OutputStreamWriter(signal.getOutputStream()))) {

							for (String s : args) {
								out.write(s + "\n");
							}
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
