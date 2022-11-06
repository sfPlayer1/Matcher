package matcher.gui;

import javafx.scene.control.ListCell;

abstract class StyledListCell<T> extends ListCell<T> {
	@Override
	protected void updateItem(T item, boolean empty) {
		super.updateItem(item, empty);

		if (empty || item == null) {
			setText(null);
			setStyle("");
		} else {
			setText(getText(item));
			setCustomStyle(this, item);
		}
	}

	protected abstract String getText(T item);

	protected void setCustomStyle(StyledListCell<?> cell, T item) {
	};
}
