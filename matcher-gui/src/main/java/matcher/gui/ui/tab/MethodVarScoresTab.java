package matcher.gui.ui.tab;

import javafx.scene.control.Tab;
import javafx.scene.control.TableView;

import matcher.gui.ui.IGuiComponent;
import matcher.gui.ui.ISelectionProvider;
import matcher.model.classifier.ClassifierResult;
import matcher.model.classifier.RankResult;
import matcher.model.type.MatchType;
import matcher.model.type.MethodVarInstance;

public class MethodVarScoresTab extends Tab implements IGuiComponent {
	public MethodVarScoresTab(ISelectionProvider selectionProvider) {
		super("var classifiers");

		this.selectionProvider = selectionProvider;

		init();
	}

	private void init() {
		setContent(table);
	}

	@Override
	public void onMethodVarSelect(MethodVarInstance arg) {
		update();
	}

	@SuppressWarnings("unchecked")
	private void update() {
		RankResult<MethodVarInstance> result = (RankResult<MethodVarInstance>) selectionProvider.getSelectedRankResult(MatchType.MethodVar);

		if (result == null) {
			table.getItems().clear();
		} else {
			table.getItems().setAll(result.getResults());
		}
	}

	private final ISelectionProvider selectionProvider;
	private final TableView<ClassifierResult<MethodVarInstance>> table = ClassScoresTab.createClassifierTable();
}
