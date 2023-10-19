package matcher.gui;

import java.util.Collections;
import java.nio.file.Paths;

import javafx.application.Application;

import matcher.PluginLoader;
import matcher.cli.MatcherCli;
import matcher.cli.provider.builtin.AdditionalPluginsCliParameterProvider;
import matcher.model.config.Config;
import matcher.gui.cli.PreLaunchGuiCliParameterProvider;

public class Main {
	public static void main(String[] args) {
		Config.init();
		PluginLoader.run(Collections.singletonList(Paths.get("plugins")));

		handlePreLaunchStartupArgs(args);
		Application.launch(MatcherGui.class, args);
	}

	private static void handlePreLaunchStartupArgs(String[] args) {
		MatcherCli cli = new MatcherCli(true);

		cli.registerParameterProvider(new AdditionalPluginsCliParameterProvider());
		cli.registerParameterProvider(new PreLaunchGuiCliParameterProvider());
		cli.processArgs(args);
	}
}
