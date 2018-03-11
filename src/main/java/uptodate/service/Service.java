package uptodate.service;

import java.util.List;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.stream.Collectors;

public interface Service {

	long version();
	
	public static <T extends Service> T loadService(Class<T> type, String override) {
		ServiceLoader<T> loader = ServiceLoader.load(type);
		List<Provider<T>> providers = loader.stream().collect(Collectors.toList());

		if (providers.isEmpty()) {
			throw new IllegalStateException("No provider found for " + type.getCanonicalName());
		}

		if (override != null) {
			for (Provider<T> p : providers) {
				if (p.type().getCanonicalName().equals(override))
					return p.get();
			}

			System.err.print(override + " not found between providers for " + type.getCanonicalName() + ", ");
		}

		List<T> values = providers.stream().map(Provider::get).collect(Collectors.toList());

		long maxVersion = Long.MIN_VALUE;
		T maxValue = null;
		for (T t : values) {
			long version = t.version();
			if (maxVersion <= version) {
				maxVersion = version;
				maxValue = t;
			}
		}

		if (override != null) {
			System.err.println("using " + maxValue.getClass().getCanonicalName() + " instead.");
		}

		return maxValue;
	}

	public static <T extends Service> T loadService(Class<T> type) {
		return loadService(type, null);
	}

}
