package matcher.gui.menu;

import java.util.EnumSet;

import javafx.scene.control.Alert.AlertType;
import javafx.application.Platform;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import matcher.Matcher;
import matcher.Matcher.MatchingStatus;
import matcher.gui.Gui;
import matcher.jobs.AutoMatchAllJob;
import matcher.jobs.AutoMatchClassesJob;
import matcher.jobs.AutoMatchFieldsJob;
import matcher.jobs.AutoMatchLocalsJob;
import matcher.jobs.AutoMatchMethodsJob;
import matcher.type.MatchType;

public class MatchingMenu extends Menu {
	MatchingMenu(Gui gui) {
		super("Matching");

		this.gui = gui;

		init();
	}

	private void init() {
		MenuItem menuItem = new MenuItem("Auto match all");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> autoMatchAll());

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Auto class match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> autoMatchClasses());

		menuItem = new MenuItem("Auto method match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> autoMatchMethods());

		menuItem = new MenuItem("Auto field match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> autoMatchFields());

		menuItem = new MenuItem("Auto method arg match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> autoMatchArgs());

		menuItem = new MenuItem("Auto method var match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> autoMatchVars());

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Status");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> showMatchingStatus());
	}

	public void autoMatchAll() {
		var job = new AutoMatchAllJob(gui.getMatcher());
		job.addCompletionListener((result, error) -> {
			if (result.isPresent()) {
				Platform.runLater(() -> gui.onMatchChange(result.get()));
			}
		});
		job.run();
	}

	public void autoMatchClasses() {
		var job = new AutoMatchClassesJob(gui.getMatcher(), Matcher.defaultAutoMatchLevel);
		job.addCompletionListener((matchedAny, error) -> {
			if (matchedAny.orElse(false)) {
				Platform.runLater(() -> gui.onMatchChange(EnumSet.allOf(MatchType.class)));
			}
		});
		job.run();
	}

	public void autoMatchMethods() {
		var job = new AutoMatchMethodsJob(gui.getMatcher(), Matcher.defaultAutoMatchLevel);
		job.addCompletionListener((matchedAny, error) -> {
			if (matchedAny.orElse(false)) {
				Platform.runLater(() -> gui.onMatchChange(EnumSet.of(MatchType.Method)));
			}
		});
		job.run();
	}

	public void autoMatchFields() {
		var job = new AutoMatchFieldsJob(gui.getMatcher(), Matcher.defaultAutoMatchLevel);
		job.addCompletionListener((matchedAny, error) -> {
			if (matchedAny.orElse(false)) {
				Platform.runLater(() -> gui.onMatchChange(EnumSet.of(MatchType.Field)));
			}
		});
		job.run();
	}

	public void autoMatchArgs() {
		var job = new AutoMatchLocalsJob(gui.getMatcher(), Matcher.defaultAutoMatchLevel, true);
		job.addCompletionListener((matchedAny, error) -> {
			if (matchedAny.orElse(false)) {
				Platform.runLater(() -> gui.onMatchChange(EnumSet.of(MatchType.MethodVar)));
			}
		});
		job.run();
	}

	public void autoMatchVars() {
		var job = new AutoMatchLocalsJob(gui.getMatcher(), Matcher.defaultAutoMatchLevel, false);
		job.addCompletionListener((matchedAny, error) -> {
			if (matchedAny.orElse(false)) {
				Platform.runLater(() -> gui.onMatchChange(EnumSet.of(MatchType.MethodVar)));
			}
		});
		job.run();
	}

	public void showMatchingStatus() {
		MatchingStatus status = gui.getMatcher().getStatus(true);

		gui.showAlert(AlertType.INFORMATION, "Matching status", "Current matching status",
				String.format("Classes: %d / %d (%.2f%%)%nMethods: %d / %d (%.2f%%)%nFields: %d / %d (%.2f%%)%nMethod args: %d / %d (%.2f%%)%nMethod vars: %d / %d (%.2f%%)",
						status.matchedClassCount, status.totalClassCount, (status.totalClassCount == 0 ? 0 : 100. * status.matchedClassCount / status.totalClassCount),
						status.matchedMethodCount, status.totalMethodCount, (status.totalMethodCount == 0 ? 0 : 100. * status.matchedMethodCount / status.totalMethodCount),
						status.matchedFieldCount, status.totalFieldCount, (status.totalFieldCount == 0 ? 0 : 100. * status.matchedFieldCount / status.totalFieldCount),
						status.matchedMethodArgCount, status.totalMethodArgCount, (status.totalMethodArgCount == 0 ? 0 : 100. * status.matchedMethodArgCount / status.totalMethodArgCount),
						status.matchedMethodVarCount, status.totalMethodVarCount, (status.totalMethodVarCount == 0 ? 0 : 100. * status.matchedMethodVarCount / status.totalMethodVarCount)
						));
	}

	private final Gui gui;
}
