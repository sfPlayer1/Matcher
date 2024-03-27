package matcher.gui.menu;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import matcher.Matcher;
import matcher.config.Config;
import matcher.config.UidConfig;
import matcher.gui.Gui;
import matcher.gui.panes.UidSetupPane;
import matcher.jobs.ImportMatchesJob;
import matcher.jobs.SubmitMatchesJob;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MatchType;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;

public class UidMenu extends Menu {
	UidMenu(Gui gui) {
		super("UID");

		this.gui = gui;

		init();
	}

	private void init() {
		MenuItem menuItem = new MenuItem("Setup");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> setup());

		getItems().add(new SeparatorMenuItem());

		var importJob = new ImportMatchesJob(gui.getMatcher());
		importJob.addCompletionListener((importedAny, error) -> {
			if (importedAny.isPresent()) {
				Platform.runLater(() -> gui.onMatchChange(EnumSet.allOf(MatchType.class)));
			}
		});

		menuItem = new MenuItem("Import matches");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> importJob.run());

		var submitJob = new SubmitMatchesJob(gui.getMatcher());

		menuItem = new MenuItem("Submit matches");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> submitJob.run());

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Assign missing");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> assignMissing());
	}

	private void setup() {
		Dialog<UidConfig> dialog = new Dialog<>();
		//dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(true);
		dialog.setTitle("UID Setup");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);

		UidSetupPane content = new UidSetupPane(Config.getUidConfig(), dialog.getOwner(), okButton);
		dialog.getDialogPane().setContent(content);
		dialog.setResultConverter(button -> button == ButtonType.OK ? content.createConfig() : null);

		dialog.showAndWait().ifPresent(newConfig -> {
			if (!newConfig.isValid()) return;

			Config.setUidConfig(newConfig);
			Config.saveAsLast();
		});
	}

	private void assignMissing() {
		ClassEnvironment env = gui.getEnv();

		int nextClassUid = env.nextClassUid;
		int nextMethodUid = env.nextMethodUid;
		int nextFieldUid = env.nextFieldUid;

		List<ClassInstance> classes = new ArrayList<>(env.getClassesB());
		classes.sort(ClassInstance.nameComparator);

		List<MethodInstance> methods = new ArrayList<>();
		List<FieldInstance> fields = new ArrayList<>();

		for (ClassInstance cls : classes) {
			assert cls.isInput();

			if (cls.isNameObfuscated() && cls.getUid() < 0) {
				cls.setUid(nextClassUid++);
			}

			for (MethodInstance method : cls.getMethods()) {
				if (method.isNameObfuscated() && method.getUid() < 0) {
					methods.add(method);
				}
			}

			if (!methods.isEmpty()) {
				methods.sort(MemberInstance.nameComparator);

				for (MethodInstance method : methods) {
					int uid = nextMethodUid++;

					for (MethodInstance m : method.getAllHierarchyMembers()) {
						m.setUid(uid);
					}
				}

				methods.clear();
			}

			for (FieldInstance field : cls.getFields()) {
				if (field.isNameObfuscated() && field.getUid() < 0) {
					fields.add(field);
				}
			}

			if (!fields.isEmpty()) {
				fields.sort(MemberInstance.nameComparator);

				for (FieldInstance field : fields) {
					field.setUid(nextFieldUid++);
					assert field.getAllHierarchyMembers().size() == 1;
				}

				fields.clear();
			}
		}

		Matcher.LOGGER.info("UIDs assigned: {} class, {} method, {} field",
				nextClassUid - env.nextClassUid,
				nextMethodUid - env.nextMethodUid,
				nextFieldUid - env.nextFieldUid);

		env.nextClassUid = nextClassUid;
		env.nextMethodUid = nextMethodUid;
		env.nextFieldUid = nextFieldUid;
	}

	private final Gui gui;
}
