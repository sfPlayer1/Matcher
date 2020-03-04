package matcher.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import matcher.NameType;
import matcher.classifier.ClassClassifier;
import matcher.classifier.ClassifierLevel;
import matcher.classifier.FieldClassifier;
import matcher.classifier.MethodClassifier;
import matcher.classifier.MethodVarClassifier;
import matcher.classifier.RankResult;
import matcher.type.ClassEnv;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.Matchable;
import matcher.type.MatchType;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class MatchPaneDst extends SplitPane implements IFwdGuiComponent, ISelectionProvider {
	public MatchPaneDst(Gui gui, MatchPaneSrc srcPane) {
		this.gui = gui;
		this.srcPane = srcPane;

		init();
	}

	private void init() {
		// content

		ContentPane content = new ContentPane(gui, this, false);
		components.add(content);
		getItems().add(content);

		// vbox

		VBox vbox = new VBox();
		getItems().add(vbox);

		// match list

		matchList.setCellFactory(ignore -> new DstListCell());
		matchList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			if (suppressChangeEvents || oldValue == newValue) return;

			Matchable<?> oldSel = oldValue != null ? oldValue.getSubject() : null;
			Matchable<?> newSel = newValue != null ? newValue.getSubject() : null;

			announceSelectionChange(oldSel, newSel);
		});

		vbox.getChildren().add(matchList);
		VBox.setVgrow(matchList, Priority.ALWAYS);

		// match filter text field

		vbox.getChildren().add(filterField);
		filterField.textProperty().addListener((observable, oldValue, newValue) -> {
			RankResult<? extends Matchable<?>> oldSelection = matchList.getSelectionModel().getSelectedItem();
			updateResults(oldSelection != null ? oldSelection.getSubject() : null);
		});

		// positioning

		SplitPane.setResizableWithParent(vbox, false);
		setDividerPosition(0, 1 - 0.25);

		srcPane.addListener(srcListener);
	}

	private class DstListCell extends StyledListCell<RankResult<? extends Matchable<?>>> {
		@Override
		protected String getText(RankResult<? extends Matchable<?>> item) {
			boolean full = item.getSubject() instanceof ClassInstance;

			return String.format("%.3f %s", item.getScore(), item.getSubject().getDisplayName(gui.getNameType(), full));
		}
	}

	private void announceSelectionChange(Matchable<?> oldSel, Matchable<?> newSel) {
		if (oldSel == newSel) {
			onMatchListRefresh();
			return;
		}

		ClassInstance oldClass, newClass;
		MethodInstance oldMethod, newMethod;
		MethodVarInstance oldMethodVar, newMethodVar;
		FieldInstance oldField, newField;

		if (oldSel == null) {
			oldClass = null;
			oldMethod = null;
			oldMethodVar = null;
			oldField = null;
		} else {
			oldClass = getClass(oldSel);
			oldMethod = getMethod(oldSel);
			oldMethodVar = getMethodVar(oldSel);
			oldField = getField(oldSel);
		}

		if (newSel == null) {
			newClass = null;
			newMethod = null;
			newMethodVar = null;
			newField = null;
		} else {
			newClass = getClass(newSel);
			newMethod = getMethod(newSel);
			newMethodVar = getMethodVar(newSel);
			newField = getField(newSel);
		}

		if (newClass != oldClass) onClassSelect(newClass);
		if (newMethod != oldMethod) onMethodSelect(newMethod);
		if (newMethodVar != oldMethodVar) onMethodVarSelect(newMethodVar);
		if (newField != oldField) onFieldSelect(newField);
	}

	@Override
	public ClassInstance getSelectedClass() {
		RankResult<? extends Matchable<?>> result = matchList.getSelectionModel().getSelectedItem();
		if (result == null) return null;

		return getClass(result.getSubject());
	}

	private static ClassInstance getClass(Matchable<?> m) {
		if (m instanceof ClassInstance) {
			return (ClassInstance) m;
		} else if (m instanceof MemberInstance<?>) {
			return ((MemberInstance<?>) m).getCls();
		} else if (m instanceof MethodVarInstance) {
			return ((MethodVarInstance) m).getMethod().getCls();
		} else {
			throw new IllegalStateException();
		}
	}

	@Override
	public MemberInstance<?> getSelectedMember() {
		RankResult<? extends Matchable<?>> result = matchList.getSelectionModel().getSelectedItem();
		if (result == null) return null;

		return getMember(result.getSubject());
	}

	private static MemberInstance<?> getMember(Matchable<?> m) {
		if (m instanceof ClassInstance) {
			return null;
		} else if (m instanceof MemberInstance) {
			return (MemberInstance<?>) m;
		} else if (m instanceof MethodVarInstance) {
			return ((MethodVarInstance) m).getMethod();
		} else {
			throw new IllegalStateException();
		}
	}

	@Override
	public MethodInstance getSelectedMethod() {
		RankResult<? extends Matchable<?>> result = matchList.getSelectionModel().getSelectedItem();
		if (result == null) return null;

		return getMethod(result.getSubject());
	}

	private static MethodInstance getMethod(Matchable<?> m) {
		if (m instanceof ClassInstance || m instanceof FieldInstance) {
			return null;
		} else if (m instanceof MethodInstance) {
			return (MethodInstance) m;
		} else if (m instanceof MethodVarInstance) {
			return ((MethodVarInstance) m).getMethod();
		} else {
			throw new IllegalStateException();
		}
	}

	@Override
	public FieldInstance getSelectedField() {
		RankResult<? extends Matchable<?>> result = matchList.getSelectionModel().getSelectedItem();
		if (result == null) return null;

		return getField(result.getSubject());
	}

	private static FieldInstance getField(Matchable<?> m) {
		if (m instanceof ClassInstance || m instanceof MethodInstance || m instanceof MethodVarInstance) {
			return null;
		} else if (m instanceof FieldInstance) {
			return (FieldInstance) m;
		} else {
			throw new IllegalStateException();
		}
	}

	@Override
	public MethodVarInstance getSelectedMethodVar() {
		RankResult<? extends Matchable<?>> result = matchList.getSelectionModel().getSelectedItem();
		if (result == null) return null;

		return getMethodVar(result.getSubject());
	}

	private static MethodVarInstance getMethodVar(Matchable<?> m) {
		if (m instanceof ClassInstance || m instanceof MethodInstance || m instanceof FieldInstance) {
			return null;
		} else if (m instanceof MethodVarInstance) {
			return (MethodVarInstance) m;
		} else {
			throw new IllegalStateException();
		}
	}

	@Override
	public RankResult<?> getSelectedRankResult(MatchType type) {
		RankResult<? extends Matchable<?>> result = matchList.getSelectionModel().getSelectedItem();
		if (result == null) return null;

		switch (type) {
		case Class:
			return result.getSubject() instanceof ClassInstance ? result : null;
		case Method:
			return result.getSubject() instanceof MethodInstance ? result : null;
		case Field:
			return result.getSubject() instanceof FieldInstance ? result : null;
		case MethodVar:
			return result.getSubject() instanceof MethodVarInstance ? result : null;
		}

		throw new IllegalArgumentException("invalid type: "+type);
	}

	@Override
	public void onProjectChange() {
		cmpClasses = gui.getEnv().getDisplayClassesB(!gui.isShowNonInputs());

		IFwdGuiComponent.super.onProjectChange();
	}

	@Override
	public void onViewChange() {
		cmpClasses = gui.getEnv().getDisplayClassesB(!gui.isShowNonInputs());

		suppressChangeEvents = true;

		Comparator<RankResult<? extends Matchable<?>>> cmp;

		if (gui.isSortMatchesAlphabetically()) {
			cmp = getNameComparator();
		} else {
			cmp = getScoreComparator();
		}

		matchList.getItems().sort(cmp);

		suppressChangeEvents = false;

		IFwdGuiComponent.super.onViewChange();
	}

	@Override
	public void onMatchChange(Set<MatchType> types) {
		if (!types.isEmpty()) {
			srcListener.onSelect(types);
		} else {
			onMatchChangeApply(types);
		}
	}

	void onMatchChangeApply(Set<MatchType> types) {
		IFwdGuiComponent.super.onMatchChange(types);
	}

	@Override
	public Collection<IGuiComponent> getComponents() {
		return components;
	}

	private static Comparator<RankResult<? extends Matchable<?>>> getNameComparator() {
		return Comparator.comparing(r -> r.getSubject().getName());
	}

	private static Comparator<RankResult<? extends Matchable<?>>> getScoreComparator() {
		return Comparator.<RankResult<? extends Matchable<?>>>comparingDouble(r -> r.getScore()).reversed();
	}

	private void updateResults(Matchable<?> oldSelection) {
		List<RankResult<? extends Matchable<?>>> newItems = new ArrayList<>(rankResults.size());
		String filterStr = filterField.getText();

		if (filterStr.isBlank()) {
			newItems.addAll(rankResults);
		} else {
			List<Object> stack = new ArrayList<>();

			for (RankResult<? extends Matchable<?>> item : rankResults) {
				stack.add(item);

				Boolean res = evalFilter(stack, item);

				if (res == null) { // eval failed
					newItems.clear();
					newItems.addAll(rankResults);
					break;
				} else if (res) {
					newItems.add(item);
				}

				stack.clear();
			}
		}

		RankResult<? extends Matchable<?>> best;

		if (!newItems.isEmpty()) {
			best = newItems.get(0);

			if (gui.isSortMatchesAlphabetically()) {
				newItems.sort(getNameComparator());
			}
		} else {
			best = null;
		}

		suppressChangeEvents = true;

		matchList.getItems().setAll(newItems);

		if (matchList.getSelectionModel().isEmpty()) {
			matchList.getSelectionModel().select(best);

			announceSelectionChange(oldSelection, best != null ? best.getSubject() : null);
		} else {
			announceSelectionChange(oldSelection, matchList.getSelectionModel().getSelectedItem().getSubject());
		}

		suppressChangeEvents = false;
	}

	@SuppressWarnings("unchecked")
	private Boolean evalFilter(List<Object> stack, RankResult<? extends Matchable<?>> resB) {
		final byte OP_TYPE_NONE = 0;
		final byte OP_TYPE_ANY = 1;
		final byte OP_TYPE_MATCHABLE = 2;
		final byte OP_TYPE_CLASS = 3;
		final byte OP_TYPE_STRING = 4;
		final byte OP_TYPE_BOOL = 5;
		final byte OP_TYPE_INT = 6;
		final byte OP_TYPE_COMPARABLE = 7;

		String filterStr = filterField.getText();
		if (filterStr.isBlank()) return Boolean.TRUE;

		Matchable<?> itemB = resB.getSubject();
		Matchable<?> itemA;

		if (itemB instanceof ClassInstance) {
			itemA = srcPane.getSelectedClass();
		} else if (itemB instanceof MethodInstance) {
			itemA = srcPane.getSelectedMethod();
		} else if (itemB instanceof FieldInstance) {
			itemA = srcPane.getSelectedField();
		} else if (itemB instanceof MethodVarInstance) {
			itemA = srcPane.getSelectedMethodVar();
		} else {
			throw new IllegalStateException();
		}

		assert itemA != null;

		ClassEnv env = gui.getEnv().getEnvB();
		String[] parts = filterStr.split("\\s+");

		for (String part : parts) {
			String op = part.toLowerCase(Locale.ENGLISH);
			//System.out.printf("stack: %s, op: %s%n", stack, op);
			byte opTypeA = OP_TYPE_NONE;
			byte opTypeB = OP_TYPE_NONE;

			switch (op) {
			case "a":
			case "b":
				break;
			case "dup":
				opTypeA = OP_TYPE_ANY;
				break;
			case "name":
			case "mapped":
			case "mappedname":
				opTypeA = OP_TYPE_MATCHABLE;
				break;
			case "supercls":
				opTypeA = OP_TYPE_CLASS;
				break;
			case "instanceof":
				opTypeA = OP_TYPE_CLASS;
				opTypeB = OP_TYPE_CLASS;
				break;
			case "swap":
			case "eq":
			case "equals":
				opTypeA = opTypeB = OP_TYPE_ANY;
				break;
			case "and":
			case "or":
				opTypeA = opTypeB = OP_TYPE_BOOL;
				break;
			case "not":
				opTypeA = OP_TYPE_BOOL;
				break;
			case "startswith":
			case "endswith":
			case "contains":
				opTypeA =  opTypeB = OP_TYPE_STRING;
				break;
			case "class":
			case "package":
				opTypeA = OP_TYPE_STRING;
				break;
			default:
				if (part.length() >= 2 && part.charAt(0) == '"' && part.charAt(part.length() - 1) == '"') {
					part = part.substring(0, part.length() - 1);
				}

				stack.add(part);
				break;
			}

			Object opA = null;
			Object opB = null;

			for (int i = 0; i < 2; i++) {
				byte type = i == 0 ? opTypeB : opTypeA;
				if (type == OP_TYPE_NONE) continue;

				if (stack.isEmpty()) {
					System.err.println("stack underflow");
					return null;
				}

				Object operand = stack.remove(stack.size() - 1);

				boolean valid = type == OP_TYPE_ANY
						|| type == OP_TYPE_MATCHABLE && (operand instanceof RankResult<?> || operand instanceof Matchable<?>)
						|| type == OP_TYPE_CLASS && (operand instanceof RankResult<?> || operand instanceof ClassInstance || operand instanceof String)
						|| type == OP_TYPE_STRING && operand instanceof String
						|| type == OP_TYPE_BOOL && operand instanceof Boolean
						|| type == OP_TYPE_INT && operand instanceof Integer
						|| type == OP_TYPE_COMPARABLE && operand instanceof Comparable<?>;

				if (!valid) {
					System.err.println("invalid operand type");
					return null;
				}

				if (type == OP_TYPE_MATCHABLE && operand instanceof RankResult<?>) {
					operand = ((RankResult<? extends Matchable<?>>) operand).getSubject();
				} else if (type == OP_TYPE_CLASS && operand instanceof RankResult<?>) {
					operand = getClass(((RankResult<? extends Matchable<?>>) operand).getSubject());
				} else if (type == OP_TYPE_CLASS && operand instanceof String) {
					ClassInstance cls = env.getClsByName((String) operand);

					if (cls == null) {
						System.err.println("unknown class "+operand);
						return null;
					} else {
						operand = cls;
					}
				}

				if (i == 0) {
					opB = operand;
				} else {
					opA = operand;
				}
			}

			//System.out.printf("opA: %s, opB: %s%n", opA, opB);

			switch (op) {
			case "a":
				stack.add(itemA);
				break;
			case "b":
				stack.add(resB);
				break;
			case "dup":
				stack.add(opA);
				stack.add(opA);
				break;
			case "swap":
				stack.add(opB);
				stack.add(opA);
				break;
			case "name":
				stack.add(((Matchable<?>) opA).getName());
				break;
			case "mapped":
			case "mappedname":
				stack.add(((Matchable<?>) opA).getName(NameType.MAPPED_PLAIN));
				break;
			case "supercls":
				stack.add(((ClassInstance) opA).getSuperClass());
				break;
			case "instanceof":
				stack.add(((ClassInstance) opB).isAssignableFrom((ClassInstance) opA));
				break;
			case "eq":
			case "equals":
				stack.add(checkEquality(opA, opB, env));
				break;
			case "and":
				stack.add(Boolean.logicalAnd((Boolean) opA, (Boolean) opB));
				break;
			case "or":
				stack.add(Boolean.logicalOr((Boolean) opA, (Boolean) opB));
				break;
			case "not":
				stack.add(!((Boolean) opA));
				break;
			case "startswith":
				stack.add(((String) opA).startsWith((String) opB));
				break;
			case "endswith":
				stack.add(((String) opA).endsWith((String) opB));
				break;
			case "contains":
				stack.add(((String) opA).contains((String) opB));
				break;
			case "class": { // extract class (cls) from some/pkg/cls
				String s = (String) opA;
				stack.add(s.substring(s.lastIndexOf('/') + 1));
				break;
			}
			case "package": { // extract package (some/pkg) from some/pkg/cls
				String s = (String) opA;
				int end = s.lastIndexOf('/');
				stack.add(end < 0 ? s : s.substring(0, end));
				break;
			}
			}
		}

		//System.out.printf("res stack: %s%n", stack);

		if (stack.isEmpty() || stack.size() > 2) {
			System.err.println("no result");
			return null;
		} else if (stack.size() == 1) {
			if (stack.get(0) instanceof Boolean) {
				return (Boolean) stack.get(0);
			} else {
				System.err.println("invalid result");
				return null;
			}
		} else { // 2 elements on the stack, use equals
			return checkEquality(stack.get(0), stack.get(1), env);
		}
	}

	private static boolean checkEquality(Object a, Object b, ClassEnv env) {
		if (a == b) return true;
		if (a == null || b == null) return false;

		if (a.getClass() != b.getClass()) {
			if (a instanceof RankResult<?>) a = ((RankResult<?>) a).getSubject();
			if (b instanceof RankResult<?>) b = ((RankResult<?>) b).getSubject();
		}

		if (a.getClass() != b.getClass()) {
			if (a instanceof ClassInstance) {
				if (b instanceof Matchable<?>) {
					b = getClass((Matchable<?>) b);
				} else if (b instanceof String) {
					b = env.getClsByName((String) b);
				}
			}

			if (b instanceof ClassInstance) {
				if (a instanceof Matchable<?>) {
					a = getClass((Matchable<?>) a);
				} else if (a instanceof String) {
					a = env.getClsByName((String) a);
				}
			}
		}

		return Objects.equals(a, b);
	}

	private class SrcListener implements IGuiComponent {
		@Override
		public void onClassSelect(ClassInstance cls) {
			onSelect(null);
		}

		@Override
		public void onMethodSelect(MethodInstance method) {
			onSelect(null);
		}

		@Override
		public void onMethodVarSelect(MethodVarInstance arg) {
			onSelect(null);
		}

		@Override
		public void onFieldSelect(FieldInstance field) {
			onSelect(null);
		}

		void onSelect(Set<MatchType> matchChangeTypes) {
			Matchable<?> newSrcSelection = getMatchableSrcSelection();
			if (newSrcSelection == oldSrcSelection && matchChangeTypes == null) return;

			// update dst selection
			RankResult<? extends Matchable<?>> dstSelection = matchList.getSelectionModel().getSelectedItem();
			if (dstSelection != null) oldDstSelection = dstSelection.getSubject();

			// refresh list selection only early if it wasn't empty and the src class selection changed to suppress class selection changes
			// from (temporarily) clearing matchList and reentering onSelect while async ranking is ongoing

			if (oldDstSelection != null && (newSrcSelection == null || oldSrcSelection == null || MatchPaneDst.getClass(newSrcSelection) != MatchPaneDst.getClass(oldSrcSelection))) {
				announceSelectionChange(oldDstSelection, null);
				oldDstSelection = null;
			}

			oldSrcSelection = newSrcSelection;

			rankResults.clear();
			suppressChangeEvents = true;
			matchList.getItems().clear();
			suppressChangeEvents = false;

			ClassifierLevel matchLevel = gui.getMatcher().getAutoMatchLevel();
			ClassEnvironment env = gui.getEnv();
			double maxMismatch = Double.POSITIVE_INFINITY;

			Callable<List<? extends RankResult<? extends Matchable<?>>>> ranker;

			if (newSrcSelection == null) { // no class selected
				return;
			} else if (newSrcSelection instanceof ClassInstance) { // unmatched class or no member/method var selected
				ClassInstance cls = (ClassInstance) newSrcSelection;
				ranker = () -> ClassClassifier.rankParallel(cls, cmpClasses.toArray(new ClassInstance[0]), matchLevel, env, maxMismatch);
			} else if (newSrcSelection instanceof MethodInstance) { // unmatched method or no method var selected
				MethodInstance method = (MethodInstance) newSrcSelection;
				ranker = () -> MethodClassifier.rank(method, method.getCls().getMatch().getMethods(), matchLevel, env, maxMismatch);
			} else if (newSrcSelection instanceof FieldInstance) { // field
				FieldInstance field = (FieldInstance) newSrcSelection;
				ranker = () -> FieldClassifier.rank(field, field.getCls().getMatch().getFields(), matchLevel, env, maxMismatch);
			} else if (newSrcSelection instanceof MethodVarInstance) { // method arg/var
				MethodVarInstance var = (MethodVarInstance) newSrcSelection;
				MethodInstance cmpMethod = var.getMethod().getMatch();
				MethodVarInstance[] cmp = var.isArg() ? cmpMethod.getArgs() : cmpMethod.getVars();
				ranker = () -> MethodVarClassifier.rank(var, cmp, matchLevel, env, maxMismatch);
			} else {
				throw new IllegalStateException();
			}

			final int cTaskId = ++taskId;

			// update matches list
			Gui.runAsyncTask(ranker)
			.whenComplete((res, exc) -> {
				if (exc != null) {
					exc.printStackTrace();
				} else if (taskId == cTaskId) {
					assert rankResults.isEmpty();
					rankResults.addAll(res);

					updateResults(oldDstSelection);
					oldDstSelection = null;

					if (matchChangeTypes != null) {
						onMatchChangeApply(matchChangeTypes);
					}
				}
			});
		}

		private Matchable<?> getMatchableSrcSelection() {
			Matchable<?> ret = srcPane.getSelectedMethodVar();

			if (ret == null) {
				ret = srcPane.getSelectedMember();

				if (ret == null) {
					ret = srcPane.getSelectedClass();
				}
			}

			if (ret != null) {
				while (ret.getOwner() != null && !ret.getOwner().hasMatch()) {
					ret = ret.getOwner();
				}
			}

			return ret;
		}

		private int taskId;
		private Matchable<?> oldSrcSelection;
		private Matchable<?> oldDstSelection;
	}

	private final Gui gui;
	private final MatchPaneSrc srcPane;
	private final Collection<IGuiComponent> components = new ArrayList<>();
	private final ListView<RankResult<? extends Matchable<?>>> matchList = new ListView<>();
	private final TextField filterField = new TextField();
	private final List<RankResult<? extends Matchable<?>>> rankResults = new ArrayList<>();
	private final SrcListener srcListener = new SrcListener();
	private List<ClassInstance> cmpClasses;

	private boolean suppressChangeEvents;
}
