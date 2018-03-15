package org.update4j;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class LaunchContext {

	private ModuleLayer layer;
	private Configuration config;
	private List<String> args;

	LaunchContext(ModuleLayer layer, Configuration config, List<String> args) {
		this.layer = Objects.requireNonNull(layer);
		this.config = Objects.requireNonNull(config);

		this.args = args == null ? List.of() : Collections.unmodifiableList(args);
	}

	public ModuleLayer getModuleLayer() {
		return layer;
	}

	public Configuration getConfiguration() {
		return config;
	}

	public List<String> getArgs() {
		return args;
	}

}
