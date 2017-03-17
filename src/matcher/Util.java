package matcher;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

public class Util {
	public static <T> Set<T> newIdentityHashSet() {
		return Collections.newSetFromMap(new IdentityHashMap<>());//new IdentityHashSet<>();
	}

	public static <T> Set<T> newIdentityHashSet(Collection<? extends T> c) {
		Set<T> ret = Collections.newSetFromMap(new IdentityHashMap<>(c.size()));
		ret.addAll(c);

		return ret;//new IdentityHashSet<>(c);
	}

	public static <T> Set<T> copySet(Set<T> set) {
		if (set instanceof HashSet) {
			return new HashSet<>(set);
		} else {
			return newIdentityHashSet(set);
		}
	}

	public static FileSystem iterateJar(Path archive, boolean autoClose, Consumer<Path> handler) {
		FileSystem fs = null;

		try {
			fs = FileSystems.newFileSystem(new URI("jar:"+archive.toUri().toString()), Collections.emptyMap());

			Files.walkFileTree(fs.getPath("/"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.toString().endsWith(".class")) {
						handler.accept(file);
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			closeSilently(fs);
			throw new UncheckedIOException(e);
		} catch (URISyntaxException e) {
			closeSilently(fs);
			throw new RuntimeException(e);
		} catch (Throwable t) {
			closeSilently(fs);
			throw t;
		}

		if (autoClose) closeSilently(fs);

		return fs;
	}

	public static void closeSilently(Closeable c) {
		if (c == null) return;

		try {
			c.close();
		} catch (IOException e) { }
	}
}
