package matcher;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import org.objectweb.asm.util.TraceClassVisitor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Callback;
import matcher.Matcher.MatchingStatus;
import matcher.classifier.ClassClassifier;
import matcher.classifier.ClassifierResult;
import matcher.classifier.FieldClassifier;
import matcher.classifier.MethodClassifier;
import matcher.classifier.RankResult;
import matcher.mapping.MappingFormat;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;

public class Gui extends Application {
	@Override
	public void start(Stage stage) {
		matcher = new Matcher();

		GridPane border = new GridPane();

		ColumnConstraints colConstraint = new ColumnConstraints();
		colConstraint.setPercentWidth(50);
		border.getColumnConstraints().addAll(colConstraint, colConstraint);

		RowConstraints defaultRowConstraints = new RowConstraints();
		RowConstraints contentRowConstraints = new RowConstraints();
		contentRowConstraints.setVgrow(Priority.ALWAYS);
		border.getRowConstraints().addAll(defaultRowConstraints, contentRowConstraints, defaultRowConstraints);

		border.add(createMenuBar(), 0, 0, 2, 1);
		border.add(createLeftPane(), 0, 1);
		border.add(createRightPane(), 1, 1);
		border.add(createBottomRow(), 0, 2, 2, 1);

		scene = new Scene(border);
		stage.setScene(scene);
		stage.setTitle("Matcher");
		stage.show();
	}

	@Override
	public void stop() throws Exception {
		threadPool.shutdown();
	}

	private MenuBar createMenuBar() {
		MenuBar ret = new MenuBar();

		// File menu

		Menu menu = new Menu("File");
		ret.getMenus().add(menu);

		MenuItem menuItem = new MenuItem("New project");
		menu.getItems().add(menuItem);
		menuItem.setOnAction(event -> newProject());

		menu.getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Load mappings");
		menu.getItems().add(menuItem);
		menuItem.setOnAction(event -> loadMappings());

		menuItem = new MenuItem("Save mappings");
		menu.getItems().add(menuItem);
		menuItem.setOnAction(event -> saveMappings());

		menuItem = new MenuItem("Clear mappings");
		menu.getItems().add(menuItem);
		menuItem.setOnAction(event -> matcher.clearMappings());

		menu.getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Load matches");
		menu.getItems().add(menuItem);
		menuItem.setOnAction(event -> loadMatches());

		menuItem = new MenuItem("Save matches");
		menu.getItems().add(menuItem);
		menuItem.setOnAction(event -> saveMatches());

		menu.getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Exit");
		menu.getItems().add(menuItem);
		menuItem.setOnAction(event -> Platform.exit());

		// Mapping menu

		menu = new Menu("Matching");
		ret.getMenus().add(menu);

		menuItem = new MenuItem("Auto match all");
		menu.getItems().add(menuItem);
		menuItem.setOnAction(event -> runProgressTask(
				"Auto matching...",
				progressReceiver -> {
					if (matcher.autoMatchClasses(absClassAutoMatchThreshold, relClassAutoMatchThreshold, progressReceiver)) {
						matcher.autoMatchClasses(absClassAutoMatchThreshold, relClassAutoMatchThreshold, progressReceiver);
					}

					boolean matchedAny;

					do {
						matchedAny = matcher.autoMatchMethods(absMethodAutoMatchThreshold, relMethodAutoMatchThreshold, progressReceiver);
						matchedAny |= matcher.autoMatchFields(absFieldAutoMatchThreshold, relFieldAutoMatchThreshold, progressReceiver);
						matchedAny |= matcher.autoMatchClasses(absClassAutoMatchThreshold, relClassAutoMatchThreshold, progressReceiver);
					} while (matchedAny);
				},
				() -> {
					invokeChangeListeners(classMatchListeners);
					invokeChangeListeners(memberMatchListeners);
				},
				Throwable::printStackTrace));

		menu.getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Auto class match");
		menu.getItems().add(menuItem);
		menuItem.setOnAction(event -> runProgressTask(
				"Auto matching classes...",
				progressReceiver -> matcher.autoMatchClasses(absClassAutoMatchThreshold, relClassAutoMatchThreshold, progressReceiver),
				() -> invokeChangeListeners(classMatchListeners),
				Throwable::printStackTrace));

		menuItem = new MenuItem("Auto method match");
		menu.getItems().add(menuItem);
		menuItem.setOnAction(event -> runProgressTask(
				"Auto matching methods...",
				progressReceiver -> matcher.autoMatchMethods(absMethodAutoMatchThreshold, relMethodAutoMatchThreshold, progressReceiver),
				() -> invokeChangeListeners(memberMatchListeners),
				Throwable::printStackTrace));

		menuItem = new MenuItem("Auto field match");
		menu.getItems().add(menuItem);
		menuItem.setOnAction(event -> runProgressTask(
				"Auto matching fields...",
				progressReceiver -> matcher.autoMatchFields(absFieldAutoMatchThreshold, relFieldAutoMatchThreshold, progressReceiver),
				() -> invokeChangeListeners(memberMatchListeners),
				Throwable::printStackTrace));

		menu.getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Status");
		menu.getItems().add(menuItem);
		menuItem.setOnAction(event -> showMatchingStatus());

		return ret;
	}

