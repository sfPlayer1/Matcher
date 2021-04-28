package matcher.gui.tab;

import javafx.scene.control.Tab;
import javafx.scene.control.TableView;

import matcher.classifier.ClassifierResult;
import matcher.classifier.RankResult;
import matcher.gui.IGuiComponent;
import matcher.gui.ISelectionProvider;
import matcher.type.MatchType;
import matcher.type.MethodVarInstance;

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
