package matcher.config;

public enum Theme {
	VS_CODE_LIGHT("vs-code-light", "VS Code Light", false),
	ECLIPSE_LIGHT("eclipse-light", "Eclipse Light", false),
	ONE_DARK("one-dark", "One Dark", true),
	DARCULA("darcula", "Darcula", true);

	public static Theme getDefault() {
		return VS_CODE_LIGHT;
	}

	public static Theme getById(String id) {
		for (Theme theme : values()) {
			if (theme.getId().equals(id)) return theme;
		}

		return null;
	}

	Theme(String id, String name, boolean dark) {
		this.id = id;
		this.name = name;
		this.dark = dark;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public boolean isDark() {
		return dark;
	}

	private final String id;
	private final String name;
	private final boolean dark;
}
