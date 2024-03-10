package matcher.gui;

import java.util.Set;

import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MatchType;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

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
