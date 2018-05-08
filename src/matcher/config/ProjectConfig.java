package matcher.config;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class ProjectConfig {
	public ProjectConfig() {
		this(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false);
	}

	ProjectConfig(Preferences prefs) throws BackingStoreException {
		this(Config.loadList(prefs, pathsAKey, Config::deserializePath),
				Config.loadList(prefs, pathsBKey, Config::deserializePath),
				Config.loadList(prefs, classPathAKey, Config::deserializePath),
				Config.loadList(prefs, classPathBKey, Config::deserializePath),
				Config.loadList(prefs, pathsSharedKey, Config::deserializePath),
				prefs.getBoolean(inputsBeforeClassPathKey, false));
	}

	public ProjectConfig(List<Path> pathsA, List<Path> pathsB, List<Path> classPathA, List<Path> classPathB, List<Path> sharedClassPath, boolean inputsBeforeClassPath) {
		this.pathsA = pathsA;
		this.pathsB = pathsB;
		this.classPathA = classPathA;
		this.classPathB = classPathB;
		this.sharedClassPath = sharedClassPath;
		this.inputsBeforeClassPath = inputsBeforeClassPath;
	}

	public List<Path> getPathsA() {
		return pathsA;
	}

	public List<Path> getPathsB() {
		return pathsB;
	}

	public List<Path> getClassPathA() {
		return classPathA;
	}

	public List<Path> getClassPathB() {
		return classPathB;
	}

	public List<Path> getSharedClassPath() {
		return sharedClassPath;
	}

	public boolean hasInputsBeforeClassPath() {
		return inputsBeforeClassPath;
	}

	public boolean isValid() {
		return !pathsA.isEmpty()
				&& !pathsB.isEmpty()
				&& Collections.disjoint(pathsA, pathsB)
				&& Collections.disjoint(pathsA, sharedClassPath)
				&& Collections.disjoint(pathsB, sharedClassPath)
				&& Collections.disjoint(classPathA, classPathB)
				&& Collections.disjoint(classPathA, pathsA)
				&& Collections.disjoint(classPathB, pathsA)
				&& Collections.disjoint(classPathA, pathsB)
				&& Collections.disjoint(classPathB, pathsB)
				&& Collections.disjoint(classPathA, sharedClassPath)
				&& Collections.disjoint(classPathB, sharedClassPath);
	}

	void save(Preferences prefs) throws BackingStoreException {
		if (!isValid()) return;

		Config.saveList(prefs.node(pathsAKey), pathsA);
		Config.saveList(prefs.node(pathsBKey), pathsB);
		Config.saveList(prefs.node(classPathAKey), classPathA);
		Config.saveList(prefs.node(classPathBKey), classPathB);
		Config.saveList(prefs.node(pathsSharedKey), sharedClassPath);
		prefs.putBoolean(inputsBeforeClassPathKey, inputsBeforeClassPath);
	}

	private static final String pathsAKey = "paths-a";
	private static final String pathsBKey = "paths-b";
	private static final String classPathAKey = "class-path-a";
	private static final String classPathBKey = "class-path-b";
	private static final String pathsSharedKey = "paths-shared";
	private static final String inputsBeforeClassPathKey = "inputs-before-classpath";

	private final List<Path> pathsA;
	private final List<Path> pathsB;
	private final List<Path> classPathA;
	private final List<Path> classPathB;
	private final List<Path> sharedClassPath;
	private final boolean inputsBeforeClassPath;
}
