package matcher.gui;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

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
import matcher.gui.menu.MainMenuBar;
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

		MainMenuBar menu = new MainMenuBar(this);
		components.add(menu);
		border.add(menu, 0, 0, 2, 1);

		MatchPaneSrc srcPane = new MatchPaneSrc(this);
		components.add(srcPane);
		border.add(srcPane, 0, 1);

		MatchPaneDst dstPane = new MatchPaneDst(this, srcPane);
		components.add(dstPane);
		border.add(dstPane, 1, 1);

		BottomPane bottomPane = new BottomPane(this, srcPane, dstPane);
		components.add(bottomPane);
		border.add(bottomPane, 0, 2, 2, 1);

		scene = new Scene(border);
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

	public void setSortMatchesAlphabetically(boolean sortMatchesAlphabetically) {
		if (this.sortMatchesAlphabetically == sortMatchesAlphabetically) return;

		this.sortMatchesAlphabetically = sortMatchesAlphabetically;

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
		Alert alert = new Alert(type);

		alert.setTitle(title);
		alert.setHeaderText(headerText);
		alert.setContentText(text);

		alert.showAndWait();
	}

	public boolean requestConfirmation(String title, String headerText, String text) {
		Alert alert = new Alert(AlertType.CONFIRMATION);

		alert.setTitle(title);
		alert.setHeaderText(headerText);
		alert.setContentText(text);

		return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
	}

	public static Path requestFile(String title, Window parent, List<ExtensionFilter> extensionFilters, boolean isOpen) {
		FileChooser fileChooser = new FileChooser();

		fileChooser.setTitle(title);
		fileChooser.getExtensionFilters().addAll(extensionFilters);
		if (lastChooserFile != null) fileChooser.setInitialDirectory(lastChooserFile);

		File file = isOpen ? fileChooser.showOpenDialog(parent) : fileChooser.showSaveDialog(parent);
		if (file == null) return null;

		lastChooserFile = file.getParentFile();

		return file.toPath();
	}

	public static Path requestDir(String title, Window parent) {
		DirectoryChooser fileChooser = new DirectoryChooser();

		fileChooser.setTitle(title);
		if (lastChooserFile != null) fileChooser.setInitialDirectory(lastChooserFile);

		File file = fileChooser.showDialog(parent);
		if (file == null) return null;

		lastChooserFile = file;

		return file.toPath();
	}

	public static enum SortKey {
		Name, MappedName, MatchStatus;
	}

	private static final ExecutorService threadPool = Executors.newCachedThreadPool();

	private ClassEnvironment env;
	private Matcher matcher;

	private Scene scene;
	private final Collection<IGuiComponent> components = new ArrayList<>();

	private SortKey sortKey = SortKey.Name;
	private boolean sortMatchesAlphabetically;
	private boolean showNonInputs;

	private static File lastChooserFile;
}
