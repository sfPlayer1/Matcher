package matcher.gui.tab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import matcher.gui.IGuiComponent;
import matcher.type.ClassInstance;

public class HierarchyTab extends Tab implements IGuiComponent {
	public HierarchyTab() {
		super("hierarchy");

		init();
	}

	private void init() {
		VBox vBox = new VBox();
		vBox.getChildren().add(classHierarchyTree);
		vBox.getChildren().add(ifaceList);

		Callback<TreeView<ClassInstance>, TreeCell<ClassInstance>> cellFactory = tree -> new TreeCell<ClassInstance>() { // makes entries in highLights bold
			@Override
			protected void updateItem(ClassInstance item, boolean empty) {
				super.updateItem(item, empty);

				if (empty || item == null) {
					setText(null);
					setStyle("");
				} else {
					setText(item.toString());

					if (highLights.contains(item)) {
						setStyle("-fx-font-weight: bold;");
					} else {
						setStyle("");
					}
				}
			}
		};

		classHierarchyTree.setCellFactory(cellFactory);

		setContent(vBox);
	}

	@Override
	public void onClassSelect(ClassInstance cls) {
		update(cls);
	}

	private void update(ClassInstance cls) {
		// clear

		highLights.clear();
		classHierarchyTree.setRoot(null);
		ifaceList.getItems().clear();

		if (cls == null) {
			return;
		}

		// populate

		List<ClassInstance> hierarchy = new ArrayList<>();
		ClassInstance cCls = cls;

		while (cCls != null) {
			hierarchy.add(cCls);
			cCls = cCls.getSuperClass();
		}

		highLights.addAll(hierarchy);

		TreeItem<ClassInstance> parent = new TreeItem<>(hierarchy.get(hierarchy.size() - 1));
		classHierarchyTree.setRoot(parent);

		if (hierarchy.size() > 1) {
			parent.setExpanded(true);
		}

		for (int i = hierarchy.size() - 1; i >= 1; i--) {
			ClassInstance parentCls = hierarchy.get(i);
			ClassInstance nextCls = hierarchy.get(i - 1);
			TreeItem<ClassInstance> next = null;

			List<ClassInstance> items = new ArrayList<>(parentCls.getChildClasses());
			items.sort(Comparator.comparing(ClassInstance::toString));

			for (int j = 0; j < items.size() && j < 10; j++) {
				ClassInstance child = items.get(j);

				TreeItem<ClassInstance> treeItem = new TreeItem<>(child);
				parent.getChildren().add(treeItem);

				if (child == nextCls) {
					next = treeItem;
				}
			}

			if (next == null) {
				next = new TreeItem<>(nextCls);
				parent.getChildren().add(next);
			}

			next.setExpanded(true);

			if (i == 1) {
				classHierarchyTree.getSelectionModel().select(next);
			}

			parent = next;
		}

		if (!cls.getChildClasses().isEmpty()) {
			List<ClassInstance> items = new ArrayList<>(cls.getChildClasses());
			items.sort(Comparator.comparing(ClassInstance::toString));

			for (int j = 0; j < items.size() && j < 10; j++) {
				ClassInstance child = items.get(j);

				parent.getChildren().add(new TreeItem<>(child));
			}
		}

		if (!cls.getInterfaces().isEmpty()) {
			Set<ClassInstance> ifaces = new HashSet<>();
			Queue<ClassInstance> toCheck = new ArrayDeque<>();
			toCheck.add(cls);

			while ((cCls = toCheck.poll()) != null) {
				for (ClassInstance next : cCls.getInterfaces()) {
					if (ifaces.add(next)) toCheck.add(next);
				}
			}

			List<ClassInstance> sortedIfaces = new ArrayList<>(ifaces);
			sortedIfaces.sort(Comparator.comparing(ClassInstance::toString));
			ifaceList.getItems().setAll(sortedIfaces);
		}
	}

	private final TreeView<ClassInstance> classHierarchyTree = new TreeView<>();
	private final Set<ClassInstance> highLights = new HashSet<>();
	private final ListView<ClassInstance> ifaceList = new ListView<>();
}
