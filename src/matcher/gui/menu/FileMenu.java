package matcher.gui.menu;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import matcher.config.Config;
import matcher.config.ProjectConfig;
import matcher.gui.Gui;
import matcher.gui.GuiConstants;
import matcher.gui.menu.LoadProjectPane.ProjectLoadSettings;
import matcher.gui.menu.SaveMappingsPane.MappingsSaveSettings;
import matcher.mapping.MappingFormat;
import matcher.mapping.Mappings;
import matcher.serdes.MatchesIo;
import matcher.type.ClassEnvironment;
import matcher.type.MatchType;

public class FileMenu extends Menu {
	FileMenu(Gui gui) {
		super("File");

		this.gui = gui;

		init();
	}

	private void init() {
		MenuItem menuItem = new MenuItem("New project");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> newProject());

		menuItem = new MenuItem("Load project");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadProject());

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Load mappings");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadMappings(null));

		menuItem = new MenuItem("Load mappings (Enigma)");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadMappings(MappingFormat.ENIGMA));

		menuItem = new MenuItem("Load mappings (MCP dir)");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadMappings(MappingFormat.MCP));

		menuItem = new MenuItem("Save mappings");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> saveMappings(false));

		menuItem = new MenuItem("Save mappings (Enigma)");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> saveMappings(true));

		menuItem = new MenuItem("Clear mappings");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> Mappings.clear(gui.getMatcher().getEnv()));

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Load matches");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadMatches());

		menuItem = new MenuItem("Save matches");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> saveMatches());

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Exit");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> Platform.exit());
	}

	private void newProject() {
		Dialog<ProjectConfig> dialog = new Dialog<>();
		//dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(true);
		dialog.setTitle("Project configuration");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
		NewProjectPane content = new NewProjectPane(Config.getProjectConfig(), dialog.getOwner(), okButton);

		dialog.getDialogPane().setContent(content);
		dialog.setResultConverter(button -> button == ButtonType.OK ? content.createConfig() : null);

		dialog.showAndWait().ifPresent(newConfig -> {
			if (!newConfig.isValid()) return;

			Config.setProjectConfig(newConfig);
			Config.saveAsLast();

			gui.getMatcher().reset();
			gui.onProjectChange();

			gui.runProgressTask("Initializing files...",
					progressReceiver -> gui.getMatcher().init(newConfig, progressReceiver),
					() -> gui.onProjectChange(),
					Throwable::printStackTrace);
		});
	}

	private void loadProject() {
		Path file = Gui.requestFile("Select matches file", gui.getScene().getWindow(), getMatchesLoadExtensionFilters(), true);
		if (file == null) return;

		Dialog<ProjectLoadSettings> dialog = new Dialog<>();
		//dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(true);
		dialog.setTitle("Project paths");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);

		LoadProjectPane content = new LoadProjectPane(Config.getInputDirs(), Config.getVerifyInputFiles(), dialog.getOwner(), okButton);
		dialog.getDialogPane().setContent(content);
		dialog.setResultConverter(button -> button == ButtonType.OK ? content.createConfig() : null);

		dialog.showAndWait().ifPresent(newConfig -> {
			if (newConfig.paths.isEmpty()) return;

			Config.setInputDirs(newConfig.paths);
			Config.setVerifyInputFiles(newConfig.verifyFiles);
			Config.saveAsLast();

			gui.getMatcher().reset();
			gui.onProjectChange();

			gui.runProgressTask("Initializing files...",
					progressReceiver -> MatchesIo.read(file, newConfig.paths, newConfig.verifyFiles, gui.getMatcher(), progressReceiver),
					() -> gui.onProjectChange(),
					Throwable::printStackTrace);
		});
	}

	private void loadMappings(MappingFormat format) {
		Window window = gui.getScene().getWindow();
		Path file;

		if (format == null || format.hasSingleFile()) {
			file = Gui.requestFile("Select mapping file", window, getMappingLoadExtensionFilters(), true);
		} else {
			file = Gui.requestDir("Select mapping dir", window);
		}

		if (file == null) return;

		Dialog<boolean[]> dialog = new Dialog<>();
		//dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(true);
		dialog.setTitle("Import Settings");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		GridPane grid = new GridPane();
		grid.setHgap(GuiConstants.padding);
		grid.setVgap(GuiConstants.padding);

		grid.add(new Label("Target:"), 0, 0);

		ToggleGroup targetGroup = new ToggleGroup();
		RadioButton rbA = new RadioButton("A");
		rbA.setToggleGroup(targetGroup);
		rbA.setSelected(true);
		grid.add(rbA, 1, 0);
		RadioButton rbB = new RadioButton("B");
		rbB.setToggleGroup(targetGroup);
		grid.add(rbB, 2, 0);

		CheckBox replaceBox = new CheckBox("Replace");
		replaceBox.setSelected(true);
		grid.add(replaceBox, 0, 1, 3, 1);

		dialog.getDialogPane().setContent(grid);
		dialog.setResultConverter(button -> button == ButtonType.OK ? new boolean[] { rbA.isSelected(), replaceBox.isSelected() } : null);

		Optional<boolean[]> resultOpt = dialog.showAndWait();
		if (!resultOpt.isPresent()) return;

		boolean[] result = resultOpt.get();

		boolean forA = result[0];
		boolean replace = result[1];

		try {
			ClassEnvironment env = gui.getMatcher().getEnv();
			Mappings.load(file, format, forA ? env.getEnvA() : env.getEnvB(), replace);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

		gui.onMappingChange();
	}

	private static List<ExtensionFilter> getMappingLoadExtensionFilters() {
		MappingFormat[] formats = MappingFormat.values();
		List<ExtensionFilter> ret = new ArrayList<>(formats.length + 1);
		List<String> supportedExtensions = new ArrayList<>(formats.length);

		for (MappingFormat format : formats) {
			if (format.hasSingleFile()) supportedExtensions.add(format.getGlobPattern());
		}

		ret.add(new FileChooser.ExtensionFilter("All supported", supportedExtensions));

		for (MappingFormat format : formats) {
			if (format.hasSingleFile()) ret.add(new FileChooser.ExtensionFilter(format.name, format.getGlobPattern()));
		}

		return ret;
	}

	private void saveMappings(boolean toDir) {
		Window window = gui.getScene().getWindow();
		Path path;
		String ext;

		if (!toDir) {
			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Save mapping file");

			for (MappingFormat format : MappingFormat.values()) {
				if (format.hasSingleFile()) {
					fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(format.name, "*."+format.fileExt));
				}
			}

			File file = fileChooser.showSaveDialog(window);
			if (file == null) return;

			path = file.toPath();
			ext = fileChooser.getSelectedExtensionFilter().getDescription();
		} else {
			path = Gui.requestDir("Save mapping dir", window);
			if (path == null) return;

			if (Files.exists(path) && !isDirEmpty(path)) { // reusing existing dir, clear out after confirmation
				if (!gui.requestConfirmation("Save Confirmation", "Replace existing data", "The selected save location is not empty.\nDo you want to clear and reuse it?")) return;

				try {
					if (!clearDir(path, file -> !Files.isDirectory(file) && !file.getFileName().toString().endsWith(".mapping"))) {
						gui.showAlert(AlertType.ERROR, "Save error", "Error while preparing save location", "The target directory contains non-mapping files.");
						return;
					}
				} catch (IOException e) {
					e.printStackTrace();
					gui.showAlert(AlertType.ERROR, "Save error", "Error while preparing save location", e.getMessage());
					return;
				}
			}

			ext = null;
		}

		MappingFormat format = getFormat(path);

		if (format == null) {
			format = getFormat(ext);
			if (format == null) throw new IllegalStateException("mapping format detection failed");

			if (format.hasSingleFile()) {
				path = path.resolveSibling(path.getFileName().toString()+"."+format.fileExt);
			}
		}

		if (Files.exists(path)) {
			if (Files.isDirectory(path) != !format.hasSingleFile()) {
				gui.showAlert(AlertType.ERROR, "Save error", "Invalid file selection", "The selected file is of the wrong type.");
				return;
			}
		}

		Dialog<MappingsSaveSettings> dialog = new Dialog<>();
		//dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(true);
		dialog.setTitle("Mappings export settings");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		SaveMappingsPane content = new SaveMappingsPane();
		dialog.getDialogPane().setContent(content);
		dialog.setResultConverter(button -> button == ButtonType.OK ? content.getSettings() : null);
		final Path savePath = path;
		final MappingFormat saveFormat = format;

		dialog.showAndWait().ifPresent(settings -> {
			ClassEnvironment env = gui.getMatcher().getEnv();

			try {
				if (Files.exists(savePath)) {
					Files.deleteIfExists(savePath);
				}

				if (!Mappings.save(savePath, saveFormat, settings.a ? env.getEnvA() : env.getEnvB(), settings.srcName, settings.dstName, settings.verbosity)) {
					gui.showAlert(AlertType.WARNING, "Mapping save warning", "No mappings to save", "There are currently no names mapped to matched classes, so saving was aborted.");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
		});
	}

	private static boolean isDirEmpty(Path dir) {
		try (Stream<Path> stream = Files.list(dir)) {
			return !stream.anyMatch(ignore -> true);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	private static boolean clearDir(Path path, Predicate<Path> disallowed) throws IOException {
		try (Stream<Path> stream = Files.walk(path, FileVisitOption.FOLLOW_LINKS)) {
			if (stream.anyMatch(disallowed)) return false;
		}

		AtomicBoolean ret = new AtomicBoolean(true);

		Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (disallowed.test(file)) {
					ret.set(false);

					return FileVisitResult.TERMINATE;
				} else {
					Files.delete(file);

					return FileVisitResult.CONTINUE;
				}
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc != null) throw exc;
				if (!dir.equals(path)) Files.delete(dir);

				return FileVisitResult.CONTINUE;
			}
		});

		return ret.get();
	}

	private static MappingFormat getFormat(Path file) {
		if (Files.isDirectory(file)) return MappingFormat.ENIGMA;

		String name = file.getFileName().toString().toLowerCase(Locale.ENGLISH);

		for (MappingFormat format : MappingFormat.values()) {
			if (format.hasSingleFile()
					&& name.endsWith(format.fileExt)
					&& name.length() > format.fileExt.length()
					&& name.charAt(name.length() - 1 - format.fileExt.length()) == '.') {
				return format;
			}
		}

		return null;
	}

	private static MappingFormat getFormat(String selectedName) {
		for (MappingFormat format : MappingFormat.values()) {
			if (format.name.equals(selectedName)) return format;
		}

		return null;
	}

	private void loadMatches() {
		Path file = Gui.requestFile("Select matches file", gui.getScene().getWindow(), getMatchesLoadExtensionFilters(), true);
		if (file == null) return;

		MatchesIo.read(file, null, false, gui.getMatcher(), progress -> {});
		gui.onMatchChange(EnumSet.allOf(MatchType.class));
	}

	private static List<ExtensionFilter> getMatchesLoadExtensionFilters() {
		return Arrays.asList(new FileChooser.ExtensionFilter("Matches", "*.match"));
	}

	private void saveMatches() {
		Path path = Gui.requestFile("Save matches file", gui.getScene().getWindow(), Arrays.asList(new FileChooser.ExtensionFilter("Matches", "*.match")), false);
		if (path == null) return;

		if (!path.getFileName().toString().toLowerCase(Locale.ENGLISH).endsWith(".match")) {
			path = path.resolveSibling(path.getFileName().toString()+".match");
		}

		try {
			if (Files.isDirectory(path)) {
				gui.showAlert(AlertType.ERROR, "Save error", "Invalid file selection", "The selected file is a directory.");
			} else if (Files.exists(path)) {
				Files.deleteIfExists(path);
			}

			if (!MatchesIo.write(gui.getMatcher(), path)) {
				gui.showAlert(AlertType.WARNING, "Matches save warning", "No matches to save", "There are currently no matched classes, so saving was aborted.");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}

	private final Gui gui;
}
