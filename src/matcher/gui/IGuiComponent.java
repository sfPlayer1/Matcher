package matcher.gui;

import java.util.Set;

import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MatchType;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public interface IGuiComponent {
	default void onProjectChange() { }

	default void onViewChange() { }

	default void onMappingChange() { }
	default void onMatchChange(Set<MatchType> types) { }

	default void onClassSelect(ClassInstance cls) { }
	default void onMethodSelect(MethodInstance method) { }
	default void onFieldSelect(FieldInstance field) { }
	default void onMethodVarSelect(MethodVarInstance arg) { }

	default void onMatchListRefresh() { }
}
