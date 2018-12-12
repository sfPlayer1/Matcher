package matcher.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class Config {
	public static void init() {
		Preferences prefs = Preferences.userRoot(); // in ~/.java/.userPrefs

		try {
			if (prefs.nodeExists(userPrefFolder)) {
				prefs = prefs.node(userPrefFolder);

				if (prefs.nodeExists(lastProjectSetupKey)) setProjectConfig(new ProjectConfig(prefs.node(lastProjectSetupKey)));
				setInputDirs(loadList(prefs, lastInputDirsKey, Config::deserializePath));
				setVerifyInputFiles(prefs.getBoolean(lastVerifyInputFilesKey, true));
				setUidConfig(new UidConfig(prefs));
			}
		} catch (BackingStoreException e) { }
	}

	private Config() { }

	public static ProjectConfig getProjectConfig() {
		return projectConfig;
	}

	public static boolean getVerifyInputFiles() {
		return verifyInputFiles;
	}

	public static List<Path> getInputDirs() {
		return inputDirs;
	}

	public static UidConfig getUidConfig() {
		return uidConfig;
	}

	public static boolean setProjectConfig(ProjectConfig config) {
		if (!config.isValid()) return false;

		projectConfig = config;

		return true;
	}

	public static void setInputDirs(List<Path> dirs) {
		inputDirs.clear();
		inputDirs.addAll(dirs);
	}

	public static void setVerifyInputFiles(boolean value) {
		verifyInputFiles = value;
	}

	public static boolean setUidConfig(UidConfig config) {
		if (!config.isValid()) return false;

		uidConfig = config;

		return true;
	}

	public static void saveAsLast() {
		Preferences root = Preferences.userRoot().node(userPrefFolder);

		try {
			if (projectConfig.isValid()) projectConfig.save(root.node(lastProjectSetupKey));
			saveList(root.node(lastInputDirsKey), inputDirs);
			root.putBoolean(lastVerifyInputFilesKey, verifyInputFiles);
			uidConfig.save(root);

			root.flush();
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

	static <T> List<T> loadList(Preferences parent, String key, Function<String, T> deserializer) throws BackingStoreException {
		if (!parent.nodeExists(key)) return Collections.emptyList();

		parent = parent.node(key);
		List<T> ret = new ArrayList<>();
		String value;

		for (int i = 0; (value = parent.get(Integer.toString(i), null)) != null; i++) {
			ret.add(deserializer.apply(value));
		}

		return ret;
	}

	static void saveList(Preferences parent, List<?> list) throws BackingStoreException {
		parent.clear();

		for (int i = 0; i < list.size(); i++) {
			parent.put(Integer.toString(i), list.get(i).toString());
		}
	}

	static Path deserializePath(String path) {
		return Paths.get(path);
	}

	private static final String userPrefFolder = "player-obf-matcher";
	private static final String lastProjectSetupKey = "last-project-setup";
	private static final String lastInputDirsKey = "last-input-dirs";
	private static final String lastVerifyInputFilesKey = "last-verify-input-files";

	private static ProjectConfig projectConfig = new ProjectConfig();
	private static final List<Path> inputDirs = new ArrayList<>();
	private static boolean verifyInputFiles = true;
	private static UidConfig uidConfig = new UidConfig();
}