	private void newProject() {
		ProjectConfig config = ProjectConfig.getLast();

		Dialog<ProjectConfig> dialog = new Dialog<>();
		//dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(true);
		dialog.setTitle("Project configuration");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		GridPane grid = new GridPane();
		dialog.getDialogPane().setContent(grid);

		grid.setHgap(4 * padding);
		grid.setVgap(4 * padding);

		ColumnConstraints colConstraint = new ColumnConstraints();
		colConstraint.setPercentWidth(50);
		grid.getColumnConstraints().addAll(colConstraint, colConstraint);

		RowConstraints rowConstraint = new RowConstraints();
		rowConstraint.setPercentHeight(50);
		grid.getRowConstraints().addAll(rowConstraint, rowConstraint);

		Window window = dialog.getOwner();
		ObservableList<Path> pathsA = FXCollections.observableList(config.pathsA);
		ObservableList<Path> pathsB = FXCollections.observableList(config.pathsB);
		ObservableList<Path> sharedClassPath = FXCollections.observableList(config.sharedClassPath);

		grid.add(createFilesSelectionPane("Inputs A", pathsA, window), 0, 0);
		grid.add(createFilesSelectionPane("Inputs B", pathsB, window), 0, 1);
		grid.add(createFilesSelectionPane("Shared class path", sharedClassPath, window), 1, 0, 1, 2);

		Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);

		ListChangeListener<Path> listChangeListener = change -> okButton.setDisable(!config.isValid());

		pathsA.addListener(listChangeListener);
		pathsB.addListener(listChangeListener);
		sharedClassPath.addListener(listChangeListener);
		listChangeListener.onChanged(null);

		dialog.setResultConverter(button -> button == ButtonType.OK ? config : null);

