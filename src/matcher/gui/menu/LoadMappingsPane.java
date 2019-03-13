package matcher.gui.menu;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import matcher.gui.GuiConstants;

class LoadMappingsPane extends GridPane {
	LoadMappingsPane() {
		init();
	}

	private void init() {
		setHgap(GuiConstants.padding);
		setVgap(GuiConstants.padding);

		add(new Label("Type:"), 0, 0);

		HBox hBox = new HBox();
		ToggleGroup group = new ToggleGroup();

		rbNames = new RadioButton("Names");
		rbNames.setToggleGroup(group);
		rbNames.setSelected(true);
		hBox.getChildren().add(rbNames);

		rbUids = new RadioButton("UIDs");
		rbUids.setToggleGroup(group);
		hBox.getChildren().add(rbUids);

		add(hBox, 1, 0);

		add(new Label("Target:"), 0, 1);

		hBox = new HBox();
		group = new ToggleGroup();

		rbA = new RadioButton("A (left)");
		rbA.setToggleGroup(group);
		hBox.getChildren().add(rbA);

		rbB = new RadioButton("B (right)");
		rbB.setToggleGroup(group);
		rbB.setSelected(true);
		hBox.getChildren().add(rbB);

		add(hBox, 1, 1);

		cbReplace = new CheckBox("Replace");
		cbReplace.setSelected(true);
		add(cbReplace, 0, 2, 2, 1);
	}

	public MappingsLoadSettings getSettings() {
		return new MappingsLoadSettings(rbNames.isSelected(), rbA.isSelected(), cbReplace.isSelected());
	}

	public static class MappingsLoadSettings {
		public MappingsLoadSettings(boolean names, boolean a, boolean replace) {
			this.names = names;
			this.a = a;
			this.replace = replace;
		}

		public final boolean names;
		public final boolean a;
		public final boolean replace;
	}

	private RadioButton rbNames;
	private RadioButton rbUids;
	private RadioButton rbA;
	private RadioButton rbB;
	private CheckBox cbReplace;
}
