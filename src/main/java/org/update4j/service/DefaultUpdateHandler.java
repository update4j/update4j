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

import static org.update4j.util.StringUtils.formatSeconds;
import static org.update4j.util.StringUtils.humanReadableByteCount;
import static org.update4j.util.StringUtils.padLeft;
import static org.update4j.util.StringUtils.padRight;
import static org.update4j.util.StringUtils.repeat;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Timer;
import java.util.TimerTask;

import org.update4j.FileMetadata;
import org.update4j.UpdateContext;
import org.update4j.util.FileUtils;
import org.update4j.util.StringUtils;

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
	public void startDownloads() throws Throwable {
		total = context.getRequiresUpdate().size();
		ordinalWidth = String.valueOf(total).length() * 2 + 1;
		initProgress();
	}

	@Override
	public void startDownloadFile(FileMetadata file) throws Throwable {
		index++;
		println(renderFilename(file));
		resetProgress(file.getSize());
	}

	@Override
	public void updateDownloadFileProgress(FileMetadata file, float frac) throws Throwable {
		currentFrac = frac;
	}

	@Override
	public void doneDownloadFile(FileMetadata file, Path tempFile) throws Throwable {
		clear();
	}

	@Override
	public void failed(Throwable t) {
		clearln();
		t.printStackTrace();
	}

	@Override
	public void stop() {
		stopTimer = true;
	}
	
	//------- Progress rendering, highly inspired by https://github.com/ctongfei/progressbar

	private PrintStream out;
	private Timer timer;

	private int totalWidth;
	private int msgWidth;
	private int rateWidth;
	private int percentWidth;
	private int timeWidth;
	private String clear;
	
	private int ordinalWidth;
	private int total;
	private int index;
	
	private long totalBytes;
	private float lastFrac;
	private float currentFrac;
	private long start;
	private boolean stopTimer;
	
	protected void initProgress() {
		out = out();
		totalWidth = consoleWidth();
		msgWidth = "Downloading".length();
		rateWidth = "@ 100.0 kB/s".length();
		percentWidth = "100%".length();
		timeWidth = "0:00:00".length();
		clear = "\r" + repeat(totalWidth, " ") + "\r";

		timer = new Timer("Progress Printer", true);
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				if(stopTimer) {
					timer.cancel();
					return;
				}
				
				print(renderProgress());
				lastFrac = currentFrac;
			}
		}, 0, 1000);
	}
	
	protected void resetProgress(long bytes) {
		currentFrac = 0;
		lastFrac = 0;
		totalBytes = bytes;
		start = System.currentTimeMillis();
	}
	
	protected PrintStream out() {
		return System.out;
	}
	
	protected int consoleWidth() {
		return 80;
	}
	
	private void clear() {
		out.print(clear);
	}

	private void clearln() {
		out.println(clear);
	}

	private void print(String str) {
		out.print("\r");
		out.print(padRight(totalWidth, str));
	}

	private void println(String str) {
		out.print("\r");
		out.println(padRight(totalWidth, str));
	}
	
	protected String renderProgress() {
		StringBuilder sb = new StringBuilder();
		sb.append("Downloading ");

		String humanReadableBytes = humanReadableByteCount(totalBytes);

		sb.append(humanReadableBytes);
		sb.append(" ");
		if(lastFrac == 0 && currentFrac == 0) {
			sb.append(repeat(rateWidth + 1, " "));
		} else {
			sb.append("@ ");
			sb.append(padRight(rateWidth - 2, humanReadableByteCount((long)((currentFrac - lastFrac) * totalBytes)) + "/s"));
			sb.append(" ");
		}
		sb.append(padLeft(percentWidth, ((int) (currentFrac * 100)) + "%"));
		sb.append(" [");

		int progressWidth = totalWidth
				- msgWidth 
				- humanReadableBytes.length()
				- rateWidth
				- percentWidth
				- timeWidth
				- 7; // spaces

		int pieces = (int) ((progressWidth - 2) * currentFrac);
		String line = repeat(pieces, "=");
		if (pieces < progressWidth - 2)
			line += ">";

		sb.append(padRight(progressWidth - 2, line));
		sb.append("]");

		long elapsed = System.currentTimeMillis() - start;
		if (currentFrac > 0) {
			sb.append(" (");
			sb.append(formatSeconds(((long) (elapsed / currentFrac) - elapsed) / 1000));
			sb.append(")");
		}

		return sb.toString();
	}

	protected String renderFilename(FileMetadata file) {
		return StringUtils.padLeft(ordinalWidth, index + "/" + total) + " " + compactName(file.getPath());
	}

	private String compactName(Path name) {
		Path relative = FileUtils.relativize(context.getConfiguration().getBasePath(), name);
		return relative.isAbsolute() ? relative.getFileName().toString() : relative.toString();
	}
}
