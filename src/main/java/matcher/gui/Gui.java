package matcher.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import job4j.Job;
import job4j.JobManager;
import job4j.JobManager.JobManagerEvent;
import job4j.JobSettings.MutableJobSettings;
import javafx.stage.Stage;
import javafx.stage.Window;

import net.fabricmc.mappingio.MappingReader;

import matcher.Matcher;
import matcher.NameType;
import matcher.config.Config;
import matcher.config.ProjectConfig;
import matcher.config.Theme;
import matcher.gui.IGuiComponent.ViewChangeCause;
import matcher.gui.jobs.GuiJobCategories;
import matcher.gui.menu.MainMenuBar;
import matcher.gui.panes.NewProjectPane;
import matcher.jobs.MatcherJob;
import matcher.mapping.MappingField;
import matcher.mapping.Mappings;
import matcher.srcprocess.BuiltinDecompiler;
import matcher.type.ClassEnvironment;
import matcher.type.MatchType;

public class Gui extends Application {
	@Override
	public void start(Stage stage) {
		Matcher.init();

		env = new ClassEnvironment();
		matcher = new Matcher(env);

		JobManager.get().registerEventListener((job, event) -> Platform.runLater(() -> onJobManagerEvent(job, event)));

		GridPane border = new GridPane();

		ColumnConstraints colConstraint = new ColumnConstraints();
		colConstraint.setPercentWidth(50);
		border.getColumnConstraints().addAll(colConstraint, colConstraint);

		RowConstraints defaultRowConstraints = new RowConstraints();
		RowConstraints contentRowConstraints = new RowConstraints();
		contentRowConstraints.setVgrow(Priority.ALWAYS);
		border.getRowConstraints().addAll(defaultRowConstraints, contentRowConstraints, defaultRowConstraints);

		menu = new MainMenuBar(this);
		components.add(menu);
		border.add(menu, 0, 0, 2, 1);

		srcPane = new MatchPaneSrc(this);
		components.add(srcPane);
		border.add(srcPane, 0, 1);

		dstPane = new MatchPaneDst(this, srcPane);
		components.add(dstPane);
		border.add(dstPane, 1, 1);

		bottomPane = new BottomPane(this, srcPane, dstPane);
		components.add(bottomPane);
		border.add(bottomPane, 0, 2, 2, 1);

		scene = new Scene(border, 1400, 800);
		Shortcuts.init(this);

		for (Consumer<Gui> l : loadListeners) {
			l.accept(this);
		}

		updateCss();

		stage.setScene(scene);
		stage.setTitle("Matcher");
		stage.show();

		border.requestFocus();
		handleStartupArgs(getParameters().getRaw());
	}

	@Override
	public void stop() throws Exception {
		JobManager.get().shutdown();
	}

