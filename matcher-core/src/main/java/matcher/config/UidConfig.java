package matcher.config;

import java.net.InetSocketAddress;
import java.util.prefs.Preferences;

public class UidConfig {
	public UidConfig() {
		this("", 0, "", "", "", "", "");
	}

	public UidConfig(Preferences prefs) {
		this(prefs.get("uidHost", ""),
				prefs.getInt("uidPort", 0),
				prefs.get("uidUser", ""),
				prefs.get("uidPassword", ""),
				prefs.get("uidProject", ""),
				prefs.get("uidVersionA", ""),
				prefs.get("uidVersionB", ""));
	}

	public UidConfig(String host, int port, String user, String password, String project, String versionA, String versionB) {
		this.host = host;
		this.port = port;
		this.user = user;
		this.password = password;
		this.project = project;
		this.versionA = versionA;
		this.versionB = versionB;
	}

	public InetSocketAddress getAddress() {
		if (host.isEmpty() || port <= 0) return null;

		return new InetSocketAddress(host, port);
	}

	public String getUser() {
		return user;
	}

	public String getPassword() {
		return password;
	}

	public String getToken() {
		if (user == null || password == null) return null;

		return user+":"+password;
	}

	public String getProject() {
		return project;
	}

	public String getVersionA() {
		return versionA;
	}

	public String getVersionB() {
		return versionB;
	}

	public boolean isValid() {
		return !host.isEmpty()
				&& port > 0 && port <= 0xffff
				&& !user.isEmpty()
				&& !password.isEmpty()
				&& !project.isEmpty()
				&& !versionA.isEmpty()
				&& !versionB.isEmpty();
	}

	void save(Preferences prefs) {
		if (!isValid()) return;

		if (host != null) prefs.put("uidHost", host);
		if (port != 0) prefs.putInt("uidPort", port);
		if (user != null) prefs.put("uidUser", user);
		if (password != null) prefs.put("uidPassword", password);
		if (project != null) prefs.put("uidProject", project);
		if (versionA != null) prefs.put("uidVersionA", versionA);
		if (versionB != null) prefs.put("uidVersionB", versionB);
	}

	private final String host;
	private final int port;
	private final String user;
	private final String password;
	private final String project;
	private final String versionA;
	private final String versionB;
}
