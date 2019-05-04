package matcher.gui.menu;

import java.util.EnumSet;

import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import matcher.Matcher.MatchingStatus;
import matcher.gui.Gui;
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
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Auto matching...",
				gui.getMatcher()::autoMatchAll,
				() -> gui.onMatchChange(EnumSet.allOf(MatchType.class)),
				Throwable::printStackTrace));

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Auto class match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Auto matching classes...",
				gui.getMatcher()::autoMatchClasses,
				() -> gui.onMatchChange(EnumSet.allOf(MatchType.class)),
				Throwable::printStackTrace));

		menuItem = new MenuItem("Auto method match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Auto matching methods...",
				gui.getMatcher()::autoMatchMethods,
				() -> gui.onMatchChange(EnumSet.of(MatchType.Method)),
				Throwable::printStackTrace));

		menuItem = new MenuItem("Auto field match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Auto matching fields...",
				gui.getMatcher()::autoMatchFields,
				() -> gui.onMatchChange(EnumSet.of(MatchType.Field)),
				Throwable::printStackTrace));

		menuItem = new MenuItem("Auto method arg match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Auto matching method args...",
				gui.getMatcher()::autoMatchMethodArgs,
				() -> gui.onMatchChange(EnumSet.of(MatchType.MethodVar)),
				Throwable::printStackTrace));

		menuItem = new MenuItem("Auto method var match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Auto matching method vars...",
				gui.getMatcher()::autoMatchMethodVars,
				() -> gui.onMatchChange(EnumSet.of(MatchType.MethodVar)),
				Throwable::printStackTrace));

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Status");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> showMatchingStatus());
	}

	private void showMatchingStatus() {
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
