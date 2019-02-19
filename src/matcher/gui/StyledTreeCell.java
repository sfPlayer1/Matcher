package matcher.gui;

import javafx.scene.control.TreeCell;

abstract class StyledTreeCell<T> extends TreeCell<T> {
	@Override
	protected void updateItem(T item, boolean empty) {
		super.updateItem(item, empty);

		if (empty || item == null) {
			setText(null);
			setStyle("");
		} else {
			setText(getText(item));
			setStyle(getStyle(item));
		}
	}

	protected abstract String getText(T item);

	protected String getStyle(T item) {
		return "";
	}
}
