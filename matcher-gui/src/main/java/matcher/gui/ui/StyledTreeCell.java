package matcher.gui.ui;

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
			setCustomStyle(this);
		}
	}

	protected abstract String getText(T item);

	protected void setCustomStyle(StyledTreeCell<?> cell) {
	}
}
