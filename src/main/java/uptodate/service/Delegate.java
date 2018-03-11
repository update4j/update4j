package uptodate.service;

import java.util.List;

public interface Delegate extends Service {

	void main(List<String> args) throws Throwable;
}
