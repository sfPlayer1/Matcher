package matcher;

import javafx.application.Application;
import matcher.config.Config;
import matcher.gui.Gui;

public class Main {
	public static void main(String[] args) {
		Config.init();
		PluginLoader.run();
		Application.launch(Gui.class, args);
	}
}
