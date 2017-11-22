package matcher.gui;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;

public abstract class ListCellFactory<T> implements Callback<ListView<T>, ListCell<T>> {
	@Override
	public ListCell<T> call(ListView<T> list) {
		return new ListCell<T>() {
			@Override
			protected void updateItem(T item, boolean empty) {
				super.updateItem(item, empty);

				if (empty || item == null) {
					setText(null);
					setStyle("");
				} else {
					setText(ListCellFactory.this.getText(item));
					setStyle(ListCellFactory.this.getStyle(item));
				}
			}
		};
	}

	protected abstract String getText(T item);

	protected String getStyle(T item) {
		return "";
	}
}
