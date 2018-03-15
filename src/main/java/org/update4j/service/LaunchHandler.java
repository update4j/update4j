package org.update4j.service;

import org.update4j.LaunchContext;

public interface LaunchHandler extends Service {

	void start(LaunchContext context) throws Throwable;

	void failed(Throwable t);

	void stop();
}
