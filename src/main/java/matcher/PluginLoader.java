package matcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class PluginLoader {
	public static void run(String[] args) {
		List<Path> pluginPaths = new ArrayList<>();
		pluginPaths.add(Paths.get("plugins"));

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "--additional-plugins":
				while (!args[i+1].startsWith("--")) {
					pluginPaths.add(Path.of(args[++i]));
				}

				break;
			}
		}

		List<URL> urls = new ArrayList<>();

		for (Path path : pluginPaths) {
			try {
				if (Files.isDirectory(path)) {
					Stream<Path> stream = Files.list(path);
					urls.addAll(stream
							.filter(p -> p.getFileName().toString().toLowerCase(Locale.ENGLISH).endsWith(".jar"))
							.map(p -> {
								try {
									return p.toUri().toURL();
								} catch (MalformedURLException e) {
									throw new RuntimeException(e);
								}
							})
							.collect(Collectors.toList()));
					stream.close();
				} else if (path.getFileName().toString().toLowerCase(Locale.ENGLISH).endsWith(".jar")) {
					urls.add(path.toUri().toURL());
				} else {
					System.err.println("Failed to load plugin(s) from " + path);
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]));

		ServiceLoader<Plugin> pluginLoader = ServiceLoader.load(Plugin.class, cl);

		for (Plugin p : pluginLoader) {
			p.init(apiVersion);
		}
	}

	private static final int apiVersion = 0;
}
