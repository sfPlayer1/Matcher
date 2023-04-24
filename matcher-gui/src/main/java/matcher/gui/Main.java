package matcher.gui;

import javafx.application.Application;

import matcher.PluginLoader;
import matcher.config.Config;

public class Main {
	public static void main(String[] args) {
		Config.init();
		PluginLoader.run(args);
		Application.launch(MatcherGui.class, args);
	}
}
