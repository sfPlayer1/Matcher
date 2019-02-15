package matcher.gui.menu;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import matcher.gui.Gui;
import matcher.gui.Gui.SortKey;
import matcher.srcprocess.BuiltinDecompiler;

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

		checkMenuItem = new CheckMenuItem("Show non-inputs");
		checkMenuItem.setSelected(gui.isShowNonInputs());
		checkMenuItem.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null) gui.setShowNonInputs(newValue);
		});
		getItems().add(checkMenuItem);

		checkMenuItem = new CheckMenuItem("Use tmp names");
		checkMenuItem.setSelected(gui.getNameType() != gui.getNameType().withTmp(false));
		checkMenuItem.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null) gui.setNameType(gui.getNameType().withTmp(newValue));
		});
		getItems().add(checkMenuItem);

		checkMenuItem = new CheckMenuItem("Map code views");
		checkMenuItem.setSelected(gui.getNameType() != gui.getNameType().withMapped(false));
		checkMenuItem.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null) gui.setNameType(gui.getNameType().withMapped(newValue));
		});
		getItems().add(checkMenuItem);

		Menu menu = new Menu("Decompiler");
		toggleGroup = new ToggleGroup();

		for (BuiltinDecompiler decompiler : BuiltinDecompiler.values()) {
			radioMenuItem = new RadioMenuItem(decompiler.name);
			radioMenuItem.setToggleGroup(toggleGroup);
			radioMenuItem.setUserData(decompiler);
			menu.getItems().add(radioMenuItem);

			if (gui.getDecompiler() == decompiler) {
				radioMenuItem.setSelected(true);
			}
		}

		toggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null) gui.setDecompiler((BuiltinDecompiler) newValue.getUserData());
		});

		getItems().add(menu);
	}

	private final Gui gui;
}
