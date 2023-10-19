package matcher.model.config;

import java.net.URL;

public enum Theme {
	VS_CODE_LIGHT("vs-code-light", "VS Code Light", false, ColorInterpolationMode.HSB),
	ECLIPSE_LIGHT("eclipse-light", "Eclipse Light", false, ColorInterpolationMode.HSB),
	ONE_DARK("one-dark", "One Dark", true, ColorInterpolationMode.HSB),
	DARCULA("darcula", "Darcula", true, ColorInterpolationMode.HSB);

	public static Theme getDefault() {
		return VS_CODE_LIGHT;
	}

	public static Theme getById(String id) {
		for (Theme theme : values()) {
			if (theme.getId().equals(id)) return theme;
		}

		return null;
	}

	Theme(String id, String name, boolean dark, ColorInterpolationMode diffColorInterpolationMode) {
		this.id = id;
		this.name = name;
		this.dark = dark;
		this.diffColorInterpolationMode = diffColorInterpolationMode;
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

	public ColorInterpolationMode getDiffColorInterpolationMode() {
		return diffColorInterpolationMode;
	}

	public URL getUrl() {
		return getClass().getResource("/ui/styles/" + id + ".css");
	}

	private final String id;
	private final String name;
	private final boolean dark;
	private final ColorInterpolationMode diffColorInterpolationMode;

	public enum ColorInterpolationMode {
		/**
		 * For dull/muddy/greyish interpolated colors.
		 */
		RGB,

		/**
		 * For vibrant interpolated colors.
		 */
		HSB
	}
}
