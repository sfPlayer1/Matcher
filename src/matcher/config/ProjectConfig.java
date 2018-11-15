package matcher.config;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ProjectConfig {
	public ProjectConfig() {
		this(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false, "", "", "", "");
	}

	ProjectConfig(Preferences prefs) throws BackingStoreException {
		this(Config.loadList(prefs, pathsAKey, Config::deserializePath),
				Config.loadList(prefs, pathsBKey, Config::deserializePath),
				Config.loadList(prefs, classPathAKey, Config::deserializePath),
				Config.loadList(prefs, classPathBKey, Config::deserializePath),
				Config.loadList(prefs, pathsSharedKey, Config::deserializePath),
				prefs.getBoolean(inputsBeforeClassPathKey, false),
				prefs.get(nonObfuscatedClassPatternAKey, ""),
				prefs.get(nonObfuscatedClassPatternBKey, ""),
				prefs.get(nonObfuscatedMemberPatternAKey, ""),
				prefs.get(nonObfuscatedMemberPatternBKey, ""));
	}

	public ProjectConfig(List<Path> pathsA, List<Path> pathsB, List<Path> classPathA, List<Path> classPathB, List<Path> sharedClassPath, boolean inputsBeforeClassPath,
			String nonObfuscatedClassesPatternA, String nonObfuscatedClassesPatternB, String nonObfuscatedMemberPatternA, String nonObfuscatedMemberPatternB) {
		this.pathsA = pathsA;
		this.pathsB = pathsB;
		this.classPathA = classPathA;
		this.classPathB = classPathB;
		this.sharedClassPath = sharedClassPath;
		this.inputsBeforeClassPath = inputsBeforeClassPath;
		this.nonObfuscatedClassPatternA = nonObfuscatedClassesPatternA;
		this.nonObfuscatedClassPatternB = nonObfuscatedClassesPatternB;
		this.nonObfuscatedMemberPatternA = nonObfuscatedMemberPatternA;
		this.nonObfuscatedMemberPatternB = nonObfuscatedMemberPatternB;
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

	public String getNonObfuscatedClassPatternA() {
		return nonObfuscatedClassPatternA;
	}

	public String getNonObfuscatedClassPatternB() {
		return nonObfuscatedClassPatternB;
	}

	public String getNonObfuscatedMemberPatternA() {
		return nonObfuscatedMemberPatternA;
	}

	public String getNonObfuscatedMemberPatternB() {
		return nonObfuscatedMemberPatternB;
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
				&& Collections.disjoint(classPathB, sharedClassPath)
				&& tryCompilePattern(nonObfuscatedClassPatternA)
				&& tryCompilePattern(nonObfuscatedClassPatternB)
				&& tryCompilePattern(nonObfuscatedMemberPatternA)
				&& tryCompilePattern(nonObfuscatedMemberPatternB);
	}

	private static boolean tryCompilePattern(String regex) {
		try {
			Pattern.compile(regex);
			return true;
		} catch (PatternSyntaxException e) {
			return false;
		}
	}

	void save(Preferences prefs) throws BackingStoreException {
		if (!isValid()) return;

		Config.saveList(prefs.node(pathsAKey), pathsA);
		Config.saveList(prefs.node(pathsBKey), pathsB);
		Config.saveList(prefs.node(classPathAKey), classPathA);
		Config.saveList(prefs.node(classPathBKey), classPathB);
		Config.saveList(prefs.node(pathsSharedKey), sharedClassPath);
		prefs.putBoolean(inputsBeforeClassPathKey, inputsBeforeClassPath);
		prefs.put(nonObfuscatedClassPatternAKey, nonObfuscatedClassPatternA);
		prefs.put(nonObfuscatedClassPatternBKey, nonObfuscatedClassPatternB);
		prefs.put(nonObfuscatedMemberPatternAKey, nonObfuscatedMemberPatternA);
		prefs.put(nonObfuscatedMemberPatternBKey, nonObfuscatedMemberPatternB);
	}

	private static final String pathsAKey = "paths-a";
	private static final String pathsBKey = "paths-b";
	private static final String classPathAKey = "class-path-a";
	private static final String classPathBKey = "class-path-b";
	private static final String pathsSharedKey = "paths-shared";
	private static final String inputsBeforeClassPathKey = "inputs-before-classpath";
	private static final String nonObfuscatedClassPatternAKey = "non-obfuscated-class-pattern-a";
	private static final String nonObfuscatedClassPatternBKey = "non-obfuscated-class-pattern-b";
	private static final String nonObfuscatedMemberPatternAKey = "non-obfuscated-member-pattern-a";
	private static final String nonObfuscatedMemberPatternBKey = "non-obfuscated-member-pattern-b";

	private final List<Path> pathsA;
	private final List<Path> pathsB;
	private final List<Path> classPathA;
	private final List<Path> classPathB;
	private final List<Path> sharedClassPath;
	private final boolean inputsBeforeClassPath;
	private final String nonObfuscatedClassPatternA;
	private final String nonObfuscatedClassPatternB;
	private final String nonObfuscatedMemberPatternA;
	private final String nonObfuscatedMemberPatternB;
}