	private void handleStartupArgs(List<String> args) {
		List<Path> inputsA = new ArrayList<>();
		List<Path> inputsB = new ArrayList<>();
		List<Path> classPathA = new ArrayList<>();
		List<Path> classPathB = new ArrayList<>();
		List<Path> sharedClassPath = new ArrayList<>();
		boolean inputsBeforeClassPath = false;
		Path mappingsPathA = null;
		Path mappingsPathB = null;
		boolean saveUnmappedMatches = true;
		String nonObfuscatedClassPatternA = "";
		String nonObfuscatedClassPatternB = "";
		String nonObfuscatedMemberPatternA = "";
		String nonObfuscatedMemberPatternB = "";
		boolean validProjectConfigArgPresent = false;

		for (int i = 0; i < args.size(); i++) {
			switch (args.get(i)) {
			// ProjectConfig args

			case "--inputs-a":
				while (i+1 < args.size() && !args.get(i+1).startsWith("--")) {
					inputsA.add(Path.of(args.get(++i)));
					validProjectConfigArgPresent = true;
				}

				break;
			case "--inputs-b":
				while (i+1 < args.size() && !args.get(i+1).startsWith("--")) {
					inputsB.add(Path.of(args.get(++i)));
					validProjectConfigArgPresent = true;
				}

				break;
			case "--classpath-a":
				while (i+1 < args.size() && !args.get(i+1).startsWith("--")) {
					classPathA.add(Path.of(args.get(++i)));
					validProjectConfigArgPresent = true;
				}

				break;
			case "--classpath-b":
				while (i+1 < args.size() && !args.get(i+1).startsWith("--")) {
					classPathB.add(Path.of(args.get(++i)));
					validProjectConfigArgPresent = true;
				}

				break;
			case "--shared-classpath":
				while (i+1 < args.size() && !args.get(i+1).startsWith("--")) {
					sharedClassPath.add(Path.of(args.get(++i)));
					validProjectConfigArgPresent = true;
				}

				break;
			case "--mappings-a":
				mappingsPathA = Path.of(args.get(++i));
				validProjectConfigArgPresent = true;
				break;
			case "--mappings-b":
				mappingsPathB = Path.of(args.get(++i));
				validProjectConfigArgPresent = true;
				break;
			case "--dont-save-unmapped-matches":
				saveUnmappedMatches = false;
				validProjectConfigArgPresent = true;
				break;
			case "--inputs-before-classpath":
				inputsBeforeClassPath = true;
				validProjectConfigArgPresent = true;
				break;
			case "--non-obfuscated-class-pattern-a":
				nonObfuscatedClassPatternA = args.get(++i);
				validProjectConfigArgPresent = true;
				break;
			case "--non-obfuscated-class-pattern-b":
				nonObfuscatedClassPatternB = args.get(++i);
				validProjectConfigArgPresent = true;
				break;
			case "--non-obfuscated-member-pattern-a":
				nonObfuscatedMemberPatternA = args.get(++i);
				validProjectConfigArgPresent = true;
				break;
			case "--non-obfuscated-member-pattern-b":
				nonObfuscatedMemberPatternB = args.get(++i);
				validProjectConfigArgPresent = true;
				break;

			// GUI args

			case "--hide-unmapped-a":
				hideUnmappedA = true;
				break;
			}
		}

		if (!validProjectConfigArgPresent) return;

		ProjectConfig config = new ProjectConfig.Builder(inputsA, inputsB)
				.classPathA(new ArrayList<>(classPathA))
				.classPathB(new ArrayList<>(classPathB))
				.sharedClassPath(new ArrayList<>(sharedClassPath))
				.inputsBeforeClassPath(inputsBeforeClassPath)
				.mappingsPathA(mappingsPathA)
				.mappingsPathB(mappingsPathB)
				.saveUnmappedMatches(saveUnmappedMatches)
				.nonObfuscatedClassPatternA(nonObfuscatedClassPatternA)
				.nonObfuscatedClassPatternB(nonObfuscatedClassPatternB)
				.nonObfuscatedMemberPatternA(nonObfuscatedMemberPatternA)
				.nonObfuscatedMemberPatternB(nonObfuscatedMemberPatternB)
				.build();

		newProject(config, inputsA.isEmpty() || inputsB.isEmpty());
	}

