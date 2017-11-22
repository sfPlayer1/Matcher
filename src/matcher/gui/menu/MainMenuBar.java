package matcher.gui.menu;

import javafx.scene.control.MenuBar;
import matcher.gui.Gui;
import matcher.gui.IGuiComponent;

public class MainMenuBar extends MenuBar implements IGuiComponent {
	public MainMenuBar(Gui gui) {
		this.gui = gui;

		init();
	}

	private void init() {
		getMenus().add(new FileMenu(gui));
		getMenus().add(new MatchingMenu(gui));
		getMenus().add(new MappingMenu(gui));
		getMenus().add(new UidMenu(gui));
		getMenus().add(new ViewMenu(gui));
	}

	private final Gui gui;
}
