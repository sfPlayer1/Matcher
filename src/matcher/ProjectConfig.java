package matcher;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

class ProjectConfig {
	public static ProjectConfig getLast() {
		Preferences prefs = Preferences.userRoot();

		try {
			if (prefs.nodeExists(userPrefFolder)
					&& (prefs = prefs.node(userPrefFolder)).nodeExists(userPrefKey)
					&& (prefs = prefs.node(userPrefKey)).nodeExists(pathsAKey) && prefs.nodeExists(pathsBKey) && prefs.nodeExists(pathsSharedKey)) {
				Function<String, Path> deserializer = str -> Paths.get(str);

				List<Path> pathsA = loadList(prefs.node(pathsAKey), deserializer);
				List<Path> pathsB = loadList(prefs.node(pathsBKey), deserializer);
				List<Path> pathsShared = loadList(prefs.node(pathsSharedKey), deserializer);

				return new ProjectConfig(pathsA, pathsB, pathsShared);
			}
		} catch (BackingStoreException e) { }

		return new ProjectConfig();
	}

	public ProjectConfig() {
		this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
	}

	ProjectConfig(List<Path> pathsA, List<Path> pathsB, List<Path> sharedClassPath) {
		this.pathsA = pathsA;
		this.pathsB = pathsB;
		this.sharedClassPath = sharedClassPath;
	}

	public boolean isValid() {
		return !pathsA.isEmpty()
				&& !pathsB.isEmpty()
				&& Collections.disjoint(pathsA, pathsB)
				&& Collections.disjoint(pathsA, sharedClassPath)
				&& Collections.disjoint(pathsB, sharedClassPath);
	}

	public void saveAsLast() {
		if (!isValid()) return;

		Preferences root = Preferences.userRoot().node(userPrefFolder).node(userPrefKey);

		saveList(root.node(pathsAKey), pathsA);
		saveList(root.node(pathsBKey), pathsB);
		saveList(root.node(pathsSharedKey), sharedClassPath);
	}

	private static <T> List<T> loadList(Preferences parent, Function<String, T> deserializer) {
		List<T> ret = new ArrayList<>();
		String value;

		for (int i = 0; (value = parent.get(Integer.toString(i), null)) != null; i++) {
			ret.add(deserializer.apply(value));
		}

		return ret;
	}

	private static void saveList(Preferences parent, List<?> list) {
		for (int i = 0; i < list.size(); i++) {
			parent.put(Integer.toString(i), list.get(i).toString());
		}
	}

	private static final String userPrefFolder = "player-obf-matcher";
	private static final String userPrefKey = "last-project-setup";
	private static final String pathsAKey = "paths-a";
	private static final String pathsBKey = "paths-b";
	private static final String pathsSharedKey = "paths-shared";

	final List<Path> pathsA;
	final List<Path> pathsB;
	final List<Path> sharedClassPath;
}