	public void newProject(ProjectConfig config, boolean showConfigDialog) {
		ProjectConfig newConfig;

		if (showConfigDialog) {
			Dialog<ProjectConfig> dialog = new Dialog<>();
			//dialog.initModality(Modality.APPLICATION_MODAL);
			dialog.setResizable(true);
			dialog.setTitle("Project configuration");
			dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

			Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
			NewProjectPane content = new NewProjectPane(config, dialog.getOwner(), okButton);

			dialog.getDialogPane().setContent(content);
			dialog.setResultConverter(button -> button == ButtonType.OK ? content.createConfig() : null);

			newConfig = dialog.showAndWait().orElse(null);
			if (newConfig == null || !newConfig.isValid()) return;
		} else {
			newConfig = config;
		}

		Config.setProjectConfig(newConfig);
		Config.saveAsLast();

		matcher.reset();
		onProjectChange();

		var job = new MatcherJob<Void>(GuiJobCategories.OPEN_NEW_PROJECT) {
			@Override
			protected void changeDefaultSettings(MutableJobSettings settings) {
				settings.enableVisualPassthrough();
			};

			@Override
			protected Void execute(DoubleConsumer progressReceiver) {
				menu.updateMenus(false, true);
				matcher.init(newConfig);
				return null;
			}
		};
		job.addCompletionListener((result, error) -> Platform.runLater(() -> {
			if (newConfig.getMappingsPathA() != null) {
				Path mappingsPath = newConfig.getMappingsPathA();

				try {
					List<String> namespaces = MappingReader.getNamespaces(mappingsPath, null);
					Mappings.load(mappingsPath, null,
							namespaces.get(0), namespaces.get(1),
							MappingField.PLAIN, MappingField.MAPPED,
							env.getEnvA(), true);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (newConfig.getMappingsPathB() != null) {
				Path mappingsPath = newConfig.getMappingsPathB();

				try {
					List<String> namespaces = MappingReader.getNamespaces(mappingsPath, null);
					Mappings.load(mappingsPath, null,
							namespaces.get(0), namespaces.get(1),
							MappingField.PLAIN, MappingField.MAPPED,
							env.getEnvB(), true);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			onProjectChange();
			menu.updateMenus(false, false);
		}));
		job.run();
	}

	public ClassEnvironment getEnv() {
		return env;
	}

	public Matcher getMatcher() {
		return matcher;
	}

	public Scene getScene() {
		return scene;
	}

	public void addListeningComponent(IGuiComponent component) {
		components.add(component);
	}

	public MainMenuBar getMenu() {
		return menu;
	}

	public MatchPaneSrc getSrcPane() {
		return srcPane;
	}

	public MatchPaneDst getDstPane() {
		return dstPane;
	}

	public BottomPane getBottomPane() {
		return bottomPane;
	}

	public SortKey getSortKey() {
		return sortKey;
	}

	public void setSortKey(SortKey sortKey) {
		if (sortKey == null) throw new NullPointerException("null sort key");
		if (this.sortKey == sortKey) return;

		this.sortKey = sortKey;

		for (IGuiComponent c : components) {
			c.onViewChange(ViewChangeCause.SORTING_CHANGED);
		}
	}

	public boolean isSortMatchesAlphabetically() {
		return sortMatchesAlphabetically;
	}

	public void setSortMatchesAlphabetically(boolean value) {
		if (this.sortMatchesAlphabetically == value) return;

		this.sortMatchesAlphabetically = value;

		for (IGuiComponent c : components) {
			c.onViewChange(ViewChangeCause.SORTING_CHANGED);
		}
	}

	public boolean isUseClassTreeView() {
		return useClassTreeView;
	}

	public void setUseClassTreeView(boolean value) {
		if (this.useClassTreeView == value) return;

		this.useClassTreeView = value;

		for (IGuiComponent c : components) {
			c.onViewChange(ViewChangeCause.CLASS_TREE_VIEW_TOGGLED);
		}
	}

	public boolean isShowNonInputs() {
		return showNonInputs;
	}

	public void setShowNonInputs(boolean showNonInputs) {
		if (this.showNonInputs == showNonInputs) return;

		this.showNonInputs = showNonInputs;

		for (IGuiComponent c : components) {
			c.onViewChange(ViewChangeCause.DISPLAY_CLASSES_CHANGED);
		}
	}

	public boolean isHideUnmappedA() {
		return hideUnmappedA;
	}

	public void setHideUnmappedA(boolean hideUnmappedA) {
		if (this.hideUnmappedA == hideUnmappedA) return;

		this.hideUnmappedA = hideUnmappedA;

		for (IGuiComponent c : components) {
			c.onViewChange(ViewChangeCause.DISPLAY_CLASSES_CHANGED);
		}
	}

	public void updateCss() {
		if (lastSwitchedToTheme != null) {
			scene.getStylesheets().removeAll(lastSwitchedToTheme.getUrl().toExternalForm());
		}

		lastSwitchedToTheme = Config.getTheme();
		scene.getStylesheets().add(lastSwitchedToTheme.getUrl().toExternalForm());

		for (IGuiComponent c : components) {
			c.onViewChange(ViewChangeCause.THEME_CHANGED);
		}
	}

	public boolean isUseDiffColors() {
		return useDiffColors;
	}

	public void setUseDiffColors(boolean useDiffColors) {
		if (this.useDiffColors == useDiffColors) return;

		this.useDiffColors = useDiffColors;

		for (IGuiComponent c : components) {
			c.onViewChange(ViewChangeCause.DIFF_COLORS_TOGGLED);
		}
	}

	public NameType getNameType() {
		return nameType;
	}

	public void setNameType(NameType value) {
		if (this.nameType == value) return;

		this.nameType = value;

		for (IGuiComponent c : components) {
			c.onViewChange(ViewChangeCause.NAME_TYPE_CHANGED);
		}
	}

	public BuiltinDecompiler getDecompiler() {
		return decompiler;
	}

	public void setDecompiler(BuiltinDecompiler value) {
		if (this.decompiler == value) return;

		this.decompiler = value;

		for (IGuiComponent c : components) {
			c.onViewChange(ViewChangeCause.DECOMPILER_CHANGED);
		}
	}

	public void onProjectChange() {
		for (IGuiComponent c : components) {
			c.onProjectChange();
		}
	}

	public void onMappingChange() {
		for (IGuiComponent c : components) {
			c.onMappingChange();
		}
	}

	public void onMatchChange(Set<MatchType> types) {
		for (IGuiComponent c : components) {
			c.onMatchChange(types);
		}
	}

	public void onJobManagerEvent(Job<?> job, JobManagerEvent event) {
		switch (event) {
		case JOB_STARTED:
			activeJobs.add(job);
			job.addProgressListener((progress) -> Platform.runLater(() -> onProgressChange(progress)));
			break;
		case JOB_FINISHED:
			activeJobs.remove(job);
			break;
		}

		updateProgressPane();
	}

	private void updateProgressPane() {
		ProgressBar progressBar = bottomPane.getProgressBar();
		Label jobLabel = bottomPane.getJobLabel();

		if (activeJobs.size() == 0) {
			jobLabel.setText("");
			progressBar.setVisible(false);
			progressBar.setProgress(0);
		} else {
			progressBar.setVisible(true);

			for (Job<?> job : activeJobs) {
				if (job.getProgress() <= 0) {
					progressBar.setProgress(-1);
					break;
				} else if (progressBar.getProgress() < 0) {
					progressBar.setProgress(job.getProgress() / activeJobs.size());
				} else {
					progressBar.setProgress(progressBar.getProgress() + (job.getProgress() / activeJobs.size()));
				}
			}

			if (activeJobs.size() == 1) {
				jobLabel.setText(activeJobs.get(0).getId());
				// progressBar.setProgress(activeJobs.get(0).getProgress());
			} else {
				jobLabel.setText(activeJobs.size() + " tasks running");
				StringBuilder tooltipText = new StringBuilder();

				for (Job<?> job : activeJobs) {
					tooltipText.append(job.getId() + "\n");
				}

				jobLabel.setTooltip(new Tooltip(tooltipText.toString()));

				// if (progressBar.getProgress() > 0) {
				// 	progressBar.setProgress(progressBar.getProgress() * (activeJobs.size() - 1) / activeJobs.size());
				// }
			}

			progressBar.setTooltip(new Tooltip(""+progressBar.getProgress()));
		}
	}

	private void onProgressChange(double progress) {
		if (activeJobs.size() == 0) return;

		ProgressBar progressBar = bottomPane.getProgressBar();
		// bottomPane.getJobLabel().setText(bottomPane.getJobLabel().getText());

		// progressBar.setProgress(progressBar.getProgress() + progress / activeJobs.size());
		bottomPane.getJobLabel().setText(String.format("%s (%.0f%%)",
				activeJobs.get(0).getId(), progress * 100));

		progressBar.setProgress(progress / activeJobs.size());
	}

	public void showAlert(AlertType type, String title, String headerText, String text) {
		Alert alert = new Alert(type, text);

		alert.setTitle(title);
		alert.setHeaderText(headerText);

		Platform.runLater(() -> alert.getDialogPane().getScene().getWindow().sizeToScene()); // work around linux display bug  (JDK-8193502)

		alert.showAndWait();
	}

	public boolean requestConfirmation(String title, String headerText, String text) {
		Alert alert = new Alert(AlertType.CONFIRMATION, text);

		alert.setTitle(title);
		alert.setHeaderText(headerText);

		Platform.runLater(() -> alert.getDialogPane().getScene().getWindow().sizeToScene()); // work around linux display bug (JDK-8193502)

		return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
	}

	public static SelectedFile requestFile(String title, Window parent, List<ExtensionFilter> extensionFilters, boolean isOpen) {
		FileChooser fileChooser = setupFileChooser(title, extensionFilters);

		File file = isOpen ? fileChooser.showOpenDialog(parent) : fileChooser.showSaveDialog(parent);
		if (file == null) return null;

		lastChooserFile = file.getParentFile();

		return new SelectedFile(file.toPath(), fileChooser.getSelectedExtensionFilter());
	}

	public static List<SelectedFile> requestFiles(String title, Window parent, List<ExtensionFilter> extensionFilters) {
		FileChooser fileChooser = setupFileChooser(title, extensionFilters);

		List<File> file = fileChooser.showOpenMultipleDialog(parent);
		if (file == null || file.isEmpty()) return Collections.emptyList();

		lastChooserFile = file.get(0).getParentFile();

		return file.stream().map(file1 -> new SelectedFile(file1.toPath(), fileChooser.getSelectedExtensionFilter())).collect(Collectors.toList());
	}

	private static FileChooser setupFileChooser(String title, List<ExtensionFilter> extensionFilters) {
		FileChooser fileChooser = new FileChooser();

		fileChooser.setTitle(title);
		fileChooser.getExtensionFilters().addAll(extensionFilters);

		while (lastChooserFile != null && !lastChooserFile.isDirectory()) {
			lastChooserFile = lastChooserFile.getParentFile();
		}

		if (lastChooserFile != null) {
			fileChooser.setInitialDirectory(lastChooserFile);
		}

		return fileChooser;
	}

	public static class SelectedFile {
		SelectedFile(Path path, ExtensionFilter filter) {
			this.path = path;
			this.filter = filter;
		}

		public final Path path;
		public final ExtensionFilter filter;
	}

	public static Path requestDir(String title, Window parent) {
		DirectoryChooser fileChooser = new DirectoryChooser();

		fileChooser.setTitle(title);

		while (lastChooserFile != null && !lastChooserFile.isDirectory()) {
			lastChooserFile = lastChooserFile.getParentFile();
		}

		if (lastChooserFile != null) fileChooser.setInitialDirectory(lastChooserFile);

		File file = fileChooser.showDialog(parent);
		if (file == null) return null;

		lastChooserFile = file;

		return file.toPath();
	}

	public enum SortKey {
		Name, MappedName, MatchStatus, Similarity;
	}

	public static final List<Consumer<Gui>> loadListeners = new ArrayList<>();

	private ClassEnvironment env;
	private Matcher matcher;

	private Scene scene;
	private final Collection<IGuiComponent> components = new ArrayList<>();

	private MainMenuBar menu;
	private MatchPaneSrc srcPane;
	private MatchPaneDst dstPane;
	private BottomPane bottomPane;

	private List<Job<?>> activeJobs = new ArrayList<>();

	private SortKey sortKey = SortKey.Name;
	private boolean sortMatchesAlphabetically;
	private boolean useClassTreeView;
	private boolean showNonInputs;
	private boolean hideUnmappedA;
	private boolean useDiffColors;
	private Theme lastSwitchedToTheme;

	private NameType nameType = NameType.MAPPED_PLAIN;
	private BuiltinDecompiler decompiler = BuiltinDecompiler.CFR;

	private static File lastChooserFile;
}