		dialog.showAndWait().ifPresent(newConfig -> {
			if (!newConfig.isValid()) return;

			newConfig.saveAsLast();
			clearProject();

			runProgressTask("Initializing files...", progressReceiver -> matcher.init(newConfig, progressReceiver), () -> {
				invokeChangeListeners(projectChangeListeners);
			}, Throwable::printStackTrace);
		});
	}

	private Node createFilesSelectionPane(String name, ObservableList<Path> entries, Window window) {
		VBox ret = new VBox(padding);

		ret.getChildren().add(new Label(name+":"));

		ListView<Path> list = new ListView<>(entries);
		ret.getChildren().add(list);

		list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		list.setPrefWidth(320);
		list.setPrefHeight(120);
		VBox.setVgrow(list, Priority.ALWAYS);

		HBox footer = new HBox(padding);
		ret.getChildren().add(footer);

		footer.setAlignment(Pos.CENTER_RIGHT);

		Button button = new Button("add");
		footer.getChildren().add(button);
		button.setOnAction(event -> {
			Path path = requestLoadPath("Select file to add", window, getInputLoadExtensionFilters());
			if (path != null && !list.getItems().contains(path)) list.getItems().add(path);
		});

		Button removeButton = new Button("remove");
		footer.getChildren().add(removeButton);
		removeButton.setOnAction(event -> {
			Set<Path> selected = new HashSet<>(list.getSelectionModel().getSelectedItems());
			list.getItems().removeIf(selected::contains);
		});

		Button upButton = new Button("up");
		footer.getChildren().add(upButton);
		upButton.setOnAction(event -> {
			MultipleSelectionModel<Path> selection = list.getSelectionModel();
			List<Integer> selected = new ArrayList<>(selection.getSelectedIndices());

			list.getSelectionModel().clearSelection();

			for (int idx : selected) {
				if (idx > 0 && !selection.isSelected(idx - 1)) {
					Path e = list.getItems().remove(idx);
					list.getItems().add(idx - 1, e);
					selection.select(idx - 1);
				} else {
					selection.select(idx);
				}
			}
		});

		Button downButton = new Button("down");
		footer.getChildren().add(downButton);
		downButton.setOnAction(event -> {
			MultipleSelectionModel<Path> selection = list.getSelectionModel();
			List<Integer> selected = new ArrayList<>(selection.getSelectedIndices());
			Collections.reverse(selected);
			list.getSelectionModel().clearSelection();

			for (int idx : selected) {
				if (idx < list.getItems().size() - 1 && !selection.isSelected(idx + 1)) {
					Path e = list.getItems().remove(idx);
					list.getItems().add(idx + 1, e);
					selection.select(idx + 1);
				} else {
					selection.select(idx);
				}
			}
		});

		ListChangeListener<Path> changeListener = change -> {
			List<Integer> selectedIndices = list.getSelectionModel().getSelectedIndices();

			boolean disableUp = true;
			boolean disableDown = true;

			if (selectedIndices.isEmpty()) {
				removeButton.setDisable(true);
			} else {
				removeButton.setDisable(false);
				Set<Integer> selected = new HashSet<>(selectedIndices);

				for (int idx : selectedIndices) {
					if (disableUp && idx > 0 && !selected.contains(idx - 1)) {
						disableUp = false;
						if (!disableDown) break;
					}

					if (disableDown && idx < list.getItems().size() - 1 && !selected.contains(idx + 1)) {
						disableDown = false;
						if (!disableUp) break;
					}
				}
			}

			upButton.setDisable(disableUp);
			downButton.setDisable(disableDown);
		};

		list.getSelectionModel().getSelectedItems().addListener(changeListener);
		changeListener.onChanged(null);

		return ret;
	}

	private static ExtensionFilter[] getInputLoadExtensionFilters() {
		return new ExtensionFilter[] { new FileChooser.ExtensionFilter("Java archive", "*.jar") };
	}

	private static Path requestLoadPath(String title, Window parent, ExtensionFilter... extensionFilters) {
		FileChooser fileChooser = new FileChooser();

		fileChooser.setTitle(title);
		fileChooser.getExtensionFilters().addAll(extensionFilters);

		File file = fileChooser.showOpenDialog(parent);
		if (file == null) return null;

		return file.toPath();
	}

	private void clearProject() {
		matcher.reset();
		invokeChangeListeners(projectChangeListeners);
	}

	private void loadMappings() {
		Path file = requestLoadPath("Select mapping file", scene.getWindow(), getMappingLoadExtensionFilters());
		if (file == null) return;

		try {
			matcher.readMappings(file);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

		clsListA.refresh();
		memberListA.refresh();
		clsListB.refresh();
		memberListB.refresh();
	}

	private static ExtensionFilter[] getMappingLoadExtensionFilters() {
		MappingFormat[] formats = MappingFormat.values();
		ExtensionFilter[] ret = new ExtensionFilter[formats.length + 1];
		String[] supportedExtensions = new String[formats.length];

		for (int i = 0; i < formats.length; i++) {
			supportedExtensions[i] = formats[i].getGlobPattern();
		}

		ret[0] = new FileChooser.ExtensionFilter("All supported", supportedExtensions);

		for (int i = 0; i < formats.length; i++) {
			MappingFormat format = formats[i];
			ret[i + 1] = new FileChooser.ExtensionFilter(format.name, format.getGlobPattern());
		}

		return ret;
	}

	private void saveMappings() {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Save mapping file");

		for (MappingFormat format : MappingFormat.values()) {
			fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(format.name, "*."+format.fileExt));
		}

		File file = fileChooser.showSaveDialog(scene.getWindow());
		if (file == null) return;

		Path path = file.toPath();
		MappingFormat format = getFormat(path);

		if (format == null) {
			format = getFormat(fileChooser.getSelectedExtensionFilter().getDescription());
			path = path.resolveSibling(path.getFileName().toString()+"."+format.fileExt);
		}

		try {
			if (Files.isDirectory(path)) {
				showAlert(AlertType.ERROR, "Save error", "Invalid file selection", "The selected file is a directory.");
			} else if (Files.exists(path)) {
				Files.deleteIfExists(path);
			}

			if (!matcher.saveMappings(path, format)) {
				showAlert(AlertType.WARNING, "Mapping save warning", "No mappings to save", "There are currently no names mapped to matched classes, so saving was aborted.");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}

	private static MappingFormat getFormat(Path file) {
		String name = file.getFileName().toString().toLowerCase(Locale.ENGLISH);

		for (MappingFormat format : MappingFormat.values()) {
			if (name.endsWith(format.fileExt)
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
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select matches file");
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Matches", "*.match"));
		File file = fileChooser.showOpenDialog(scene.getWindow());
		if (file == null) return;

		try {
			matcher.readMatches(file.toPath());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

		clsListA.refresh();
		memberListA.refresh();
	}

	private void saveMatches() {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Save matches file");
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Matches", "*.match"));

		File file = fileChooser.showSaveDialog(scene.getWindow());
		if (file == null) return;

		Path path = file.toPath();

		if (!path.getFileName().toString().toLowerCase(Locale.ENGLISH).endsWith(".match")) {
			path = path.resolveSibling(path.getFileName().toString()+".match");
		}

		try {
			if (Files.isDirectory(path)) {
				showAlert(AlertType.ERROR, "Save error", "Invalid file selection", "The selected file is a directory.");
			} else if (Files.exists(path)) {
				Files.deleteIfExists(path);
			}

			if (!matcher.saveMatches(path)) {
				showAlert(AlertType.WARNING, "Matches save warning", "No matches to save", "There are currently no matched classes, so saving was aborted.");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}

	private void showMatchingStatus() {
		MatchingStatus status = matcher.getStatus();

		showAlert(AlertType.INFORMATION, "Matching status", "Current matching status",
				String.format("Classes: %d / %d (%.2f%%)%nMethods: %d / %d (%.2f%%)%nFields: %d / %d (%.2f%%)",
						status.matchedClassCount, status.totalClassCount, (status.totalClassCount == 0 ? 0 : 100. * status.matchedClassCount / status.totalClassCount),
						status.matchedMethodCount, status.totalMethodCount, (status.totalMethodCount == 0 ? 0 : 100. * status.matchedMethodCount / status.totalMethodCount),
						status.matchedFieldCount, status.totalFieldCount, (status.totalFieldCount == 0 ? 0 : 100. * status.matchedFieldCount / status.totalFieldCount)));
	}

	private Node createLeftPane() {
		SplitPane horizontalPane = new SplitPane();

		SplitPane verticalPane = new SplitPane();
		horizontalPane.getItems().add(verticalPane);
		verticalPane.setOrientation(Orientation.VERTICAL);

		clsListA = new ListView<>();
		verticalPane.getItems().add(clsListA);

		clsListA.setCellFactory(list -> new ListCell<ClassInstance>() {
			@Override
			protected void updateItem(ClassInstance item, boolean empty) {
				super.updateItem(item, empty);

				if (empty || item == null) {
					setText(null);
					setStyle("");
				} else {
					String name = item.toString();
					String mappedName = item.getMappedName();

					if (mappedName == null || name.equals(mappedName)) {
						setText(name);
					} else {
						setText(name+" - "+mappedName);
					}

					if (item.getMatch() == null) {
						setStyle("-fx-text-fill: darkred;");
					} else {
						boolean allMatched = true;

						for (MethodInstance method : item.getMethods()) {
							if (method.getMatch() == null) {
								allMatched = false;
								break;
							}
						}

						if (allMatched) {
							for (FieldInstance field : item.getFields()) {
								if (field.getMatch() == null) {
									allMatched = false;
									break;
								}
							}
						}

						if (allMatched) {
							setStyle("-fx-text-fill: darkgreen;");
						} else {
							setStyle("-fx-text-fill: chocolate;");
						}
					}
				}
			}
		});

		projectChangeListeners.add(() -> clsListA.setItems(FXCollections.observableList(matcher.getClassesA())));
		classMatchListeners.add(() -> clsListA.refresh());
		memberMatchListeners.add(() -> clsListA.refresh());

		SplitPane.setResizableWithParent(verticalPane, false);
		horizontalPane.setDividerPosition(0, 0.25);

		leftContent = createContentNode(true);
		horizontalPane.getItems().add(leftContent.tabPane);

		memberListA = new ListView<>(FXCollections.observableList(new ArrayList<>()));
		verticalPane.getItems().add(memberListA);

		memberListA.setCellFactory(list -> new ListCell<MemberInstance<?>>() {
			@Override
			protected void updateItem(MemberInstance<?> item, boolean empty) {
				super.updateItem(item, empty);

				if (empty || item == null) {
					setText(null);
					setStyle("");
				} else {
					String name = item.getName();
					String mappedName = item.getMappedName();

					if (mappedName == null || name.equals(mappedName)) {
						setText(name);
					} else {
						setText(name+" - "+mappedName);
					}

					if (item.getMatch() == null) {
						setStyle("-fx-text-fill: darkred;");
					} else {
						setStyle("-fx-text-fill: darkgreen;");
					}
				}
			}
		});

		ChangeListener<MemberInstance<?>> changeListener = new MemberChangeListener(leftContent);

		memberListA.getSelectionModel().selectedItemProperty().addListener(changeListener);
		// update member match list after matching a class while having one of its members selected
		classMatchListeners.add(() -> {
			if (!memberListA.getSelectionModel().isEmpty()) {
				changeListener.changed(null, memberListA.getSelectionModel().getSelectedItem(), memberListA.getSelectionModel().getSelectedItem());
			}
		});
		// update member match list after matching a member
		memberMatchListeners.add(() -> {
			memberListA.refresh();
			changeListener.changed(null, memberListA.getSelectionModel().getSelectedItem(), memberListA.getSelectionModel().getSelectedItem());
		});

		verticalPane.setDividerPosition(0, 0.65);

		return horizontalPane;
	}

	private Node createRightPane() {
		SplitPane horizontalPane = new SplitPane();

		SplitPane verticalPane = new SplitPane();
		verticalPane.setOrientation(Orientation.VERTICAL);

		clsListB = new ListView<>();
		verticalPane.getItems().add(clsListB);

		clsListB.setCellFactory(list -> new ListCell<RankResult<ClassInstance>>() {
			@Override
			protected void updateItem(RankResult<ClassInstance> item, boolean empty) {
				super.updateItem(item, empty);

				if (empty || item == null) {
					setText(null);
				} else {
					setText(String.format("%.3f %s", item.getScore(), item.getSubject().toString()));
				}
			}
		});

		memberListB = new ListView<>();
		verticalPane.getItems().add(memberListB);

		memberListB.setCellFactory(list -> new ListCell<RankResult<MemberInstance<?>>>() {
			@Override
			protected void updateItem(RankResult<MemberInstance<?>> item, boolean empty) {
				super.updateItem(item, empty);

				if (empty || item == null) {
					setText(null);
				} else {
					setText(String.format("%.3f %s", item.getScore(), item.getSubject().getName()));
				}
			}
		});

		rightContent = createContentNode(false);
		horizontalPane.getItems().add(rightContent.tabPane);
		horizontalPane.getItems().add(verticalPane);

		SplitPane.setResizableWithParent(verticalPane, false);
		horizontalPane.setDividerPosition(0, 1 - 0.25);
		verticalPane.setDividerPosition(0, 0.65);

		return horizontalPane;
	}

	private ContentNode createContentNode(boolean leftSide) {
		TabPane tabPane = new TabPane();

		// source tab

		Tab tab = new Tab("source");
		tabPane.getTabs().add(tab);

		TextArea srcText = new TextArea("");
		tab.setContent(srcText);
		srcText.setEditable(false);

		// bytecode tab

		tab = new Tab("bytecode");
		tabPane.getTabs().add(tab);

		TextArea bcText = new TextArea("");
		tab.setContent(bcText);
		bcText.setEditable(false);

		// hierarchy tab

		TreeView<ClassInstance> classHierarchyTree;
		Set<ClassInstance> highLights;
		ListView<ClassInstance> ifaceList;

		if (showHierarchy) {
			tab = new Tab("hierarchy");
			tabPane.getTabs().add(tab);
			VBox vBox = new VBox();
			tab.setContent(vBox);
			classHierarchyTree = new TreeView<>();
			vBox.getChildren().add(classHierarchyTree);
			ifaceList = new ListView<>();
			vBox.getChildren().add(ifaceList);

			highLights = new HashSet<>();
			Callback<TreeView<ClassInstance>, TreeCell<ClassInstance>> cellFactory = tree -> new TreeCell<ClassInstance>() { // makes entries in highLights bold
				@Override
				protected void updateItem(ClassInstance item, boolean empty) {
					super.updateItem(item, empty);

					if (empty || item == null) {
						setText(null);
						setStyle("");
					} else {
						setText(item.toString());

						if (highLights.contains(item)) {
							setStyle("-fx-font-weight: bold;");
						} else {
							setStyle("");
						}
					}
				}
			};

			classHierarchyTree.setCellFactory(cellFactory);
		} else {
			classHierarchyTree = null;
			highLights = null;
			ifaceList = null;
		}

		// classification scores tab

		TableView<ClassifierResult<ClassInstance>> classClassifierTable;
		TableView<ClassifierResult<MemberInstance<?>>> memberClassifierTable;

		if (leftSide) {
			classClassifierTable = null;
			memberClassifierTable = null;
		} else { // right side
			tab = new Tab("class classifiers");
			tabPane.getTabs().add(tab);

			classClassifierTable = createClassifierTable();
			tab.setContent(classClassifierTable);

			tab = new Tab("member classifiers");
			tabPane.getTabs().add(tab);

			memberClassifierTable = createClassifierTable();
			tab.setContent(memberClassifierTable);
		}

		ContentNode contentNode = new ContentNode(leftSide, tabPane, srcText, bcText, classHierarchyTree, highLights, ifaceList, classClassifierTable, memberClassifierTable);

		ChangeListener<ClassInstance> listener = new ClassChangeListener(contentNode);

		if (leftSide) {
			clsListA.getSelectionModel().selectedItemProperty().addListener(listener);
			classMatchListeners.add(() -> listener.changed(null, clsListA.getSelectionModel().getSelectedItem(), clsListA.getSelectionModel().getSelectedItem()));
		} else {
			ChangeListener<RankResult<ClassInstance>> rankResultListener = adaptRankResultListener(listener);

			clsListB.getSelectionModel().selectedItemProperty().addListener(rankResultListener);
			memberListB.getSelectionModel().selectedItemProperty().addListener(adaptRankResultListener(new MemberChangeListener(contentNode)));
			classMatchListeners.add(() -> rankResultListener.changed(null, clsListB.getSelectionModel().getSelectedItem(), clsListB.getSelectionModel().getSelectedItem()));
		}

		return contentNode;
	}

	private static <T> TableView<ClassifierResult<T>> createClassifierTable() {
		TableView<ClassifierResult<T>> ret = new TableView<>();
		ret.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<ClassifierResult<T>, String> tab0 = new TableColumn<>("name");
		tab0.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getClassifier().getName()));
		ret.getColumns().add(tab0);

		TableColumn<ClassifierResult<T>, String> tab1 = new TableColumn<>("score");
		tab1.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(String.format("%.2f", data.getValue().getScore())));
		ret.getColumns().add(tab1);

		TableColumn<ClassifierResult<T>, Double> tab2 = new TableColumn<>("weight");
		tab2.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getClassifier().getWeight()));
		ret.getColumns().add(tab2);

		TableColumn<ClassifierResult<T>, String> tab3 = new TableColumn<>("w. score");
		tab3.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(String.format("%.2f", data.getValue().getScore() * data.getValue().getClassifier().getWeight())));
		ret.getColumns().add(tab3);

		ret.setItems(FXCollections.observableArrayList());

		return ret;
	}

	private static <T> ChangeListener<RankResult<T>> adaptRankResultListener(ChangeListener<T> listener) {
		return (observable, oldValue, newValue) -> listener.changed(null, null, newValue == null ? null : newValue.getSubject());
	}

	private Node createBottomRow() {
		StackPane ret = new StackPane();
		ret.setPadding(new Insets(padding));

		HBox center = new HBox(padding);
		ret.getChildren().add(center);
		StackPane.setAlignment(center, Pos.CENTER);
		center.setAlignment(Pos.CENTER);

		Button matchClassButton = new Button("match classes");
		center.getChildren().add(matchClassButton);

		matchClassButton.setOnAction(event -> {
			ClassInstance clsA = clsListA.getSelectionModel().getSelectedItem();
			RankResult<ClassInstance> resB = clsListB.getSelectionModel().getSelectedItem();

			if (clsA == null || resB == null) {
				return;
			}

			ClassInstance clsB = resB.getSubject();
			matcher.match(clsA, clsB);
			invokeChangeListeners(classMatchListeners);
		});

		matchClassButton.setDisable(true);

		clsListA.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
		matchClassButton.setDisable(newValue == null
		|| clsListB.getSelectionModel().isEmpty()
		|| newValue.getMatch() == clsListB.getSelectionModel().getSelectedItem().getSubject()));

		clsListB.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
		matchClassButton.setDisable(newValue == null
		|| clsListA.getSelectionModel().isEmpty()
		|| clsListA.getSelectionModel().getSelectedItem().getMatch() == newValue.getSubject()));

		Button matchMemberButton = new Button("match members");
		center.getChildren().add(matchMemberButton);

		matchMemberButton.setOnAction(event -> {
			MemberInstance<?> memberA = memberListA.getSelectionModel().getSelectedItem();
			RankResult<MemberInstance<?>> resB = memberListB.getSelectionModel().getSelectedItem();

			if (memberA == null || resB == null || memberA.getClass() != resB.getSubject().getClass()) {
				return;
			}

			MemberInstance<?> memberB = resB.getSubject();

			if (memberA instanceof MethodInstance) {
				matcher.match((MethodInstance) memberA, (MethodInstance) memberB);
			} else {
				matcher.match((FieldInstance) memberA, (FieldInstance) memberB);
			}

			invokeChangeListeners(memberMatchListeners);
		});

		matchMemberButton.setDisable(true);

		memberListA.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
		matchMemberButton.setDisable(newValue == null
		|| memberListB.getSelectionModel().isEmpty()
		|| newValue.getMatch() == memberListB.getSelectionModel().getSelectedItem().getSubject()));

		memberListB.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
		matchMemberButton.setDisable(newValue == null
		|| memberListA.getSelectionModel().isEmpty()
		|| memberListA.getSelectionModel().getSelectedItem().getMatch() == newValue.getSubject()));

		HBox right = new HBox(padding);
		ret.getChildren().add(right);
		StackPane.setAlignment(right, Pos.CENTER_RIGHT);
		right.setAlignment(Pos.CENTER_RIGHT);
		right.setPickOnBounds(false);

		Button unmatchClassButton = new Button("unmatch classes");
		right.getChildren().add(unmatchClassButton);

		unmatchClassButton.setOnAction(event -> {
			ClassInstance cls = clsListA.getSelectionModel().getSelectedItem();
			if (cls == null) return;

			matcher.unmatch(cls);
			invokeChangeListeners(classMatchListeners);
			invokeChangeListeners(memberMatchListeners);
		});

		unmatchClassButton.setDisable(true);

		clsListA.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
		unmatchClassButton.setDisable(newValue == null || newValue.getMatch() == null));

		classMatchListeners.add(() ->
		unmatchClassButton.setDisable(clsListA.getSelectionModel().isEmpty() || clsListA.getSelectionModel().getSelectedItem().getMatch() == null));

		Button unmatchMemberButton = new Button("unmatch members");
		right.getChildren().add(unmatchMemberButton);

		unmatchMemberButton.setOnAction(event -> {
			MemberInstance<?> member = memberListA.getSelectionModel().getSelectedItem();
			if (member == null) return;

			matcher.unmatch(member);
			invokeChangeListeners(memberMatchListeners);
		});

		unmatchMemberButton.setDisable(true);

		memberListA.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
		unmatchMemberButton.setDisable(newValue == null || newValue.getMatch() == null));

		memberMatchListeners.add(() ->
		unmatchMemberButton.setDisable(memberListA.getSelectionModel().isEmpty() || memberListA.getSelectionModel().getSelectedItem().getMatch() == null));

		return ret;
	}

	private static <T> CompletableFuture<T> runAsyncTask(Callable<T> task) {
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

	private void runProgressTask(String labelText, Consumer<DoubleConsumer> task, Runnable onSuccess, Consumer<Throwable> onError) {
		Stage stage = new Stage(StageStyle.UTILITY);
		stage.initOwner(this.scene.getWindow());
		VBox pane = new VBox(padding);

		stage.setScene(new Scene(pane));
		stage.initModality(Modality.APPLICATION_MODAL);
		stage.setOnCloseRequest(event -> event.consume());
		stage.setResizable(false);
		stage.setTitle("Operation progress");

		pane.setPadding(new Insets(padding));

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

	private static void decompile(byte[] clsData, URI file, Consumer<String> handler) {
		Task<String> task = new Task<String>() {
			@Override
			protected String call() throws Exception {
				String path = file.getRawSchemeSpecificPart();
				int sepPos = path.lastIndexOf('!');
				String jarPath = URLDecoder.decode(path.substring("file://".length(), sepPos), "UTF-8");
				String clsPath = URLDecoder.decode(path.substring(sepPos + 1), "UTF-8");

				Path tmpIn = null;
				Path tmpOut = null;

				try {
					tmpIn = Files.createTempFile("decmp-tmp", ".class");

					if (clsData == null) {
						synchronized (zipFsLock) { // avoid opening the same fs multiple times at once
							try (FileSystem fs = FileSystems.newFileSystem(file, Collections.emptyMap())) {
								Files.copy(fs.getPath(clsPath), tmpIn, StandardCopyOption.REPLACE_EXISTING);
							}
						}
					} else {
						Files.write(tmpIn, clsData);
					}

					tmpOut = Files.createTempFile("decmp-tmp", ".java");

					Process process = new ProcessBuilder("java", "-jar", "cfr_0_119.jar", tmpIn.toString(), "--silent"/*, "--extraclasspath", jarPath*/)
							.directory(Paths.get("extbin").toFile())
							.redirectOutput(tmpOut.toFile())
							.redirectError(Redirect.INHERIT)
							.start();

					try {
						process.waitFor();
					} catch (InterruptedException e) { }

					return new String(Files.readAllBytes(tmpOut), StandardCharsets.UTF_8);
				} finally {
					try {
						if (tmpIn != null) Files.deleteIfExists(tmpIn);
						if (tmpOut != null) Files.deleteIfExists(tmpOut);
					} catch (IOException e) { }
				}
			}
		};

		task.setOnSucceeded(event -> {
			try {
				handler.accept(task.get());
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		});

		task.setOnFailed(event -> {
			Throwable t = task.getException();

			if (t == null) {
				System.err.println("decompilation failed for unknown reason");
			} else {
				t.printStackTrace();
			}
		});

		threadPool.execute(task);
	}

	private static void showAlert(AlertType type, String title, String headerText, String text) {
		Alert alert = new Alert(type);

		alert.setTitle(title);
		alert.setHeaderText(headerText);
		alert.setContentText(text);

		alert.showAndWait();
	}

	private static boolean requestConfirmation(String title, String headerText, String text) {
		Alert alert = new Alert(AlertType.CONFIRMATION);

		alert.setTitle(title);
		alert.setHeaderText(headerText);
		alert.setContentText(text);

		return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
	}

	private static void invokeChangeListeners(Collection<Runnable> listeners) {
		listeners.forEach(Runnable::run);
	}

	private static class ContentNode {
		public ContentNode(boolean leftSide, TabPane tabPane,
				TextArea srcText, TextArea bcText,
				TreeView<ClassInstance> classHierarchyTree, Set<ClassInstance> highLights, ListView<ClassInstance> ifaceList,
				TableView<ClassifierResult<ClassInstance>> classClassifierTable, TableView<ClassifierResult<MemberInstance<?>>> memberClassifierTable) {
			this.leftSide = leftSide;
			this.tabPane = tabPane;
			this.srcText = srcText;
			this.bcText = bcText;
			this.classHierarchyTree = classHierarchyTree;
			this.highLights = highLights;
			this.ifaceList = ifaceList;
			this.classClassifierTable = classClassifierTable;
			this.memberClassifierTable = memberClassifierTable;
		}

		final boolean leftSide;
		final TabPane tabPane;
		final TextArea srcText;
		final TextArea bcText;
		final TreeView<ClassInstance> classHierarchyTree;
		final Set<ClassInstance> highLights;
		final ListView<ClassInstance> ifaceList;
		final TableView<ClassifierResult<ClassInstance>> classClassifierTable;
		final TableView<ClassifierResult<MemberInstance<?>>> memberClassifierTable;
	}

	private class ClassChangeListener implements ChangeListener<ClassInstance> {
		ClassChangeListener(ContentNode contentNode) {
			this.contentNode = contentNode;

			if (contentNode.leftSide) {
				projectChangeListeners.add(() -> cmpClasses = matcher.getClassesB());
			} else {
				memberMatchListeners.add(() -> {
					if (!clsListB.getSelectionModel().isEmpty()) {
						updateDecompText(clsListB.getSelectionModel().getSelectedItem().getSubject());
					}
				});
			}
		}

		@Override
		public void changed(ObservableValue<? extends ClassInstance> observable, ClassInstance oldValue, ClassInstance newValue) {
			// clear everything
			if (newValue != oldValue) {
				clearClassInfo();
			}

			final ClassInstance prevMatchSelection;

			if (contentNode.leftSide) {
				prevMatchSelection = newValue != oldValue || clsListB.getSelectionModel().isEmpty() ? null : clsListB.getSelectionModel().getSelectedItem().getSubject();

				if (newValue != oldValue) {
					memberListA.getItems().clear();
				}

				clsListB.getItems().clear();
				memberListB.getItems().clear();
			} else { // right side
				prevMatchSelection = null;

				contentNode.classClassifierTable.getItems().clear();
			}

			if (newValue == null) return;

			if (newValue != oldValue) {
				updateClassInfo(newValue);
			}

			if (contentNode.leftSide) {
				// update member list
				if (newValue != oldValue) {
					memberListA.getItems().setAll(newValue.getMethods());
					memberListA.getItems().addAll(newValue.getFields());

					memberListA.getItems().sort((a, b) -> {
						boolean aIsMethod = a instanceof MethodInstance;
						boolean bIsMethod = b instanceof MethodInstance;

						if (aIsMethod != bIsMethod) {
							return aIsMethod ? -1 : 1;
						} else {
							return a.getOrigName().compareTo(b.getOrigName());
						}
					});
				}

				// update matches list
				if (cmpClasses != null) {
					//clsListB.getItems().setAll(ClassClassifier.rank(cls, new ArrayList<>(cmpClasses)));
					runAsyncTask(() -> ClassClassifier.rank(newValue, cmpClasses.toArray(new ClassInstance[0]), matcher))
					.whenComplete((res, exc) -> {
						if (exc != null) {
							exc.printStackTrace();
						} else if (clsListA.getSelectionModel().getSelectedItem() == newValue) {
							clsListB.getItems().setAll(res);

							if (prevMatchSelection != null) { // reselect the previously selected entry
								for (int i = 0; i < clsListB.getItems().size(); i++) {
									if (clsListB.getItems().get(i).getSubject() == prevMatchSelection) {
										clsListB.getSelectionModel().select(i);
										break;
									}
								}
							}

							if (clsListB.getSelectionModel().isEmpty()) {
								clsListB.getSelectionModel().selectFirst();
							}
						}
					});
				}
			} else { // right side
				// update classifier score table
				contentNode.classClassifierTable.getItems().setAll(clsListB.getSelectionModel().getSelectedItem().getResults());
			}
		}

		private void clearClassInfo() {
			contentNode.srcText.setText("");
			contentNode.bcText.setText("");

			if (showHierarchy) {
				contentNode.highLights.clear();
				contentNode.classHierarchyTree.setRoot(null);
				contentNode.ifaceList.getItems().clear();
			}
		}

		private void updateClassInfo(ClassInstance cls) {
			// update bytecode view
			StringWriter writer = new StringWriter();

			try (PrintWriter pw = new PrintWriter(writer)) {
				cls.getAsmNode().accept(new TraceClassVisitor(pw));
			}

			contentNode.bcText.setText(writer.toString());

			// update decompiled source code view
			contentNode.srcText.setText("decompiling...");

			updateDecompText(cls);

			// update type hierarchy
			if (showHierarchy) {
				populateTypeHierarchy(cls);
			}
		}

		private void updateDecompText(ClassInstance cls) {
			double prevScroll = contentNode.srcText.getScrollTop();

			decompile(matcher.serializeClass(cls, contentNode.leftSide), cls.getUri(), result -> {
				ClassInstance currentValue;

				if (contentNode.leftSide) {
					currentValue = clsListA.getSelectionModel().getSelectedItem();
				} else {
					RankResult<ClassInstance> res = clsListB.getSelectionModel().getSelectedItem();
					currentValue = res == null ? null : res.getSubject();
				}

				if (currentValue == cls) { // still the correct list entry selected
					boolean scrollUnchanged = Math.abs(contentNode.srcText.getScrollTop() - prevScroll) < 1e-4;

					contentNode.srcText.setText(result);

					if (scrollUnchanged) {
						contentNode.srcText.setScrollTop(prevScroll);
					}
				}
			});
		}

		private void populateTypeHierarchy(ClassInstance cls) {
			List<ClassInstance> hierarchy = new ArrayList<>();
			ClassInstance cCls = cls;

			while (cCls != null) {
				hierarchy.add(cCls);
				cCls = cCls.getSuperClass();
			}

			contentNode.highLights.addAll(hierarchy);

			TreeItem<ClassInstance> parent = new TreeItem<>(hierarchy.get(hierarchy.size() - 1));
			contentNode.classHierarchyTree.setRoot(parent);

			if (hierarchy.size() > 1) {
				parent.setExpanded(true);
			}

			for (int i = hierarchy.size() - 1; i >= 1; i--) {
				ClassInstance parentCls = hierarchy.get(i);
				ClassInstance nextCls = hierarchy.get(i - 1);
				TreeItem<ClassInstance> next = null;

				List<ClassInstance> items = new ArrayList<>(parentCls.getChildClasses());
				items.sort(Comparator.comparing(ClassInstance::toString));

				for (int j = 0; j < items.size() && j < 10; j++) {
					ClassInstance child = items.get(j);

					TreeItem<ClassInstance> treeItem = new TreeItem<>(child);
					parent.getChildren().add(treeItem);

					if (child == nextCls) {
						next = treeItem;
					}
				}

				if (next == null) {
					next = new TreeItem<>(nextCls);
					parent.getChildren().add(next);
				}

				next.setExpanded(true);

				if (i == 1) {
					contentNode.classHierarchyTree.getSelectionModel().select(next);
				}

				parent = next;
			}

			if (!cls.getChildClasses().isEmpty()) {
				List<ClassInstance> items = new ArrayList<>(cls.getChildClasses());
				items.sort(Comparator.comparing(ClassInstance::toString));

				for (int j = 0; j < items.size() && j < 10; j++) {
					ClassInstance child = items.get(j);

					parent.getChildren().add(new TreeItem<>(child));
				}
			}

			if (!cls.getInterfaces().isEmpty()) {
				Set<ClassInstance> ifaces = new HashSet<>();
				Queue<ClassInstance> toCheck = new ArrayDeque<>();
				toCheck.add(cls);

				while ((cCls = toCheck.poll()) != null) {
					for (ClassInstance next : cCls.getInterfaces()) {
						if (ifaces.add(next)) toCheck.add(next);
					}
				}

				List<ClassInstance> sortedIfaces = new ArrayList<>(ifaces);
				sortedIfaces.sort(Comparator.comparing(ClassInstance::toString));
				contentNode.ifaceList.getItems().setAll(sortedIfaces);
			}
		}

		private final ContentNode contentNode;
		private List<ClassInstance> cmpClasses;
	}

	private class MemberChangeListener implements ChangeListener<MemberInstance<?>> {
		MemberChangeListener(ContentNode contentNode) {
			this.contentNode = contentNode;
		}

		@SuppressWarnings("unchecked")
		@Override
		public void changed(ObservableValue<? extends MemberInstance<?>> observable, MemberInstance<?> oldValue, MemberInstance<?> newValue) {
			final MemberInstance<?> prevMatchSelection;

			if (contentNode.leftSide) {
				prevMatchSelection = newValue != oldValue || memberListB.getSelectionModel().isEmpty() ? null : memberListB.getSelectionModel().getSelectedItem().getSubject();

				memberListB.getItems().clear();
			} else { // right side
				prevMatchSelection = null;

				contentNode.memberClassifierTable.getItems().clear();
			}

			if (newValue == null) return;
			if (newValue.getCls().getMatch() == null) return;

			if (contentNode.leftSide) {
				Callable<List<? extends RankResult<? extends MemberInstance<?>>>> ranker;

				if (newValue instanceof MethodInstance) {
					ranker = () -> MethodClassifier.rank((MethodInstance) newValue, newValue.getCls().getMatch().getMethods(), matcher);
				} else {
					ranker = () -> FieldClassifier.rank((FieldInstance) newValue, newValue.getCls().getMatch().getFields(), matcher);
				}

				// update matches list
				runAsyncTask(ranker)
				.whenComplete((res, exc) -> {
					if (exc != null) {
						exc.printStackTrace();
					} else if (memberListA.getSelectionModel().getSelectedItem() == newValue) {
						memberListB.getItems().setAll((List<RankResult<MemberInstance<?>>>) res);

						if (prevMatchSelection != null) { // reselect the previously selected entry
							for (int i = 0; i < memberListB.getItems().size(); i++) {
								if (memberListB.getItems().get(i).getSubject() == prevMatchSelection) {
									memberListB.getSelectionModel().select(i);
									break;
								}
							}
						}

						if (memberListB.getSelectionModel().isEmpty()) {
							memberListB.getSelectionModel().selectFirst();
						}
					}
				});
			} else { // right side
				// update classifier score table
				contentNode.memberClassifierTable.getItems().setAll(memberListB.getSelectionModel().getSelectedItem().getResults());
			}
		}

		private final ContentNode contentNode;
	}

	private static final boolean showHierarchy = false;
	private static final double padding = 5;

	private static final ExecutorService threadPool = Executors.newCachedThreadPool();
	private static final Object zipFsLock = new Object();

	private final Set<Runnable> projectChangeListeners = new HashSet<>();
	private final Set<Runnable> classMatchListeners = new HashSet<>();
	private final Set<Runnable> memberMatchListeners = new HashSet<>();
	private Matcher matcher;

	Scene scene;
	ListView<ClassInstance> clsListA;
	ListView<RankResult<ClassInstance>> clsListB;
	ListView<MemberInstance<?>> memberListA;
	ListView<RankResult<MemberInstance<?>>> memberListB;
	ContentNode leftContent;
	ContentNode rightContent;

	private final double absClassAutoMatchThreshold = 0.8;
	private final double relClassAutoMatchThreshold = 0.08;
	private final double absMethodAutoMatchThreshold = 0.8;
	private final double relMethodAutoMatchThreshold = 0.08;
	private final double absFieldAutoMatchThreshold = 0.8;
	private final double relFieldAutoMatchThreshold = 0.08;
}
