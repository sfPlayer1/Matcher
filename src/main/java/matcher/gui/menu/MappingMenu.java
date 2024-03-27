package matcher.gui.menu;

import java.util.Optional;
import java.util.function.DoubleConsumer;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import matcher.gui.Gui;
import matcher.gui.panes.FixRecordNamesPane;
import matcher.gui.panes.FixRecordNamesPane.NamespaceSettings;
import matcher.jobs.JobCategories;
import matcher.jobs.MatcherJob;
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

		var job = new MatcherJob<Void>(JobCategories.PROPAGATE_METHOD_NAMES) {
			@Override
			protected Void execute(DoubleConsumer progressReceiver) {
				MappingPropagator.propagateNames(gui.getEnv(), progressReceiver);
				return null;
			}
		};

		menuItem.setOnAction(event -> job.run());

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
