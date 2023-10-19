package matcher.gui.ui;

import java.util.Collection;
import java.util.Set;

import matcher.model.type.ClassInstance;
import matcher.model.type.FieldInstance;
import matcher.model.type.MatchType;
import matcher.model.type.MethodInstance;
import matcher.model.type.MethodVarInstance;

public interface IFwdGuiComponent extends IGuiComponent {
	Collection<IGuiComponent> getComponents();

	default void addListener(IGuiComponent listener) {
		getComponents().add(listener);
	}

	@Override
	default void onProjectChange() {
		for (IGuiComponent c : getComponents()) {
			c.onProjectChange();
		}
	}

	@Override
	default void onViewChange(ViewChangeCause cause) {
		for (IGuiComponent c : getComponents()) {
			c.onViewChange(cause);
		}
	}

	@Override
	default void onMappingChange() {
		for (IGuiComponent c : getComponents()) {
			c.onMappingChange();
		}
	}

	@Override
	default void onMatchChange(Set<MatchType> types) {
		for (IGuiComponent c : getComponents()) {
			c.onMatchChange(types);
		}
	}

	@Override
	default void onClassSelect(ClassInstance cls) {
		for (IGuiComponent c : getComponents()) {
			c.onClassSelect(cls);
		}
	}

	@Override
	default void onMethodSelect(MethodInstance method) {
		for (IGuiComponent c : getComponents()) {
			c.onMethodSelect(method);
		}
	}

	@Override
	default void onFieldSelect(FieldInstance field) {
		for (IGuiComponent c : getComponents()) {
			c.onFieldSelect(field);
		}
	}

	@Override
	default void onMethodVarSelect(MethodVarInstance arg) {
		for (IGuiComponent c : getComponents()) {
			c.onMethodVarSelect(arg);
		}
	}

	default void onMatchListRefresh() {
		for (IGuiComponent c : getComponents()) {
			c.onMatchListRefresh();
		}
	}
}
