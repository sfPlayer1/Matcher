package matcher.gui;

import javafx.application.Application;

import matcher.PluginLoader;
import matcher.config.Config;
import matcher.gui.ui.Gui;

public class Main {
	public static void main(String[] args) {
		Config.init(args);
		PluginLoader.run(args);
		Application.launch(Gui.class, args);
	}
}
