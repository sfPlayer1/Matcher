package matcher.gui;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import matcher.Matcher;
import matcher.NameType;
import matcher.gui.menu.MainMenuBar;
import matcher.srcprocess.BuiltinDecompiler;
import matcher.type.ClassEnvironment;
import matcher.type.MatchType;

public class Gui extends Application {
	@Override
	public void start(Stage stage) {
		Matcher.init();

		env = new ClassEnvironment();
		matcher = new Matcher(env);

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

		stage.setScene(scene);
		stage.setTitle("Matcher");
		stage.show();
	}

	@Override
	public void stop() throws Exception {
		threadPool.shutdown();
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
			c.onViewChange();
		}
	}

	public boolean isSortMatchesAlphabetically() {
		return sortMatchesAlphabetically;
	}

	public void setSortMatchesAlphabetically(boolean value) {
		if (this.sortMatchesAlphabetically == value) return;

		this.sortMatchesAlphabetically = value;

		for (IGuiComponent c : components) {
			c.onViewChange();
		}
	}

	public boolean isUseClassTreeView() {
		return useClassTreeView;
	}

	public void setUseClassTreeView(boolean value) {
		if (this.useClassTreeView == value) return;

		this.useClassTreeView = value;

		for (IGuiComponent c : components) {
			c.onViewChange();
		}
	}

	public boolean isShowNonInputs() {
		return showNonInputs;
	}

	public void setShowNonInputs(boolean showNonInputs) {
		if (this.showNonInputs == showNonInputs) return;

		this.showNonInputs = showNonInputs;

		for (IGuiComponent c : components) {
			c.onViewChange();
		}
	}

	public boolean isUseDiffColors() {
		return useDiffColors;
	}

	public void setUseDiffColors(boolean useDiffColors) {
		if (this.useDiffColors == useDiffColors) return;

		this.useDiffColors = useDiffColors;

		for (IGuiComponent c : components) {
			c.onViewChange();
		}
	}

	public NameType getNameType() {
		return nameType;
	}

	public void setNameType(NameType value) {
		if (this.nameType == value) return;

		this.nameType = value;

		for (IGuiComponent c : components) {
			c.onViewChange();
		}
	}


	public BuiltinDecompiler getDecompiler() {
		return decompiler;
	}

	public void setDecompiler(BuiltinDecompiler value) {
		if (this.decompiler == value) return;

		this.decompiler = value;

		for (IGuiComponent c : components) {
			c.onViewChange();
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

	public static <T> CompletableFuture<T> runAsyncTask(Callable<T> task) {
		Task<T> jfxTask = new Task<T>() {
			@Override
			protected T call() throws Exception {
				return task.call();
			}
		};

		CompletableFuture<T> ret = new CompletableFuture<T>();

		jfxTask.setOnSucceeded(event -> ret.complete(jfxTask.getValue()));
		jfxTask.setOnFailed(event -> ret.completeExceptionally(jfxTask.getException()));
		jfxTask.setOnCancelled(event -> ret.cancel(false));

		threadPool.execute(jfxTask);

		return ret;
	}

	public void runProgressTask(String labelText, Consumer<DoubleConsumer> task, Runnable onSuccess, Consumer<Throwable> onError) {
		Stage stage = new Stage(StageStyle.UTILITY);
		stage.initOwner(this.scene.getWindow());
		VBox pane = new VBox(GuiConstants.padding);

		stage.setScene(new Scene(pane));
		stage.initModality(Modality.APPLICATION_MODAL);
		stage.setOnCloseRequest(event -> event.consume());
		stage.setResizable(false);
		stage.setTitle("Operation progress");

		pane.setPadding(new Insets(GuiConstants.padding));

		pane.getChildren().add(new Label(labelText));

		ProgressBar progress = new ProgressBar(0);
		progress.setPrefWidth(400);
		pane.getChildren().add(progress);

		stage.show();

		Task<Void> jfxTask = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				task.accept(cProgress -> Platform.runLater(() -> progress.setProgress(cProgress)));

				return null;
			}
		};

		jfxTask.setOnSucceeded(event -> {
			onSuccess.run();
			stage.hide();
		});

		jfxTask.setOnFailed(event -> {
			onError.accept(jfxTask.getException());
			stage.hide();
		});

		threadPool.execute(jfxTask);
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

		if (lastChooserFile != null)
			fileChooser.setInitialDirectory(lastChooserFile);

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

	private static final ExecutorService threadPool = Executors.newCachedThreadPool();

	private ClassEnvironment env;
	private Matcher matcher;

	private Scene scene;
	private final Collection<IGuiComponent> components = new ArrayList<>();

	private MainMenuBar menu;
	private MatchPaneSrc srcPane;
	private MatchPaneDst dstPane;
	private BottomPane bottomPane;

	private SortKey sortKey = SortKey.Name;
	private boolean sortMatchesAlphabetically;
	private boolean useClassTreeView;
	private boolean showNonInputs;
	private boolean useDiffColors;

	private NameType nameType = NameType.MAPPED_PLAIN;
	private BuiltinDecompiler decompiler = BuiltinDecompiler.CFR;

	private static File lastChooserFile;
}
