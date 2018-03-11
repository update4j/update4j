package uptodate.service;

import uptodate.Library;
import uptodate.UpdateContext;

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
	public void startCheckUpdateLibrary(Library lib) throws Throwable {
	}

	@Override
	public void doneCheckUpdateLibrary(Library lib, boolean requires) throws Throwable {
		System.out.print(context.getConfiguration().getBasePath().relativize(lib.getPath()));
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
	public void startDownloadLibrary(Library lib) throws Throwable {
		System.out.print("Downloading: " + context.getConfiguration().getBasePath().relativize(lib.getPath()) + " <"
						+ lib.getUri() + "> ");
	}

	private String percent;

	@Override
	public void updateDownloadLibraryProgress(Library lib, float frac) throws InterruptedException {
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
	public void verifyingLibrary(Library lib) throws Throwable {
	}

	@Override
	public void doneDownloadLibrary(Library lib) throws Throwable {
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
	public void succedded() {
	}

	@Override
	public void stop() {
	}

}
