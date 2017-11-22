package matcher.gui.menu;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import matcher.gui.Gui;

public class MappingMenu extends Menu {
	MappingMenu(Gui gui) {
		super("Mapping");

		this.gui = gui;

		init();
	}

	private void init() {
		MenuItem menuItem = new MenuItem("Propagate names");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Propagating method names/args...",
				gui.getMatcher()::propagateNames,
				() -> {},
				Throwable::printStackTrace));
	}

	private final Gui gui;
}
