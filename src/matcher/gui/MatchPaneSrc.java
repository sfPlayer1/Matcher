package matcher.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import matcher.NameType;
import matcher.Util;
import matcher.gui.Gui.SortKey;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MatchType;
import matcher.type.Matchable;
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

		verticalPane.getItems().add(createClassList());

		// member list

		memberList.setCellFactory(ignore -> new SrcListCell<MemberInstance<?>>());
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

		// method var list

		varList.setCellFactory(ignore -> new SrcListCell<MethodVarInstance>());
		varList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			if (suppressChangeEvents || oldValue == newValue) return;

			onMethodVarSelect(newValue);
		});

		verticalPane.getItems().add(varList);

		// content

		ContentPane content = new ContentPane(gui, this, true);
		components.add(content);
		getItems().add(content);

		// positioning

		verticalPane.setOrientation(Orientation.VERTICAL);
		verticalPane.setDividerPositions(0.65, 0.9);

		SplitPane.setResizableWithParent(verticalPane, false);
		setDividerPosition(0, 0.25);
	}

	private Node createClassList() {
		if (useClassTree) {
			classList = null;
			classTree = new TreeView<>(new TreeItem<>(null));

			classTree.setShowRoot(false);
			classTree.setCellFactory(ignore -> new SrcTreeCell());
			classTree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
				if (suppressChangeEvents || oldValue == newValue) return;

				onClassSelect(newValue != null && newValue.getValue() instanceof ClassInstance ? (ClassInstance) newValue.getValue() : null);
			});

			return classTree;
		} else {
			classTree = null;
			classList = new ListView<>();

			classList.setCellFactory(ignore -> new SrcListCell<>());
			classList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
				if (suppressChangeEvents || oldValue == newValue) return;

				onClassSelect(newValue);
			});

			return classList;
		}
	}

	private class SrcListCell<T extends Matchable<? extends T>> extends StyledListCell<T> {
		@Override
		protected String getText(T item) {
			return getCellText(item, true);
		}

		@Override
		protected String getStyle(T item) {
			return getCellStyle(item);
		}
	}

	private class SrcTreeCell extends StyledTreeCell<Object> {
		@Override
		protected String getText(Object item) {
			if (item instanceof String) {
				return (String) item;
			} else if (item instanceof Matchable<?>) {
				return getCellText((Matchable<?>) item, false);
			} else {
				return "";
			}
		}

		@Override
		protected String getStyle(Object item) {
			if (item instanceof String) {
				return "";
			} else if (item instanceof Matchable<?>) {
				return getCellStyle((Matchable<?>) item);
			} else {
				return "";
			}
		}
	}

	private String getCellText(Matchable<?> item, boolean full) {
		String name = getName(item, full);
		String mappedName = getMappedName(item, full);

		if (name.equals(mappedName)) {
			return name;
		} else {
			return name+" - "+mappedName;
		}
	}

	private String getCellStyle(Matchable<?> item) {
		if (gui.isUseDiffColors()) {
			final float epsilon = 1e-5f;
			float similarity = item.getSimilarity();

			if (similarity < epsilon) {
				return "-fx-text-fill: darkred;";
			} else if (similarity > 1 - epsilon) {
				return "-fx-text-fill: darkgreen;";
			} else {
				final float hue0 = 30; // red, darkred=0
				final float hue1 = 90; // green, darkgreen=120
				final float saturation = 0.8f; // darkred=darkgreen=1
				final float value0 = 0.645f; // darkred=0.545
				final float value1 = 0.492f; // darkgreen=0.392

				float f0 = 1 - similarity;
				float f1 = similarity;
				float h = hue0 * f0 + hue1 * f1;
				float v = value0 * f0 + value1 * f1;

				float vs = v * saturation;
				float h6 = h * (1f / 60);

				float red = hsvToRgb(v, vs, h6, 5);
				float green = hsvToRgb(v, vs, h6, 3);
				float blue = hsvToRgb(v, vs, h6, 1);

				return String.format("-fx-text-fill: #%02x%02x%02x", (int) (red * 255), (int) (green * 255), (int) (blue * 255));
			}
		} else {
			if (!item.hasPotentialMatch()) {
				return "-fx-text-fill: dimgray;";
			} else if (item.getMatch() == null) {
				return "-fx-text-fill: darkred;";
			} else if (!item.isFullyMatched(false)) { // TODO: change recursive to true once arg+var matching is further implemented
				return "-fx-text-fill: chocolate;";
			} else {
				return "-fx-text-fill: darkgreen;";
			}
		}
	}

	private static float hsvToRgb(float v, float vs, float h6, int n) {
		float k = (n + h6) % 6;

		return v - vs * Math.max(Math.min(Math.min(k, 4-k), 1), 0);
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
	public void onMethodSelect(MethodInstance method) {
		List<MethodVarInstance> items = varList.getItems();
		items.clear();

		if (method != null) {
			for (MethodVarInstance m : method.getArgs()) {
				items.add(m);
			}

			for (MethodVarInstance m : method.getVars()) {
				items.add(m);
			}

			items.sort(getVarComparator());
		}

		IFwdGuiComponent.super.onMethodSelect(method);
	}

	@Override
	public ClassInstance getSelectedClass() {
		if (useClassTree) {
			TreeItem<Object> item = classTree.getSelectionModel().getSelectedItem();

			return item != null && item.getValue() instanceof ClassInstance ? (ClassInstance) item.getValue() : null;
		} else {
			return classList.getSelectionModel().getSelectedItem();
		}
	}

	@Override
	public MemberInstance<?> getSelectedMember() {
		return memberList.getSelectionModel().getSelectedItem();
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
	public MethodVarInstance getSelectedMethodVar() {
		return varList.getSelectionModel().getSelectedItem();
	}

	@Override
	public void onProjectChange() {
		updateLists(true, true);

		IFwdGuiComponent.super.onProjectChange();
	}

	@Override
	public void onViewChange() {
		if (gui.isUseClassTreeView() != useClassTree) {
			ClassInstance selected = getSelectedClass();
			useClassTree = gui.isUseClassTreeView();

			((SplitPane) getItems().get(0)).getItems().set(0, createClassList());

			updateLists(true, true);

			if (selected != null) {
				if (useClassTree) {
					for (TreeItem<Object> pkgItem : classTree.getRoot().getChildren()) {
						for (TreeItem<Object> item : pkgItem.getChildren()) {
							if (item.getValue() == selected) {
								classTree.getSelectionModel().select(item);
								break;
							}
						}
					}
				} else {
					classList.getSelectionModel().select(selected);
				}
			}
		} else {
			updateLists(true, true);
		}

		IFwdGuiComponent.super.onViewChange();
	}

	private void updateLists(boolean updateContents, boolean updateMembers) {
		Comparator<ClassInstance> clsComparator = getClassComparator();
		Comparator<MemberInstance<?>> memberComparator = getMemberComparator();

		ClassInstance selClass = getSelectedClass();
		MemberInstance<?> selMember = memberList.getSelectionModel().getSelectedItem();

		suppressChangeEvents = true;

		List<ClassInstance> classes = updateContents ? gui.getEnv().getDisplayClassesA(!gui.isShowNonInputs()) : null;


		if (useClassTree) {
			updateClassTree(classes, clsComparator, selClass);
		} else {
			if (updateContents) {
				classList.setItems(FXCollections.observableList(classes));
			} else {
				classes = classList.getItems();
			}

			classes.sort(clsComparator);
			classList.getSelectionModel().select(selClass);
		}

		if (updateMembers) {
			memberList.getItems().sort(memberComparator);
			memberList.getSelectionModel().select(selMember);
		}

		suppressChangeEvents = false;
	}

	private void updateClassTree(List<ClassInstance> newClasses, Comparator<ClassInstance> sortComparator, ClassInstance selectedClass) {
		Map<String, List<TreeItem<Object>>> pkgNodeMap = null; // reuse pkg level nodes to keep their folding state

		if (newClasses == null) {
			newClasses = new ArrayList<>(2000);
			pkgNodeMap = new HashMap<>(100);

			for (TreeItem<Object> pkgItem : classTree.getRoot().getChildren()) {
				pkgNodeMap.computeIfAbsent((String) pkgItem.getValue(), ignore -> new ArrayList<>()).add(pkgItem);

				for (TreeItem<Object> item : pkgItem.getChildren()) {
					newClasses.add((ClassInstance) item.getValue());
				}

				pkgItem.getChildren().clear();
			}
		}

		newClasses.sort(sortComparator);

		NameType nameType = gui.getNameType()
				.withMapped(gui.getSortKey() == SortKey.MappedName)
				.withUnmatchedTmp(true);

		List<TreeItem<Object>> items = classTree.getRoot().getChildren();
		items.clear();
		String pkg = null;
		List<TreeItem<Object>> pkgItems = null;
		TreeItem<Object> toSelect = null;

		for (ClassInstance cls : newClasses) {
			ClassInstance outerCls = cls;

			while (outerCls.getOuterClass() != null) {
				outerCls = outerCls.getOuterClass();
			}

			String name = outerCls.getDisplayName(nameType, true);
			int pos = name.lastIndexOf('.');

			if (pos == -1) {
				name = "<no package>";
			} else {
				name = name.substring(0, pos);
			}

			if (!name.equals(pkg)) {
				TreeItem<Object> item;
				List<TreeItem<Object>> pkgNode;

				if (pkgNodeMap != null && (pkgNode = pkgNodeMap.remove(name)) != null) {
					item = pkgNode.remove(0);
					if (!pkgNode.isEmpty()) pkgNodeMap.put(name, pkgNode);
				} else {
					item = new TreeItem<>(name);
				}

				items.add(item);
				pkgItems = item.getChildren();
				pkg = name;
			}

			TreeItem<Object> item = new TreeItem<>(cls);
			pkgItems.add(item);

			if (cls == selectedClass) toSelect = item;
		}

		if (toSelect != null) {
			classTree.getSelectionModel().select(toSelect);
		}
	}

	private void refreshClassList() {
		if (useClassTree) {
			classTree.refresh();
		} else {
			classList.refresh();
		}
	}

	@SuppressWarnings("unchecked")
	private Comparator<ClassInstance> getClassComparator() {
		switch (gui.getSortKey()) {
		case Name:
			return Comparator.comparing(this::getName, clsNameComparator);
		case MappedName:
			return Comparator.comparing(this::getMappedName, clsNameComparator);
		case MatchStatus:
			return ((Comparator<ClassInstance>) matchStatusComparator).thenComparing(this::getName, clsNameComparator);
		case Similarity:
			return ((Comparator<ClassInstance>) similarityComparator).thenComparing(this::getName, clsNameComparator);
		}

		throw new IllegalStateException("unhandled sort key: "+gui.getSortKey());
	}

	@SuppressWarnings("unchecked")
	private Comparator<MemberInstance<?>> getMemberComparator() {
		switch (gui.getSortKey()) {
		case Name:
			return memberTypeComparator.thenComparing(this::getName, clsNameComparator);
		case MappedName:
			return memberTypeComparator.thenComparing(this::getMappedName, clsNameComparator);
		case MatchStatus:
			return ((Comparator<MemberInstance<?>>) matchStatusComparator).thenComparing(memberTypeComparator).thenComparing(this::getName, clsNameComparator);
		case Similarity:
			return ((Comparator<MemberInstance<?>>) similarityComparator).thenComparing(memberTypeComparator).thenComparing(this::getName, clsNameComparator);
		}

		throw new IllegalStateException("unhandled sort key: "+gui.getSortKey());
	}

	@SuppressWarnings("unchecked")
	private Comparator<MethodVarInstance> getVarComparator() {
		switch (gui.getSortKey()) {
		case Name:
			return varTypeComparator.thenComparing(this::getName, clsNameComparator);
		case MappedName:
			return varTypeComparator.thenComparing(this::getMappedName, clsNameComparator);
		case MatchStatus:
			return ((Comparator<MethodVarInstance>) matchStatusComparator).thenComparing(varTypeComparator).thenComparing(this::getName, clsNameComparator);
		case Similarity:
			return ((Comparator<MethodVarInstance>) similarityComparator).thenComparing(varTypeComparator).thenComparing(this::getName, clsNameComparator);
		}

		throw new IllegalStateException("unhandled sort key: "+gui.getSortKey());
	}

	private String getName(Matchable<?> m) {
		return getName(m, true);
	}

	private String getName(Matchable<?> m, boolean full) {
		return m.getDisplayName(gui.getNameType().withMapped(false).withUnmatchedTmp(true), full && m instanceof ClassInstance);
	}

	private String getMappedName(Matchable<?> m) {
		return getMappedName(m, true);
	}

	private String getMappedName(Matchable<?> m, boolean full) {
		return m.getDisplayName(gui.getNameType().withMapped(true).withUnmatchedTmp(true), full && m instanceof ClassInstance);
	}

	@Override
	public void onMappingChange() {
		updateLists(false, true);
		refreshClassList();

		memberList.refresh();

		IFwdGuiComponent.super.onMappingChange();
	}

	@Override
	public void onMatchChange(Set<MatchType> types) {
		if (gui.getSortKey() == SortKey.MatchStatus || gui.getSortKey() == SortKey.Similarity) {
			updateLists(false, true);
		} else if (types.contains(MatchType.Class)) {
			updateLists(false, false);
		}

		refreshClassList(); // unconditional because it could affect the fully matched status

		if (types.contains(MatchType.Method) || types.contains(MatchType.Field) || types.contains(MatchType.MethodVar)) {
			memberList.refresh();
		}

		if (types.contains(MatchType.MethodVar)) {
			varList.refresh();
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

	private static final Comparator<MethodVarInstance> varTypeComparator = (a, b) -> {
		if (a.isArg() == b.isArg()) {
			return 0;
		} else {
			return a.isArg() ? -1 : 1;
		}
	};

	private static final Comparator<? extends Matchable<?>> matchStatusComparator = (a, b) -> {
		// sort order: unmatched partially-matched fully-matched-shallow fully-matched-recursive unmatchable

		boolean aMatchable = a.hasPotentialMatch();
		boolean bMatchable = b.hasPotentialMatch();

		if (aMatchable != bMatchable) {
			return aMatchable ? -1 : 1;
		} else if (!aMatchable) {
			return 0;
		} else if (a.hasMatch() != b.hasMatch()) {
			return a.hasMatch() ? 1 : -1;
		} else if (!a.hasMatch()) {
			return 0;
		}

		boolean aFull = a.isFullyMatched(false);
		boolean bFull = b.isFullyMatched(false);

		if (aFull != bFull) {
			return aFull ? 1 : -1;
		} else if (!aFull) {
			return 0;
		}

		aFull = a.isFullyMatched(true);
		bFull = b.isFullyMatched(true);

		if (aFull != bFull) {
			return aFull ? 1 : -1;
		}

		return 0;
	};

	private static final Comparator<? extends Matchable<?>> similarityComparator = (a, b) -> {
		return Float.compare(a.getSimilarity(), b.getSimilarity());
	};

	private static final Comparator<String> clsNameComparator = Util::compareNatural;

	private final Gui gui;
	private final Collection<IGuiComponent> components = new ArrayList<>();
	private boolean useClassTree;
	private ListView<ClassInstance> classList;
	private TreeView<Object> classTree;
	private final ListView<MemberInstance<?>> memberList = new ListView<>();
	private final ListView<MethodVarInstance> varList = new ListView<>();

	private boolean suppressChangeEvents;
}
