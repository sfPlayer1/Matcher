package matcher.gui.tab;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import matcher.classifier.ClassifierResult;
import matcher.classifier.RankResult;
import matcher.gui.IGuiComponent;
import matcher.gui.ISelectionProvider;
import matcher.type.ClassInstance;
import matcher.type.MatchType;

public class ClassScoresTab extends Tab implements IGuiComponent {
	public ClassScoresTab(ISelectionProvider selectionProvider) {
		super("class classifiers");

		this.selectionProvider = selectionProvider;

		init();
	}

	private void init() {
		setContent(table);
	}

	@Override
	public void onClassSelect(ClassInstance cls) {
		update();
	}

	private void update() {
		@SuppressWarnings("unchecked")
		RankResult<ClassInstance> result = (RankResult<ClassInstance>) selectionProvider.getSelectedRankResult(MatchType.Class);

		if (result == null) {
			table.getItems().clear();
		} else {
			table.getItems().setAll(result.getResults());
		}
	}

	static <T> TableView<ClassifierResult<T>> createClassifierTable() {
		TableView<ClassifierResult<T>> ret = new TableView<>();
		ret.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<ClassifierResult<T>, String> tab0 = new TableColumn<>("name");
		tab0.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getClassifier().getName()));
		ret.getColumns().add(tab0);

		TableColumn<ClassifierResult<T>, String> tab1 = new TableColumn<>("score");
		tab1.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(String.format("%.2f", data.getValue().getScore())));
		ret.getColumns().add(tab1);

		TableColumn<ClassifierResult<T>, Double> tab2 = new TableColumn<>("weight");
		tab2.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getClassifier().getWeight()));
		ret.getColumns().add(tab2);

		TableColumn<ClassifierResult<T>, String> tab3 = new TableColumn<>("w. score");
		tab3.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(String.format("%.2f", data.getValue().getScore() * data.getValue().getClassifier().getWeight())));
		ret.getColumns().add(tab3);

		ret.setItems(FXCollections.observableArrayList());

		return ret;
	}

	private final ISelectionProvider selectionProvider;
	private final TableView<ClassifierResult<ClassInstance>> table = createClassifierTable();
}
