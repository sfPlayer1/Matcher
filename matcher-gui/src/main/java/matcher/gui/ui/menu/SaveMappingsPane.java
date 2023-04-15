package matcher.gui.ui.menu;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import matcher.NameType;
import matcher.mapping.MappingsExportVerbosity;
import matcher.gui.ui.GuiConstants;

class SaveMappingsPane extends GridPane {
	SaveMappingsPane(boolean offerNamespaces) {
		init(offerNamespaces);
	}

	private void init(boolean offerNamespaces) {
		setHgap(GuiConstants.padding);
		setVgap(GuiConstants.padding);

		int col23Span = offerNamespaces ? 2 : 1;

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

		add(hBox, 1, 0, col23Span, 1);

		add(new Label("Source name type:"), 0, 1);
		cbSrc = new ComboBox<>(FXCollections.observableArrayList(NameType.values()));
		cbSrc.getSelectionModel().select(NameType.PLAIN);
		add(cbSrc, 1, 1);

		if (offerNamespaces) {
			tfSrc = new TextField();
			add(tfSrc, 2, 1);
		}

		add(new Label("Target name type:"), 0, 2);
		cbDst = new ComboBox<>(FXCollections.observableArrayList(NameType.values()));
		cbDst.getSelectionModel().select(NameType.MAPPED_PLAIN);
		add(cbDst, 1, 2);

		if (offerNamespaces) {
			tfDst = new TextField();
			add(tfDst, 2, 2);
		}

		add(new Label("Verbosity:"), 0, 3);
		cbVerbosity = new ComboBox<>(FXCollections.observableArrayList(MappingsExportVerbosity.values()));
		cbVerbosity.getSelectionModel().select(MappingsExportVerbosity.FULL);
		add(cbVerbosity, 1, 3, col23Span, 1);

		cbForAnyInput = new CheckBox("compatible with any input");
		cbForAnyInput.setSelected(true);
		add(cbForAnyInput, 0, 4, 1 + col23Span, 1);

		cbFieldsFirst = new CheckBox("emit fields first");
		add(cbFieldsFirst, 0, 5, 1 + col23Span, 1);
	}

	public MappingsSaveSettings getSettings() {
		return new MappingsSaveSettings(rbA.isSelected(),
				List.of(cbSrc.getValue(), cbDst.getValue()), (tfSrc != null ? List.of(tfSrc.getText(), tfDst.getText()) : null),
				cbVerbosity.getValue(), cbForAnyInput.isSelected(), cbFieldsFirst.isSelected());
	}

	public static class MappingsSaveSettings {
		MappingsSaveSettings(boolean a, List<NameType> nsTypes, List<String> nsNames,
				MappingsExportVerbosity verbosity, boolean forAnyInput, boolean fieldsFirst) {
			this.a = a;
			this.nsTypes = nsTypes;
			this.nsNames = nsNames;
			this.verbosity = verbosity;
			this.forAnyInput = forAnyInput;
			this.fieldsFirst = fieldsFirst;
		}

		public final boolean a;
		public final List<NameType> nsTypes;
		public final List<String> nsNames;
		public final MappingsExportVerbosity verbosity;
		public final boolean forAnyInput;
		public final boolean fieldsFirst;
	}

	private RadioButton rbA;
	private RadioButton rbB;
	private ComboBox<NameType> cbSrc;
	private TextField tfSrc;
	private ComboBox<NameType> cbDst;
	private TextField tfDst;
	private ComboBox<MappingsExportVerbosity> cbVerbosity;
	private CheckBox cbForAnyInput;
	private CheckBox cbFieldsFirst;
}
