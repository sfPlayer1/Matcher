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
		getMenus().add(fileMenu = new FileMenu(gui));
		getMenus().add(matchingMenu = new MatchingMenu(gui));
		getMenus().add(mappingMenu = new MappingMenu(gui));
		getMenus().add(uidMenu = new UidMenu(gui));
		getMenus().add(viewMenu = new ViewMenu(gui));
	}

	public FileMenu getFileMenu() {
		return fileMenu;
	}

	public MatchingMenu getMatchingMenu() {
		return matchingMenu;
	}

	public MappingMenu getMappingMenu() {
		return mappingMenu;
	}

	public UidMenu getUidMenu() {
		return uidMenu;
	}

	public ViewMenu getViewMenu() {
		return viewMenu;
	}

	private final Gui gui;

	private FileMenu fileMenu;
	private MatchingMenu matchingMenu;
	private MappingMenu mappingMenu;
	private UidMenu uidMenu;
	private ViewMenu viewMenu;
}
