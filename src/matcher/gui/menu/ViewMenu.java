package matcher.gui.menu;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;

import matcher.NameType;
import matcher.gui.Gui;
import matcher.gui.Gui.SortKey;
import matcher.gui.GuiUtil;
import matcher.gui.IGuiComponent;
import matcher.srcprocess.BuiltinDecompiler;

public class ViewMenu extends Menu implements IGuiComponent {
	ViewMenu(Gui gui) {
		super("View");

		this.gui = gui;

		init();
	}

	private void init() {
		sortSourceToggleGroup = new ToggleGroup();

		RadioMenuItem radioMenuItem = new RadioMenuItem("Sort by orig. name");
		radioMenuItem.setToggleGroup(sortSourceToggleGroup);
		radioMenuItem.setUserData(SortKey.Name);
		getItems().add(radioMenuItem);

		radioMenuItem = new RadioMenuItem("Sort by mapped name");
		radioMenuItem.setToggleGroup(sortSourceToggleGroup);
		radioMenuItem.setUserData(SortKey.MappedName);
		getItems().add(radioMenuItem);

		radioMenuItem = new RadioMenuItem("Sort by match status");
		radioMenuItem.setToggleGroup(sortSourceToggleGroup);
		radioMenuItem.setUserData(SortKey.MatchStatus);
		getItems().add(radioMenuItem);

		radioMenuItem = new RadioMenuItem("Sort by similarity");
		radioMenuItem.setToggleGroup(sortSourceToggleGroup);
		radioMenuItem.setUserData(SortKey.Similarity);
		getItems().add(radioMenuItem);

		for (Toggle toggle : sortSourceToggleGroup.getToggles()) {
			if (toggle.getUserData() == gui.getSortKey()) {
				sortSourceToggleGroup.selectToggle(toggle);
				break;
			}
		}

		sortSourceToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null) gui.setSortKey((SortKey) newValue.getUserData());
		});

		getItems().add(new SeparatorMenuItem());

		sortAlphabeticallyItem = GuiUtil.addCheckMenuItem(this, "Sort matches alphabetically",
				gui.isSortMatchesAlphabetically(),
				gui::setSortMatchesAlphabetically);

		useTreeViewItem = GuiUtil.addCheckMenuItem(this, "Use tree view",
				gui.isUseClassTreeView(),
				gui::setUseClassTreeView);

		showNonInputsItem = GuiUtil.addCheckMenuItem(this, "Show non-inputs",
				gui.isShowNonInputs(),
				gui::setShowNonInputs);

		useAuxNamesItems = new CheckMenuItem[NameType.AUX_COUNT];

		for (int i = 0; i < NameType.AUX_COUNT; i++) {
			final int index = i;
			useAuxNamesItems[i] = GuiUtil.addCheckMenuItem(this, String.format("Use aux%s names", i > 0 ? Integer.toString(i + 1) : ""),
					gui.getNameType() != gui.getNameType().withAux(i, false),
					value -> gui.setNameType(gui.getNameType().withAux(index, value)));
		}

		useTmpNamesItem = GuiUtil.addCheckMenuItem(this, "Use tmp names",
				gui.getNameType() != gui.getNameType().withTmp(false),
				value -> gui.setNameType(gui.getNameType().withTmp(value)));

		useDiffColorsItem = GuiUtil.addCheckMenuItem(this, "Use diff colors",
				gui.isUseDiffColors(),
				gui::setUseDiffColors);

		mapCodeViewsItem = GuiUtil.addCheckMenuItem(this, "Map code views",
				gui.getNameType() != gui.getNameType().withMapped(false),
				value -> gui.setNameType(gui.getNameType().withMapped(value)));

		Menu menu = new Menu("Decompiler");
		decompilerToggleGroup = new ToggleGroup();

		for (BuiltinDecompiler decompiler : BuiltinDecompiler.values()) {
			radioMenuItem = new RadioMenuItem(decompiler.name);
			radioMenuItem.setToggleGroup(decompilerToggleGroup);
			radioMenuItem.setUserData(decompiler);
			menu.getItems().add(radioMenuItem);

			if (gui.getDecompiler() == decompiler) {
				radioMenuItem.setSelected(true);
			}
		}

		decompilerToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null) gui.setDecompiler((BuiltinDecompiler) newValue.getUserData());
		});

		getItems().add(menu);
	}

	@Override
	public void onViewChange() {
		updateSelections();
	}

	private void updateSelections() {
		for (Toggle toggle : sortSourceToggleGroup.getToggles()) {
			if (toggle.getUserData() == gui.getSortKey()) {
				sortSourceToggleGroup.selectToggle(toggle);
				break;
			}
		}

		sortAlphabeticallyItem.setSelected(gui.isSortMatchesAlphabetically());
		useTreeViewItem.setSelected(gui.isUseClassTreeView());
		showNonInputsItem.setSelected(gui.isShowNonInputs());

		for (int i = 0; i < NameType.AUX_COUNT; i++) {
			useAuxNamesItems[i].setSelected(gui.getNameType() != gui.getNameType().withAux(i, false));
		}

		useTmpNamesItem.setSelected(gui.getNameType() != gui.getNameType().withTmp(false));
		useDiffColorsItem.setSelected(gui.isUseDiffColors());
		mapCodeViewsItem.setSelected(gui.getNameType() != gui.getNameType().withMapped(false));

		for (Toggle toggle : decompilerToggleGroup.getToggles()) {
			if (toggle.getUserData() == gui.getDecompiler()) {
				decompilerToggleGroup.selectToggle(toggle);
				break;
			}
		}
	}

	private final Gui gui;
	private ToggleGroup sortSourceToggleGroup;
	private CheckMenuItem sortAlphabeticallyItem;
	private CheckMenuItem useTreeViewItem;
	private CheckMenuItem showNonInputsItem;
	private CheckMenuItem[] useAuxNamesItems;
	private CheckMenuItem useTmpNamesItem;
	private CheckMenuItem useDiffColorsItem;
	private CheckMenuItem mapCodeViewsItem;
	private ToggleGroup decompilerToggleGroup;
}
