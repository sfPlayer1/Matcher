package matcher.gui.menu;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import matcher.gui.GuiConstants;
import matcher.mapping.MappingField;

class LoadMappingsPane extends GridPane {
	LoadMappingsPane(String[] namespaces) {
		init(namespaces);
	}

	private void init(String[] namespaces) {
		setHgap(GuiConstants.padding);
		setVgap(GuiConstants.padding);

		add(new Label("Namespace"), 1, 0);
		add(new Label("Field"), 2, 0);

		add(new Label("Source:"), 0, 1);
		cbNsSrc = new ComboBox<>(FXCollections.observableArrayList(namespaces));
		cbNsSrc.getSelectionModel().select(0);
		add(cbNsSrc, 1, 1);
		cbFieldSrc = new ComboBox<>(FXCollections.observableArrayList(MappingField.VALUES));
		cbFieldSrc.getSelectionModel().select(MappingField.PLAIN);
		add(cbFieldSrc, 2, 1);

		add(new Label("Target:"), 0, 2);
		cbNsDst = new ComboBox<>(FXCollections.observableArrayList(namespaces));
		cbNsDst.getSelectionModel().select(1);
		add(cbNsDst, 1, 2);
		ObservableList<MappingField> dstValues = FXCollections.observableArrayList(MappingField.VALUES);
		dstValues.remove(MappingField.PLAIN);
		cbFieldDst = new ComboBox<>(dstValues);
		cbFieldDst.getSelectionModel().select(MappingField.MAPPED);
		add(cbFieldDst, 2, 2);

		new DedupeChangeListener<>(cbNsSrc, cbNsDst);
		new DedupeChangeListener<>(cbFieldSrc, cbFieldDst);

		add(new Label("Side:"), 0, 3);

		HBox hBox = new HBox();
		ToggleGroup group = new ToggleGroup();

		rbA = new RadioButton("A (left)");
		rbA.setToggleGroup(group);
		hBox.getChildren().add(rbA);

		rbB = new RadioButton("B (right)");
		rbB.setToggleGroup(group);
		rbB.setSelected(true);
		hBox.getChildren().add(rbB);

		add(hBox, 1, 3, 2, 1);

		cbReplace = new CheckBox("Replace");
		cbReplace.setSelected(true);
		add(cbReplace, 0, 4, 3, 1);
	}

	public MappingsLoadSettings getSettings() {
		return new MappingsLoadSettings(cbNsSrc.getValue(), cbNsDst.getValue(), cbFieldSrc.getValue(), cbFieldDst.getValue(), rbA.isSelected(), cbReplace.isSelected());
	}

	public static class MappingsLoadSettings {
		public MappingsLoadSettings(String nsSource, String nsTarget, MappingField fieldSource, MappingField fieldTarget, boolean a, boolean replace) {
			this.nsSource = nsSource;
			this.nsTarget = nsTarget;
			this.fieldSource = fieldSource;
			this.fieldTarget = fieldTarget;
			this.a = a;
			this.replace = replace;
		}

		public final String nsSource;
		public final String nsTarget;
		public final MappingField fieldSource;
		public final MappingField fieldTarget;
		public final boolean a;
		public final boolean replace;
	}

	// logic to avoid selecting the same value for source+target, swaps selections if this condition would emerge
	private static class DedupeChangeListener<T> implements ChangeListener<T> {
		public DedupeChangeListener(ComboBox<T> a, ComboBox<T> b) {
			this.a = a;
			this.b = b;

			a.getSelectionModel().selectedItemProperty().addListener(this);
			b.getSelectionModel().selectedItemProperty().addListener(this);

			assert !a.getSelectionModel().getSelectedItem().equals(b.getSelectionModel().getSelectedItem());
		}

		@Override
		public void changed(ObservableValue<? extends T> observable, T oldValue, T newValue) {
			if (suppress) return;

			ComboBox<T> cbOther; // combo box that wasn't changed

			if (observable == a.getSelectionModel().selectedItemProperty()) {
				cbOther = b;
			} else {
				cbOther = a;
			}

			if (newValue.equals(cbOther.getSelectionModel().getSelectedItem())) {
				if (!cbOther.getItems().contains(oldValue)) { // swap is not possible, use first suitable value
					for (T v : cbOther.getItems()) {
						if (!v.equals(newValue)) {
							oldValue = v;
							break;
						}
					}
				}

				suppress = true; // avoid recursive event
				cbOther.getSelectionModel().select(oldValue);
				suppress = false;
			}
		}

		private final ComboBox<T> a;
		private final ComboBox<T> b;
		private boolean suppress;
	}

	private ComboBox<String> cbNsSrc;
	private ComboBox<String> cbNsDst;
	private ComboBox<MappingField> cbFieldSrc;
	private ComboBox<MappingField> cbFieldDst;
	private RadioButton rbA;
	private RadioButton rbB;
	private CheckBox cbReplace;
}
