package matcher.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import matcher.classifier.ClassClassifier;
import matcher.classifier.ClassifierLevel;
import matcher.classifier.FieldClassifier;
import matcher.classifier.MethodClassifier;
import matcher.classifier.MethodVarClassifier;
import matcher.classifier.RankResult;
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

		// match list

		matchList.setCellFactory(ignore -> new DstListCell());
		matchList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			if (suppressChangeEvents || oldValue == newValue) return;

			Matchable<?> oldSel = oldValue != null ? oldValue.getSubject() : null;
			Matchable<?> newSel = newValue != null ? newValue.getSubject() : null;

			announceSelectionChange(oldSel, newSel);
		});

		getItems().add(matchList);

		// positioning

		SplitPane.setResizableWithParent(matchList, false);
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

			if (oldDstSelection != null && (newSrcSelection == null || MatchPaneDst.getClass(newSrcSelection) != MatchPaneDst.getClass(oldSrcSelection))) {
				announceSelectionChange(oldDstSelection, null);
				oldDstSelection = null;
			}

			oldSrcSelection = newSrcSelection;

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
					RankResult<? extends Matchable<?>> best;

					if (!res.isEmpty()) {
						best = res.get(0);

						if (gui.isSortMatchesAlphabetically()) {
							res.sort(getNameComparator());
						}
					} else {
						best = null;
					}

					suppressChangeEvents = true;

					matchList.getItems().setAll(res);

					if (matchList.getSelectionModel().isEmpty()) {
						matchList.getSelectionModel().select(best);

						announceSelectionChange(oldDstSelection, best != null ? best.getSubject() : null);
					} else {
						announceSelectionChange(oldDstSelection, matchList.getSelectionModel().getSelectedItem().getSubject());
					}

					suppressChangeEvents = false;

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
	private final SrcListener srcListener = new SrcListener();
	private List<ClassInstance> cmpClasses;

	private boolean suppressChangeEvents;
}
