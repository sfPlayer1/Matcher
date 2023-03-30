package matcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class PluginLoader {
	public static void run() {
		Path pluginFolder = Paths.get("plugins");
		URL[] urls = new URL[0];

		if (Files.isDirectory(pluginFolder)) {
			try (Stream<Path> stream = Files.list(pluginFolder)) {
				urls = stream
						.filter(p -> p.getFileName().toString().toLowerCase(Locale.ENGLISH).endsWith(".jar"))
						.map(p -> {
							try {
								return p.toUri().toURL();
							} catch (MalformedURLException e) {
								throw new RuntimeException(e);
							}
						})
						.collect(Collectors.toList()).toArray(urls);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		URLClassLoader cl = new URLClassLoader(urls);

		ServiceLoader<Plugin> pluginLoader = ServiceLoader.load(Plugin.class, cl);

		for (Plugin p : pluginLoader) {
			p.init(apiVersion);
		}
	}

	private static final int apiVersion = 0;
}
