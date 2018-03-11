package uptodate.service;

import uptodate.LaunchContext;

public interface LaunchHandler extends Service {

	void start(LaunchContext context) throws Throwable;

	void failed(Throwable t);

	void stop();
}
