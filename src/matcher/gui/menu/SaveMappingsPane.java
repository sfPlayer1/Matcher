package matcher.gui.menu;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import matcher.NameType;
import matcher.gui.GuiConstants;
import matcher.mapping.MappingsExportVerbosity;

class SaveMappingsPane extends GridPane {
	SaveMappingsPane() {
		init();
	}

	private void init() {
		setHgap(GuiConstants.padding);
		setVgap(GuiConstants.padding);

		add(new Label("Environment:"), 0, 0);

		HBox hBox = new HBox();
		ToggleGroup envGroup = new ToggleGroup();

		rbA = new RadioButton("A (left)");
		rbA.setToggleGroup(envGroup);
		hBox.getChildren().add(rbA);

		rbB = new RadioButton("B (right)");
		rbB.setToggleGroup(envGroup);
		rbB.setSelected(true);
		hBox.getChildren().add(rbB);

		add(hBox, 1, 0);

		add(new Label("Source name type:"), 0, 1);
		cbSrc = new ComboBox<>(FXCollections.observableArrayList(NameType.values()));
		cbSrc.getSelectionModel().select(NameType.PLAIN);
		add(cbSrc, 1, 1);

		add(new Label("Target name type:"), 0, 2);
		cbDst = new ComboBox<>(FXCollections.observableArrayList(NameType.values()));
		cbDst.getSelectionModel().select(NameType.MAPPED_PLAIN);
		add(cbDst, 1, 2);

		add(new Label("Verbosity:"), 0, 3);
		cbVerbosity = new ComboBox<>(FXCollections.observableArrayList(MappingsExportVerbosity.values()));
		cbVerbosity.getSelectionModel().select(MappingsExportVerbosity.FULL);
		add(cbVerbosity, 1, 3);
	}

	public MappingsSaveSettings getSettings() {
		return new MappingsSaveSettings(rbA.isSelected(), cbSrc.getValue(), cbDst.getValue(), cbVerbosity.getValue());
	}

	public static class MappingsSaveSettings {
		public MappingsSaveSettings(boolean a, NameType srcName, NameType dstName, MappingsExportVerbosity verbosity) {
			this.a = a;
			this.srcName = srcName;
			this.dstName = dstName;
			this.verbosity = verbosity;
		}

		public final boolean a;
		public final NameType srcName;
		public final NameType dstName;
		public final MappingsExportVerbosity verbosity;
	}

	private RadioButton rbA;
	private RadioButton rbB;
	private ComboBox<NameType> cbSrc;
	private ComboBox<NameType> cbDst;
	private ComboBox<MappingsExportVerbosity> cbVerbosity;
}
