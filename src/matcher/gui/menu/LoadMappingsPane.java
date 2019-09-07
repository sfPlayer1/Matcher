package matcher.gui.menu;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import matcher.gui.GuiConstants;

class LoadMappingsPane extends GridPane {
	LoadMappingsPane(String[] namespaces) {
		init(namespaces);
	}

	private void init(String[] namespaces) {
		setHgap(GuiConstants.padding);
		setVgap(GuiConstants.padding);

		add(new Label("Source NS:"), 0, 0);
		cbSrc = new ComboBox<>(FXCollections.observableArrayList(namespaces));
		cbSrc.getSelectionModel().select(0);
		add(cbSrc, 1, 0);

		add(new Label("Target NS:"), 0, 1);
		cbDst = new ComboBox<>(FXCollections.observableArrayList(namespaces));
		cbDst.getSelectionModel().select(1);
		add(cbDst, 1, 1);

		// logic to avoid selecting the same ns for source+target, swaps selections if this condition would emerge
		ChangeListener<String> nsChangeListener = new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				if (suppress) return;

				ComboBox<String> cbOther; // combo box that wasn't changed

				if (observable == cbSrc.getSelectionModel().selectedItemProperty()) {
					cbOther = cbDst;
				} else {
					cbOther = cbSrc;
				}

				if (newValue.equals(cbOther.getSelectionModel().getSelectedItem())) {
					suppress = true; // avoid recursive event
					cbOther.getSelectionModel().select(oldValue);
					suppress = false;
				}
			}

			private boolean suppress;
		};

		cbSrc.getSelectionModel().selectedItemProperty().addListener(nsChangeListener);
		cbDst.getSelectionModel().selectedItemProperty().addListener(nsChangeListener);

		add(new Label("Type:"), 0, 2);

		HBox hBox = new HBox();
		ToggleGroup group = new ToggleGroup();

		rbNames = new RadioButton("Names");
		rbNames.setToggleGroup(group);
		rbNames.setSelected(true);
		hBox.getChildren().add(rbNames);

		rbUids = new RadioButton("UIDs");
		rbUids.setToggleGroup(group);
		hBox.getChildren().add(rbUids);

		add(hBox, 1, 2);

		add(new Label("Target:"), 0, 3);

		hBox = new HBox();
		group = new ToggleGroup();

		rbA = new RadioButton("A (left)");
		rbA.setToggleGroup(group);
		hBox.getChildren().add(rbA);

		rbB = new RadioButton("B (right)");
		rbB.setToggleGroup(group);
		rbB.setSelected(true);
		hBox.getChildren().add(rbB);

		add(hBox, 1, 3);

		cbReplace = new CheckBox("Replace");
		cbReplace.setSelected(true);
		add(cbReplace, 0, 4, 2, 1);
	}

	public MappingsLoadSettings getSettings() {
		return new MappingsLoadSettings(cbSrc.getValue(), cbDst.getValue(), rbNames.isSelected(), rbA.isSelected(), cbReplace.isSelected());
	}

	public static class MappingsLoadSettings {
		public MappingsLoadSettings(String nsSource, String nsTarget, boolean names, boolean a, boolean replace) {
			this.nsSource = nsSource;
			this.nsTarget = nsTarget;
			this.names = names;
			this.a = a;
			this.replace = replace;
		}

		public final String nsSource;
		public final String nsTarget;
		public final boolean names;
		public final boolean a;
		public final boolean replace;
	}

	private ComboBox<String> cbSrc;
	private ComboBox<String> cbDst;
	private RadioButton rbNames;
	private RadioButton rbUids;
	private RadioButton rbA;
	private RadioButton rbB;
	private CheckBox cbReplace;
}
