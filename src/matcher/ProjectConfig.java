package matcher;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class ProjectConfig {
	public static ProjectConfig getLast() {
		Preferences prefs = Preferences.userRoot();

		try {
			if (prefs.nodeExists(userPrefFolder)
					&& (prefs = prefs.node(userPrefFolder)).nodeExists(userPrefKey)
					&& (prefs = prefs.node(userPrefKey)).nodeExists(pathsAKey) && prefs.nodeExists(pathsBKey) && prefs.nodeExists(pathsSharedKey)) {
				Function<String, Path> deserializer = str -> Paths.get(str);

				List<Path> pathsA = loadList(prefs.node(pathsAKey), deserializer);
				List<Path> pathsB = loadList(prefs.node(pathsBKey), deserializer);
				List<Path> classPathA = loadList(prefs.node(classPathAKey), deserializer);
				List<Path> classPathB = loadList(prefs.node(classPathBKey), deserializer);
				List<Path> pathsShared = loadList(prefs.node(pathsSharedKey), deserializer);
				boolean inputsBeforeClassPath = prefs.getBoolean(inputsBeforeClassPathKey, false);

				ProjectConfig ret = new ProjectConfig(pathsA, pathsB, classPathA, classPathB, pathsShared, inputsBeforeClassPath);

				ret.setUidSettings(prefs.get("uidHost", null),
						prefs.getInt("uidPort", 0),
						prefs.get("uidUser", null),
						prefs.get("uidPassword", null),
						prefs.get("uidProject", null),
						prefs.get("uidVersionA", null),
						prefs.get("uidVersionB", null));

				return ret;
			}
		} catch (BackingStoreException e) { }

		return new ProjectConfig();
	}

	public ProjectConfig() {
		this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), false);
	}

	ProjectConfig(List<Path> pathsA, List<Path> pathsB, List<Path> classPathA, List<Path> classPathB, List<Path> sharedClassPath, boolean inputsBeforeClassPath) {
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

	public void setUidSettings(String host, int port, String user, String password, String project, String versionA, String versionB) {
		this.uidHost = host;
		this.uidPort = port;
		this.uidUser = user;
		this.uidPassword = password;
		this.uidProject = project;
		this.uidVersionA = versionA;
		this.uidVersionB = versionB;
	}

	public InetSocketAddress getUidAddress() {
		if (uidHost == null || uidPort <= 0) return null;

		return new InetSocketAddress(uidHost, uidPort);
	}

	public String getUidUser() {
		return uidUser;
	}

	public String getUidPassword() {
		return uidPassword;
	}

	public String getUidToken() {
		if (uidUser == null || uidPassword == null) return null;

		return uidUser+":"+uidPassword;
	}

	public String getUidProject() {
		return uidProject;
	}

	public String getUidVersionA() {
		return uidVersionA;
	}

	public String getUidVersionB() {
		return uidVersionB;
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

	public void saveAsLast() {
		if (!isValid()) return;

		Preferences root = Preferences.userRoot().node(userPrefFolder).node(userPrefKey);

		try {
			saveList(root.node(pathsAKey), pathsA);
			saveList(root.node(pathsBKey), pathsB);
			saveList(root.node(classPathAKey), classPathA);
			saveList(root.node(classPathBKey), classPathB);
			saveList(root.node(pathsSharedKey), sharedClassPath);
			root.putBoolean(inputsBeforeClassPathKey, inputsBeforeClassPath);
			if (uidHost != null) root.put("uidHost", uidHost);
			if (uidPort != 0) root.putInt("uidPort", uidPort);
			if (uidUser != null) root.put("uidUser", uidUser);
			if (uidPassword != null) root.put("uidPassword", uidPassword);
			if (uidProject != null) root.put("uidProject", uidProject);
			if (uidVersionA != null) root.put("uidVersionA", uidVersionA);
			if (uidVersionB != null) root.put("uidVersionB", uidVersionB);

			root.flush();
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

	private static <T> List<T> loadList(Preferences parent, Function<String, T> deserializer) {
		List<T> ret = new ArrayList<>();
		String value;

		for (int i = 0; (value = parent.get(Integer.toString(i), null)) != null; i++) {
			ret.add(deserializer.apply(value));
		}

		return ret;
	}

	private static void saveList(Preferences parent, List<?> list) throws BackingStoreException {
		parent.clear();

		for (int i = 0; i < list.size(); i++) {
			parent.put(Integer.toString(i), list.get(i).toString());
		}
	}

	private static final String userPrefFolder = "player-obf-matcher";
	private static final String userPrefKey = "last-project-setup";
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
	private String uidHost;
	private int uidPort;
	private String uidUser;
	private String uidPassword;
	private String uidProject;
	private String uidVersionA;
	private String uidVersionB;
}