package matcher.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javafx.geometry.Orientation;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import matcher.classifier.ClassClassifier;
import matcher.classifier.FieldClassifier;
import matcher.classifier.MethodClassifier;
import matcher.classifier.RankResult;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.IMatchable;
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

		// lists

		SplitPane verticalPane = new SplitPane();
		getItems().add(verticalPane);

		// class list

		classList.setCellFactory(new MatchableListCellFactory<ClassInstance>());
		classList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			if (suppressChangeEvents || oldValue == newValue) return;

			onClassSelect(newValue != null ? newValue.getSubject() : null);
		});

		verticalPane.getItems().add(classList);

		// member list

		memberList.setCellFactory(new MatchableListCellFactory<MemberInstance<?>>());
		memberList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			if (suppressChangeEvents || oldValue == newValue) return;

			boolean wasMethod = oldValue != null && oldValue.getSubject() instanceof MethodInstance;
			boolean wasField = oldValue != null && oldValue.getSubject() instanceof FieldInstance;
			boolean isMethod = newValue != null && newValue.getSubject() instanceof MethodInstance;
			boolean isField = newValue != null && newValue.getSubject() instanceof FieldInstance;

			if (wasMethod && isField
					|| wasField && isMethod) {
				if (wasMethod) {
					onMethodSelect(null);
				} else {
					onFieldSelect(null);
				}
			}

			if (isMethod || newValue == null && wasMethod) {
				onMethodSelect(isMethod ? (MethodInstance) newValue.getSubject() : null);
			} else {
				onFieldSelect(isField ? (FieldInstance) newValue.getSubject() : null);
			}
		});

		verticalPane.getItems().add(memberList);

		// positioning

		verticalPane.setOrientation(Orientation.VERTICAL);
		verticalPane.setDividerPosition(0, 0.65);

		SplitPane.setResizableWithParent(verticalPane, false);
		setDividerPosition(0, 1 - 0.25);

		srcPane.addListener(srcListener);
	}

	private class MatchableListCellFactory<T extends IMatchable<? extends T>> extends ListCellFactory<RankResult<T>> {
		@Override
		protected String getText(RankResult<T> item) {
			boolean full = item.getSubject() instanceof ClassInstance;

			return String.format("%.3f %s", item.getScore(), item.getSubject().getDisplayName(full, false, gui.isTmpNamed(), true));
		}
	}

	@Override
	public ClassInstance getSelectedClass() {
		RankResult<ClassInstance> result = classList.getSelectionModel().getSelectedItem();

		return result != null ? result.getSubject() : null;
	}

	@Override
	public MethodInstance getSelectedMethod() {
		RankResult<MemberInstance<?>> result = memberList.getSelectionModel().getSelectedItem();
		if (result == null) return null;

		return result.getSubject() instanceof MethodInstance ? (MethodInstance) result.getSubject() : null;
	}

	@Override
	public FieldInstance getSelectedField() {
		RankResult<MemberInstance<?>> result = memberList.getSelectionModel().getSelectedItem();
		if (result == null) return null;

		return result.getSubject() instanceof FieldInstance ? (FieldInstance) result.getSubject() : null;
	}

	@Override
	public MethodVarInstance getSelectedMethodArg() {
		return null;
	}

	@Override
	public RankResult<?> getSelectedRankResult(MatchType type) {
		switch (type) {
		case Class:
			return classList.getSelectionModel().getSelectedItem();
		case Method:
		case Field: {
			RankResult<MemberInstance<?>> result = memberList.getSelectionModel().getSelectedItem();

			if (result == null
					|| type == MatchType.Method && !(result.getSubject() instanceof MethodInstance)
					|| type == MatchType.Field && !(result.getSubject() instanceof FieldInstance)) {
				return null;
			} else {
				return result;
			}
		}
		case MethodArg:
			return null;
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

		Comparator<RankResult<? extends IMatchable<?>>> cmp;

		if (gui.isSortMatchesAlphabetically()) {
			cmp = getNameComparator();
		} else {
			cmp = getScoreComparator();
		}

		memberList.getItems().sort(cmp);
		classList.getItems().sort(cmp);

		suppressChangeEvents = false;

		IFwdGuiComponent.super.onViewChange();
	}

	@Override
	public void onMatchChange(Set<MatchType> types) {
		if (types.contains(MatchType.Class)) {
			srcListener.onClassSelect(srcPane.getSelectedClass());
		}

		if (types.contains(MatchType.Method)) {
			MethodInstance m = srcPane.getSelectedMethod();
			if (m != null) srcListener.onMethodSelect(m);
		}

		if (types.contains(MatchType.Field)) {
			FieldInstance m = srcPane.getSelectedField();
			if (m != null) srcListener.onFieldSelect(m);
		}

		IFwdGuiComponent.super.onMatchChange(types);
	}

	@Override
	public Collection<IGuiComponent> getComponents() {
		return components;
	}

	private static Comparator<RankResult<? extends IMatchable<?>>> getNameComparator() {
		return Comparator.comparing(r -> r.getSubject().getName());
	}

	private static Comparator<RankResult<? extends IMatchable<?>>> getScoreComparator() {
		return Comparator.<RankResult<? extends IMatchable<?>>>comparingDouble(r -> r.getScore()).reversed();
	}

	private class SrcListener implements IGuiComponent {
		@Override
		public void onClassSelect(ClassInstance cls) {
			final int cClassId = ++classId;

			classList.getItems().clear();
			memberList.getItems().clear();

			if (cls == null) return;
			if (cmpClasses == null) return;

			Gui.runAsyncTask(() -> ClassClassifier.rankParallel(cls, cmpClasses.toArray(new ClassInstance[0]), gui.getMatcher().getAutoMatchLevel(), gui.getEnv(), Double.POSITIVE_INFINITY))
			.whenComplete((res, exc) -> {
				if (exc != null) {
					exc.printStackTrace();
				} else if (cClassId == classId) {
					RankResult<ClassInstance> best;

					if (!res.isEmpty()) {
						best = res.get(0);

						if (gui.isSortMatchesAlphabetically()) {
							res.sort(getNameComparator());
						}
					} else {
						best = null;
					}

					classList.getItems().setAll(res);

					/*if (prevMatchSelection != null) { // reselect the previously selected entry
						for (int i = 0; i < classList.getItems().size(); i++) {
							if (classList.getItems().get(i).getSubject() == prevMatchSelection) {
								classList.getSelectionModel().select(i);
								break;
							}
						}
					}*/

					if (classList.getSelectionModel().isEmpty()) {
						classList.getSelectionModel().select(best);
					}
				}
			});
		}

		@Override
		public void onMethodSelect(MethodInstance method) {
			onMemberSelect(method);
		}

		@Override
		public void onFieldSelect(FieldInstance field) {
			onMemberSelect(field);
		}

		@SuppressWarnings("unchecked")
		private void onMemberSelect(MemberInstance<?> member) {
			final int cMemberId = ++memberId;

			memberList.getItems().clear();

			if (member == null) return;
			if (member.getCls().getMatch() == null) return;

			Callable<List<? extends RankResult<? extends MemberInstance<?>>>> ranker;

			if (member instanceof MethodInstance) {
				ranker = () -> MethodClassifier.rank((MethodInstance) member, member.getCls().getMatch().getMethods(), gui.getMatcher().getAutoMatchLevel(), gui.getEnv());
			} else {
				ranker = () -> FieldClassifier.rank((FieldInstance) member, member.getCls().getMatch().getFields(), gui.getMatcher().getAutoMatchLevel(), gui.getEnv());
			}

			// update matches list
			Gui.runAsyncTask(ranker)
			.whenComplete((res, exc) -> {
				if (exc != null) {
					exc.printStackTrace();
				} else if (cMemberId == memberId) {
					RankResult<MemberInstance<?>> best;

					if (!res.isEmpty()) {
						best = (RankResult<MemberInstance<?>>) res.get(0);

						if (gui.isSortMatchesAlphabetically()) {
							res.sort(getNameComparator());
						}
					} else {
						best = null;
					}

					memberList.getItems().setAll((List<RankResult<MemberInstance<?>>>) res);

					/*if (prevMatchSelection != null) { // reselect the previously selected entry
						for (int i = 0; i < memberList.getItems().size(); i++) {
							if (memberList.getItems().get(i).getSubject() == prevMatchSelection) {
								memberList.getSelectionModel().select(i);
								break;
							}
						}
					}*/

					if (memberList.getSelectionModel().isEmpty()) {
						memberList.getSelectionModel().select(best);
					}
				}
			});
		}

		private int classId;
		private int memberId;
	}

	private final Gui gui;
	private final MatchPaneSrc srcPane;
	private final Collection<IGuiComponent> components = new ArrayList<>();
	private final ListView<RankResult<ClassInstance>> classList = new ListView<>();
	private final ListView<RankResult<MemberInstance<?>>> memberList = new ListView<>();
	private final SrcListener srcListener = new SrcListener();
	private List<ClassInstance> cmpClasses;

	private boolean suppressChangeEvents;
}
