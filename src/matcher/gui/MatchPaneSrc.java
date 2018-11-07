package matcher.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import matcher.classifier.ClassifierUtil;
import matcher.gui.Gui.SortKey;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.IMatchable;
import matcher.type.MatchType;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class MatchPaneSrc extends SplitPane implements IFwdGuiComponent, ISelectionProvider {
	public MatchPaneSrc(Gui gui) {
		this.gui = gui;

		init();
	}

	private void init() {
		// lists

		SplitPane verticalPane = new SplitPane();
		getItems().add(verticalPane);

		// class list

		classList.setCellFactory(new MatchableListCellFactory<ClassInstance>() {
			@Override
			protected boolean isFullyMatched(ClassInstance item) {
				return MatchPaneSrc.isFullyMatched(item);
			}
		});
		classList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			if (suppressChangeEvents || oldValue == newValue) return;

			onClassSelect(newValue);
		});

		verticalPane.getItems().add(classList);

		// member list

		memberList.setCellFactory(new MatchableListCellFactory<MemberInstance<?>>() {
			@Override
			protected boolean isFullyMatched(MemberInstance<?> item) {
				return MatchPaneSrc.isFullyMatched(item);
			}
		});
		memberList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			if (suppressChangeEvents || oldValue == newValue) return;

			boolean wasMethod = oldValue instanceof MethodInstance;
			boolean wasField = oldValue instanceof FieldInstance;
			boolean isMethod = newValue instanceof MethodInstance;
			boolean isField = newValue instanceof FieldInstance;

			if (wasMethod && isField
					|| wasField && isMethod) {
				if (wasMethod) {
					onMethodSelect(null);
				} else {
					onFieldSelect(null);
				}
			}

			if (isMethod || newValue == null && wasMethod) {
				onMethodSelect((MethodInstance) newValue);
			} else {
				onFieldSelect((FieldInstance) newValue);
			}
		});

		verticalPane.getItems().add(memberList);

		// content

		ContentPane content = new ContentPane(gui, this, true);
		components.add(content);
		getItems().add(content);

		// positioning

		verticalPane.setOrientation(Orientation.VERTICAL);
		verticalPane.setDividerPosition(0, 0.65);

		SplitPane.setResizableWithParent(verticalPane, false);
		setDividerPosition(0, 0.25);
	}

	private static boolean isFullyMatched(ClassInstance cls) {
		ClassInstance matched = cls.getMatch();
		if (matched == null) return false;

		boolean found = false;

		for (MethodInstance method : matched.getMethods()) {
			if (!method.hasMatch()) {
				found = true;
				break;
			}
		}

		if (found) {
			for (MethodInstance methodA : cls.getMethods()) {
				if (methodA.hasMatch()) continue;

				for (MethodInstance methodB : matched.getMethods()) {
					if (!methodB.hasMatch() && ClassifierUtil.checkPotentialEquality(methodA, methodB)) {
						return false;
					}
				}
			}
		}

		found = false;

		for (FieldInstance field : matched.getFields()) {
			if (!field.hasMatch()) {
				found = true;
				break;
			}
		}

		if (found) {
			for (FieldInstance fieldA : cls.getFields()) {
				if (fieldA.hasMatch()) continue;

				for (FieldInstance fieldB : matched.getFields()) {
					if (!fieldB.hasMatch() && ClassifierUtil.checkPotentialEquality(fieldA, fieldB)) {
						return false;
					}
				}
			}
		}

		return true;
	}

	private static boolean isFullyMatched(MemberInstance<?> member) {
		return true;
	}

	private abstract class MatchableListCellFactory<T extends IMatchable<? extends T>> extends ListCellFactory<T> {
		@Override
		protected String getText(T item) {
			String name = item.getName(false, gui.isTmpNamed(), true);
			String mappedName = item.getName(true, gui.isTmpNamed(), true);

			if (name.equals(mappedName)) {
				return name;
			} else {
				return name+" - "+mappedName;
			}
		}

		@Override
		protected String getStyle(T item) {
			if (item.getMatch() == null) {
				return "-fx-text-fill: darkred;";
			} else if (!isFullyMatched(item)) {
				return "-fx-text-fill: chocolate;";
			} else {
				return "-fx-text-fill: darkgreen;";
			}
		}

		protected abstract boolean isFullyMatched(T item);
	}

	@Override
	public void onClassSelect(ClassInstance cls) {
		List<MemberInstance<?>> items = memberList.getItems();
		items.clear();

		if (cls != null) {
			for (MethodInstance m : cls.getMethods()) {
				if (m.isReal()) items.add(m);
			}

			for (FieldInstance m : cls.getFields()) {
				if (m.isReal()) items.add(m);
			}

			items.sort(getMemberComparator());
		}

		IFwdGuiComponent.super.onClassSelect(cls);
	}

	@Override
	public ClassInstance getSelectedClass() {
		return classList.getSelectionModel().getSelectedItem();
	}

	@Override
	public MethodInstance getSelectedMethod() {
		MemberInstance<?> member = memberList.getSelectionModel().getSelectedItem();

		return member instanceof MethodInstance ? (MethodInstance) member : null;
	}

	@Override
	public FieldInstance getSelectedField() {
		MemberInstance<?> member = memberList.getSelectionModel().getSelectedItem();

		return member instanceof FieldInstance ? (FieldInstance) member : null;
	}

	@Override
	public MethodVarInstance getSelectedMethodArg() {
		return null;
	}

	@Override
	public void onProjectChange() {
		updateLists(true);

		IFwdGuiComponent.super.onProjectChange();
	}

	@Override
	public void onViewChange() {
		updateLists(true);

		IFwdGuiComponent.super.onViewChange();
	}

	private void updateLists(boolean updateContents) {
		Comparator<ClassInstance> clsComparator = getClassComparator();
		Comparator<MemberInstance<?>> memberComparator = getMemberComparator();

		ClassInstance selClass = classList.getSelectionModel().getSelectedItem();
		MemberInstance<?> selMember = memberList.getSelectionModel().getSelectedItem();

		suppressChangeEvents = true;

		classList.setItems(FXCollections.observableList(gui.getEnv().getDisplayClassesA(!gui.isShowNonInputs())));

		classList.getItems().sort(clsComparator);
		memberList.getItems().sort(memberComparator);
		classList.getSelectionModel().select(selClass);
		memberList.getSelectionModel().select(selMember);

		suppressChangeEvents = false;
	}

	private Comparator<ClassInstance> getClassComparator() {
		switch (gui.getSortKey()) {
		case Name:
			return Comparator.comparing(this::getName);
		case MappedName:
			return Comparator.<ClassInstance, String>comparing(MatchPaneSrc::getMappedName, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(this::getName);
		case MatchStatus:
			return clsMatchStatusComparator.thenComparing(this::getName);
		}

		throw new IllegalStateException("unhandled sort key: "+gui.getSortKey());
	}

	private Comparator<MemberInstance<?>> getMemberComparator() {
		switch (gui.getSortKey()) {
		case Name:
			return memberTypeComparator.thenComparing(this::getName);
		case MappedName:
			return memberTypeComparator.thenComparing(MatchPaneSrc::getMappedName, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(this::getName);
		case MatchStatus:
			return memberMatchStatusComparator.thenComparing(memberTypeComparator).thenComparing(this::getName);
		}

		throw new IllegalStateException("unhandled sort key: "+gui.getSortKey());
	}

	private String getName(IMatchable<?> m) {
		return m.getDisplayName(false, false, gui.isTmpNamed(), true);
	}

	private static String getMappedName(IMatchable<?> m) {
		String ret = m.getMappedName();
		if (ret != null) return ret;
		if (!m.isNameObfuscated(false)) return m.getName();

		return null;
	}

	@Override
	public void onMappingChange() {
		updateLists(false);
		classList.refresh();
		memberList.refresh();

		IFwdGuiComponent.super.onMappingChange();
	}

	@Override
	public void onMatchChange(Set<MatchType> types) {
		if (gui.getSortKey() == SortKey.MatchStatus) {
			updateLists(false);
		}

		classList.refresh();

		if (types.contains(MatchType.Method) || types.contains(MatchType.Field) || types.contains(MatchType.MethodArg)) {
			memberList.refresh();
		}

		IFwdGuiComponent.super.onMatchChange(types);
	}

	@Override
	public Collection<IGuiComponent> getComponents() {
		return components;
	}

	private static final Comparator<MemberInstance<?>> memberTypeComparator = (a, b) -> {
		boolean aIsMethod = a instanceof MethodInstance;
		boolean bIsMethod = b instanceof MethodInstance;

		if (aIsMethod == bIsMethod) {
			return 0;
		} else {
			return aIsMethod ? -1 : 1;
		}
	};

	private static final Comparator<ClassInstance> clsMatchStatusComparator = (a, b) -> {
		if (a.hasMatch() != b.hasMatch()) {
			return a.hasMatch() ? 1 : -1;
		} else {
			boolean aFull = isFullyMatched(a);
			boolean bFull = isFullyMatched(b);

			if (aFull == bFull) {
				return 0;
			} else {
				return aFull ? 1 : -1;
			}
		}
	};

	private static final Comparator<MemberInstance<?>> memberMatchStatusComparator = (a, b) -> {
		if (a.hasMatch() != b.hasMatch()) {
			return a.hasMatch() ? 1 : -1;
		} else {
			boolean aFull = isFullyMatched(a);
			boolean bFull = isFullyMatched(b);

			if (aFull == bFull) {
				return 0;
			} else {
				return aFull ? 1 : -1;
			}
		}
	};

	private final Gui gui;
	private final Collection<IGuiComponent> components = new ArrayList<>();
	private final ListView<ClassInstance> classList = new ListView<>();
	private final ListView<MemberInstance<?>> memberList = new ListView<>();

	private boolean suppressChangeEvents;
}
