package matcher.gui.ui;

import java.util.Set;

import matcher.model.type.ClassInstance;
import matcher.model.type.FieldInstance;
import matcher.model.type.MatchType;
import matcher.model.type.MethodInstance;
import matcher.model.type.MethodVarInstance;

public interface IGuiComponent {
	default void onProjectChange() { }

	default void onViewChange(ViewChangeCause cause) { }

	default void onMappingChange() { }
	default void onMatchChange(Set<MatchType> types) { }

	default void onClassSelect(ClassInstance cls) { }
	default void onMethodSelect(MethodInstance method) { }
	default void onFieldSelect(FieldInstance field) { }
	default void onMethodVarSelect(MethodVarInstance arg) { }

	default void onMatchListRefresh() { }

	enum ViewChangeCause {
		SORTING_CHANGED,
		CLASS_TREE_VIEW_TOGGLED,
		DISPLAY_CLASSES_CHANGED,
		THEME_CHANGED,
		DIFF_COLORS_TOGGLED,
		NAME_TYPE_CHANGED,
		DECOMPILER_CHANGED
	}

	interface Selectable extends IGuiComponent {
		void onSelectStateChange(boolean selected);
	}
}
