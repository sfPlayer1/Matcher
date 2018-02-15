package matcher.gui.menu;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import matcher.gui.Gui;
import matcher.gui.Gui.SortKey;

public class ViewMenu extends Menu {
	ViewMenu(Gui gui) {
		super("View");

		this.gui = gui;

		init();
	}

	private void init() {
		ToggleGroup toggleGroup = new ToggleGroup();

		RadioMenuItem radioMenuItem = new RadioMenuItem("Sort by orig. name");
		radioMenuItem.setToggleGroup(toggleGroup);
		radioMenuItem.setUserData(SortKey.Name);
		getItems().add(radioMenuItem);

		radioMenuItem = new RadioMenuItem("Sort by mapped name");
		radioMenuItem.setToggleGroup(toggleGroup);
		radioMenuItem.setUserData(SortKey.MappedName);
		getItems().add(radioMenuItem);

		radioMenuItem = new RadioMenuItem("Sort by match status");
		radioMenuItem.setToggleGroup(toggleGroup);
		radioMenuItem.setUserData(SortKey.MatchStatus);
		getItems().add(radioMenuItem);

		for (Toggle toggle : toggleGroup.getToggles()) {
			if (toggle.getUserData() == gui.getSortKey()) {
				toggleGroup.selectToggle(toggle);
				break;
			}
		}

		toggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null) gui.setSortKey((SortKey) newValue.getUserData());
		});

		getItems().add(new SeparatorMenuItem());

		CheckMenuItem checkMenuItem = new CheckMenuItem("Sort matches alphabetically");
		checkMenuItem.setSelected(gui.isSortMatchesAlphabetically());
		checkMenuItem.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null) gui.setSortMatchesAlphabetically(newValue);
		});
		getItems().add(checkMenuItem);

		getItems().add(new SeparatorMenuItem());

		checkMenuItem = new CheckMenuItem("Show non-inputs");
		checkMenuItem.setSelected(gui.isShowNonInputs());
		checkMenuItem.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null) gui.setShowNonInputs(newValue);
		});
		getItems().add(checkMenuItem);
	}

	private final Gui gui;
}
