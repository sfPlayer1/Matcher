package matcher.gui.menu;

import java.util.Optional;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import matcher.gui.Gui;
import matcher.gui.menu.FixRecordNamesPane.NamespaceSettings;
import matcher.mapping.MappingPropagator;

public class MappingMenu extends Menu {
	MappingMenu(Gui gui) {
		super("Mapping");

		this.gui = gui;

		init();
	}

	private void init() {
		MenuItem menuItem = new MenuItem("Propagate names");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Propagating method names/args...",
				progressReceiver -> MappingPropagator.propagateNames(gui.getEnv(), progressReceiver),
				() -> {},
				Throwable::printStackTrace));

		menuItem = new MenuItem("Fix record member names");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> fixRecordMemberNames());
	}

	private void fixRecordMemberNames() {
		Dialog<NamespaceSettings> dialog = new Dialog<>();
		//dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(true);
		dialog.setTitle("Mapping Namespace Settings");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		FixRecordNamesPane content = new FixRecordNamesPane();
		dialog.getDialogPane().setContent(content);
		dialog.setResultConverter(button -> button == ButtonType.OK ? content.getSettings() : null);

		Optional<NamespaceSettings> result = dialog.showAndWait();
		if (!result.isPresent()) return;

		NamespaceSettings settings = result.get();

		if (MappingPropagator.fixRecordMemberNames(gui.getEnv(), settings.ns, settings.linkNs)) {
			gui.onMappingChange();
		}
	}


	private final Gui gui;
}
