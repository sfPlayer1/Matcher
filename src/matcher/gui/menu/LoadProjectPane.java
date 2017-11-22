package matcher.gui.menu;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import matcher.gui.Gui;
import matcher.gui.GuiConstants;

public class LoadProjectPane extends VBox {
	LoadProjectPane(List<Path> paths, Window window, Node okButton) {
		super(GuiConstants.padding);

		this.paths = paths;
		this.window = window;
		this.okButton = okButton;

		init();
	}

	private void init() {
		getChildren().add(new Label("Input directories:"));

		ListView<Path> list = new ListView<>(FXCollections.observableList(paths));
		getChildren().add(list);

		list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		list.setPrefWidth(320);
		list.setPrefHeight(200);
		VBox.setVgrow(list, Priority.ALWAYS);

		HBox footer = new HBox(GuiConstants.padding);
		getChildren().add(footer);

		footer.setAlignment(Pos.CENTER_RIGHT);

		Button button = new Button("add");
		footer.getChildren().add(button);
		button.setOnAction(event -> {
			Path path = Gui.requestDir("Select directory to add", window);
			if (path != null && !list.getItems().contains(path)) list.getItems().add(path);
		});

		Button removeButton = new Button("remove");
		footer.getChildren().add(removeButton);
		removeButton.setOnAction(event -> {
			Set<Path> selected = new HashSet<>(list.getSelectionModel().getSelectedItems());
			list.getItems().removeIf(selected::contains);
		});

		ListChangeListener<Path> itemChangeListener = change -> {
			okButton.setDisable(paths.isEmpty());
		};

		list.getItems().addListener(itemChangeListener);

		ListChangeListener<Path> selectChangeListener = change -> {
			List<Integer> selectedIndices = list.getSelectionModel().getSelectedIndices();
			boolean empty = selectedIndices.isEmpty();

			removeButton.setDisable(empty);
		};

		list.getSelectionModel().getSelectedItems().addListener(selectChangeListener);

		itemChangeListener.onChanged(null);
		selectChangeListener.onChanged(null);
	}

	private final List<Path> paths;
	private final Window window;
	private final Node okButton;
}
