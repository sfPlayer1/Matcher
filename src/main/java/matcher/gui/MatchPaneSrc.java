package matcher.gui;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.css.CssParser;
import javafx.css.Declaration;
import javafx.css.Rule;
import javafx.css.Selector;
import javafx.css.Stylesheet;
import javafx.css.converter.ColorConverter;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Cell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.paint.Color;

import matcher.Matcher;
import matcher.NameType;
import matcher.Util;
import matcher.config.Config;
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
		setId("match-pane-src");
		retrieveCellTextColors();

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

	private void retrieveCellTextColors() {
		CssParser parser = new CssParser();
		Stylesheet css;

		try {
			css = parser.parse(Config.getTheme().getUrl().toURI().toURL());
		} catch (IOException | URISyntaxException e) {
			Matcher.LOGGER.error("CSS parsing failed", e);
			return;
		}

		for (Rule rule : css.getRules()) {
			boolean lowSimilarityStylePresent = false;
			boolean highSimilarityStylePresent = false;

			for (Selector selector : rule.getSelectors()) {
				if (selector.toString().contains(CellStyleClass.LOW_MATCH_SIMILARITY.cssName)) {
					lowSimilarityStylePresent = true;
				}

				if (selector.toString().contains(CellStyleClass.HIGH_MATCH_SIMILARITY.cssName)) {
					highSimilarityStylePresent = true;
				}

				if (lowSimilarityStylePresent && highSimilarityStylePresent) break;
			}

			if (!lowSimilarityStylePresent && !highSimilarityStylePresent) continue;

			Optional<Declaration> textColorDecl = rule.getDeclarations().stream()
					.filter(decl -> decl.getProperty().equals("-fx-text-fill"))
					.reduce((first, second) -> second);
			if (textColorDecl.isEmpty()) continue;

			Color color = ColorConverter.getInstance().convert(textColorDecl.get().getParsedValue(), null);

			if (lowSimilarityStylePresent) lowSimilarityCellTextColor = color;
			if (highSimilarityStylePresent) highSimilarityCellTextColor = color;
		};
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
		protected void setCustomStyle(StyledListCell<?> cell, T item) {
			setCellStyle(cell, item);
		}
	}

	protected enum CellStyleClass {
		NO_MATCH("no-match-cell"),
		LOW_MATCH_SIMILARITY("low-match-similarity-cell"),
		MODERATE_MATCH_SIMILARITY("moderate-match-similarity-cell"),
		HIGH_MATCH_SIMILARITY("high-match-similarity-cell");

		static {
			cssNames = Arrays.stream(values())
					.map(e -> e.cssName)
					.collect(Collectors.toList());
		}

		CellStyleClass(String cssName) {
			this.cssName = cssName;
		}

		public final String cssName;
		public static final List<String> cssNames;
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
		protected void setCustomStyle(StyledTreeCell<?> cell) {
			if (cell.getItem() instanceof Matchable<?>) {
				setCellStyle(cell, (Matchable<?>) cell.getItem());
			} else {
				cell.getStyleClass().removeAll(CellStyleClass.cssNames);
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

	private void setCellStyle(Cell<?> cell, Matchable<?> item) {
		CellStyleClass styleClass = null;

		if (!gui.isUseDiffColors()) {
			if (!item.hasPotentialMatch()) {
				styleClass = CellStyleClass.NO_MATCH;
			} else if (item.getMatch() == null) {
				styleClass = CellStyleClass.LOW_MATCH_SIMILARITY;
			} else if (!item.isFullyMatched(false)) { // TODO: change recursive to true once arg+var matching is further implemented
				styleClass = CellStyleClass.MODERATE_MATCH_SIMILARITY;
			} else {
				styleClass = CellStyleClass.HIGH_MATCH_SIMILARITY;
			}
		} else {
			float similarity = item.getSimilarity();

			if (similarity < Util.floatError) {
				styleClass = CellStyleClass.LOW_MATCH_SIMILARITY;
			} else if (similarity > 1 - Util.floatError) {
				styleClass = CellStyleClass.HIGH_MATCH_SIMILARITY;
			} else {
				cell.setTextFill(null);
				if (lowSimilarityCellTextColor == null || highSimilarityCellTextColor == null) return;

				similarity = Math.round((similarity / diffColorStepAlignment)) * diffColorStepAlignment;
				int similarityAsInt = (int) (similarity * 100);
				Color textColor;

				switch (Config.getTheme().getDiffColorInterpolationMode()) {
				case RGB:
					textColor = similarityToColorRgb.get(similarityAsInt);

					if (textColor == null) {
						textColor = interpolateRgb(lowSimilarityCellTextColor, highSimilarityCellTextColor, similarity);
						similarityToColorRgb.put(similarityAsInt, textColor);
					}

					break;
				case HSB:
					textColor = similarityToColorHsb.get(similarityAsInt);

					if (textColor == null) {
						textColor = interpolateHsb(lowSimilarityCellTextColor, highSimilarityCellTextColor, similarity);
						similarityToColorHsb.put(similarityAsInt, textColor);
					}

					break;
				default:
					throw new UnsupportedOperationException("Unsupported color interpolation mode!");
				}

				assert textColor != null;
				cell.setTextFill(textColor);
			}
		}

		cell.getStyleClass().removeAll(CellStyleClass.cssNames);
		if (styleClass != null) cell.getStyleClass().add(styleClass.cssName);
	}

	public Color interpolateRgb(Color colorA, Color colorB, float step) {
		float redA = (float) colorA.getRed() * 255;
		float redB = (float) colorB.getRed() * 255;
		float greenA = (float) colorA.getGreen() * 255;
		float greenB = (float) colorB.getGreen() * 255;
		float blueA = (float) colorA.getBlue() * 255;
		float blueB = (float) colorB.getBlue() * 255;
		float stepInv = 1 - step;

		return Color.rgb(
			(int) (redA * stepInv + redB * step),
			(int) (greenA * stepInv + greenB * step),
			(int) (blueA * stepInv + blueB * step));
	}

	public Color interpolateHsb(Color colorA, Color colorB, float step) {
		float hueA = (float) colorA.getHue();
		float hueB = (float) colorB.getHue();

		// Hue interpolation
		float hue;
		float hueDiff = hueB - hueA;

		if (hueA > hueB) {
			float tempHue = hueB;
			hueB = hueA;
			hueA = tempHue;

			Color tempColor = colorB;
			colorB = colorA;
			colorA = tempColor;

			hueDiff = -hueDiff;
			step = 1 - step;
		}

		if (hueDiff <= 180) {
			hue = hueA + step * hueDiff;
		} else {
			hueA = hueA + 360;
			hue = (hueA + step * (hueB - hueA)) % 360;
		}

		// Interpolate the rest
		return Color.hsb(
				hue,
				colorA.getSaturation() + step * (colorB.getSaturation() - colorA.getSaturation()),
				colorA.getBrightness() + step * (colorB.getBrightness() - colorA.getBrightness()));
	}

	@Override
	public void onClassSelect(ClassInstance cls) {
		List<MemberInstance<?>> items = memberList.getItems();
		items.clear();

		if (cls != null) {
			for (MethodInstance mth : cls.getMethods()) {
				if (!mth.isReal() || (gui.isHideUnmappedA() && !mth.hasNonInheritedMappedName() && !mth.hasMappedChildren())) {
					continue;
				}

				items.add(mth);
			}

			for (FieldInstance fld : cls.getFields()) {
				if (!fld.isReal() || (gui.isHideUnmappedA() && !fld.hasMappedName())) {
					continue;
				}

				items.add(fld);
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
			for (MethodVarInstance arg : method.getArgs()) {
				if (gui.isHideUnmappedA() && !arg.hasMappedName()) {
					continue;
				}

				items.add(arg);
			}

			for (MethodVarInstance var : method.getVars()) {
				if (gui.isHideUnmappedA() && !var.hasMappedName()) {
					continue;
				}

				items.add(var);
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
	public void onViewChange(ViewChangeCause cause) {
		switch (cause) {
		case CLASS_TREE_VIEW_TOGGLED:
			ClassInstance selected = getSelectedClass();
			useClassTree = gui.isUseClassTreeView();

			((SplitPane) getItems().get(0)).getItems().set(0, createClassList());

			updateLists(true, false);

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

			break;

		case THEME_CHANGED:
			retrieveCellTextColors();
			similarityToColorRgb.clear();
			similarityToColorHsb.clear();
		case DIFF_COLORS_TOGGLED:
			updateLists(true, true);
			break;

		case DISPLAY_CLASSES_CHANGED:
			updateLists(true, false);
			break;

		case SORTING_CHANGED:
		case NAME_TYPE_CHANGED:
			updateLists(false, true);
			break;
		}

		IFwdGuiComponent.super.onViewChange(cause);
	}

	/**
	 * @param updateContents if classes got added/removed or the use of the class tree view got toggled
	 * @param resortMembers whether or not all classes' members should get re-sorted
	 */
	private void updateLists(boolean updateContents, boolean resortMembers) {
		Comparator<ClassInstance> clsComparator = getClassComparator();
		Comparator<MemberInstance<?>> memberComparator = getMemberComparator();

		ClassInstance selClass = getSelectedClass();
		MemberInstance<?> selMember = memberList.getSelectionModel().getSelectedItem();

		suppressChangeEvents = true;

		List<ClassInstance> classes = updateContents ? gui.getEnv().getDisplayClassesA(!gui.isShowNonInputs(), gui.isHideUnmappedA()) : null;

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

		if (resortMembers) {
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
	private static final int diffColorSteps = 20;
	private static final float diffColorStepAlignment = 1f / diffColorSteps;
	private static final Map<Integer, Color> similarityToColorRgb = new HashMap<>(diffColorSteps / 2 + 1);
	private static final Map<Integer, Color> similarityToColorHsb = new HashMap<>(diffColorSteps / 2 + 1);

	private final Gui gui;
	private final Collection<IGuiComponent> components = new ArrayList<>();
	private boolean useClassTree;
	private ListView<ClassInstance> classList;
	private TreeView<Object> classTree;
	private final ListView<MemberInstance<?>> memberList = new ListView<>();
	private final ListView<MethodVarInstance> varList = new ListView<>();

	private Color lowSimilarityCellTextColor;
	private Color highSimilarityCellTextColor;
	private boolean suppressChangeEvents;
}
