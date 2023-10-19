package matcher.gui.ui.tab;

import javafx.scene.control.Tab;
import javafx.scene.control.TableView;

import matcher.gui.ui.IGuiComponent;
import matcher.gui.ui.ISelectionProvider;
import matcher.model.classifier.ClassifierResult;
import matcher.model.classifier.RankResult;
import matcher.model.type.FieldInstance;
import matcher.model.type.MatchType;
import matcher.model.type.MemberInstance;
import matcher.model.type.MethodInstance;

public class MemberScoresTab extends Tab implements IGuiComponent {
	public MemberScoresTab(ISelectionProvider selectionProvider) {
		super("member classifiers");

		this.selectionProvider = selectionProvider;

		init();
	}

	private void init() {
		setContent(table);
	}

	@Override
	public void onMethodSelect(MethodInstance method) {
		update();
	}

	@Override
	public void onFieldSelect(FieldInstance field) {
		update();
	}

	@SuppressWarnings("unchecked")
	private void update() {
		RankResult<MemberInstance<?>> result = (RankResult<MemberInstance<?>>) selectionProvider.getSelectedRankResult(MatchType.Method);
		if (result == null) result = (RankResult<MemberInstance<?>>) selectionProvider.getSelectedRankResult(MatchType.Field);

		if (result == null) {
			table.getItems().clear();
		} else {
			table.getItems().setAll(result.getResults());
		}
	}

	private final ISelectionProvider selectionProvider;
	private final TableView<ClassifierResult<MemberInstance<?>>> table = ClassScoresTab.createClassifierTable();
}
