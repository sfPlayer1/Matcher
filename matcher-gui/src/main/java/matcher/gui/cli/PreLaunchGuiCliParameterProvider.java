package matcher.gui.cli;

import com.beust.jcommander.Parameter;

import matcher.Matcher;
import matcher.cli.provider.CliParameterProvider;
import matcher.config.Config;
import matcher.config.Theme;

public class PreLaunchGuiCliParameterProvider implements CliParameterProvider {
	@Parameter(names = {BuiltinGuiCliParameters.THEME})
	String themeId;

	@Override
	public Object getDataHolder() {
		return this;
	}

	@Override
	public void processArgs() {
		if (themeId != null) {
			Theme theme = Theme.getById(themeId);

			if (theme == null) {
				Matcher.LOGGER.error("Startup arg '--theme' couldn't be applied, as there exists no theme with ID " + themeId + "!");
			} else {
				Config.setTheme(theme);
			}
		}
	}
}
