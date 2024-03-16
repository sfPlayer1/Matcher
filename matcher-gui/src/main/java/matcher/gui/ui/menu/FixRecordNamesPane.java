package matcher.gui.ui.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import matcher.gui.ui.GuiConstants;
import matcher.model.NameType;

class FixRecordNamesPane extends GridPane {
	FixRecordNamesPane() {
		init();
	}

	private void init() {
		setHgap(GuiConstants.padding);
		setVgap(GuiConstants.padding);

		List<NameType> namespaces = new ArrayList<>();

		for (NameType type : NameType.values()) {
			if (type.mapped) namespaces.add(type);
		}

		add(new Label("Member Namespace:"), 0, 0);
		cbNs = new ComboBox<>(FXCollections.observableArrayList(namespaces));
		cbNs.getSelectionModel().select(0);
		add(cbNs, 1, 0);

		List<NameType> linkingNamespaces = new ArrayList<>();
		linkingNamespaces.add(null);
		linkingNamespaces.addAll(Arrays.asList(NameType.values()));

		add(new Label("Linking Namespace:"), 0, 2);
		cbLinkNs = new ComboBox<>(FXCollections.observableArrayList(linkingNamespaces));
		cbLinkNs.getSelectionModel().select(0);
		add(cbLinkNs, 1, 2);
	}

	public NamespaceSettings getSettings() {
		return new NamespaceSettings(cbNs.getValue(), cbLinkNs.getValue());
	}

	public static class NamespaceSettings {
		NamespaceSettings(NameType ns, NameType linkNs) {
			this.ns = ns;
			this.linkNs = linkNs;
		}

		public final NameType ns;
		public final NameType linkNs;
	}

	private ComboBox<NameType> cbNs;
	private ComboBox<NameType> cbLinkNs;
}
