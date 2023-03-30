package matcher.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MultipleSelectionModel;

public final class GuiUtil {
	public static CheckMenuItem addCheckMenuItem(Menu menu, String text, boolean value, Consumer<Boolean> handler) {
		CheckMenuItem item = new CheckMenuItem(text);
		item.setSelected(value);

		item.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null) handler.accept(newValue);
		});

		menu.getItems().add(item);

		return item;
	}

	public static <T> void moveSelectionUp(ListView<T> list) {
		MultipleSelectionModel<T> selection = list.getSelectionModel();
		List<Integer> selected = new ArrayList<>(selection.getSelectedIndices());

		list.getSelectionModel().clearSelection();

		for (int idx : selected) {
			if (idx > 0 && !selection.isSelected(idx - 1)) {
				T e = list.getItems().remove(idx);
				list.getItems().add(idx - 1, e);
				selection.select(idx - 1);
			} else {
				selection.select(idx);
			}
		}
	}

	public static <T> void moveSelectionDown(ListView<T> list) {
		MultipleSelectionModel<T> selection = list.getSelectionModel();
		List<Integer> selected = new ArrayList<>(selection.getSelectedIndices());
		Collections.reverse(selected);
		list.getSelectionModel().clearSelection();

		for (int idx : selected) {
			if (idx < list.getItems().size() - 1 && !selection.isSelected(idx + 1)) {
				T e = list.getItems().remove(idx);
				list.getItems().add(idx + 1, e);
				selection.select(idx + 1);
			} else {
				selection.select(idx);
			}
		}
	}
}
