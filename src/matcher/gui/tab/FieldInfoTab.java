package matcher.gui.tab;

import org.objectweb.asm.tree.FieldNode;

import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.GridPane;
import matcher.Util;
import matcher.Util.AFElementType;
import matcher.gui.GuiConstants;
import matcher.gui.IGuiComponent;
import matcher.gui.ISelectionProvider;
import matcher.type.FieldInstance;

public class FieldInfoTab extends Tab implements IGuiComponent {
	public FieldInfoTab(ISelectionProvider selectionProvider) {
		super("field info");

		this.selectionProvider = selectionProvider;

		init();
	}

	private void init() {
		GridPane grid = new GridPane();
		grid.setPadding(new Insets(GuiConstants.padding));
		grid.setHgap(GuiConstants.padding);
		grid.setVgap(GuiConstants.padding);
		int row = 0;

		row = addRow("Owner", ownerLabel, grid, row);
		row = addRow("Name", nameLabel, grid, row);
		row = addRow("Type", typeLabel, grid, row);
		row = addRow("Access", accessLabel, grid, row);
		row = addRow("Signature", sigLabel, grid, row);
		row = addRow("Parents", parentLabel, grid, row);
		row = addRow("Children", childLabel, grid, row);
		row = addRow("Read refs", readRefLabel, grid, row);
		row = addRow("Write refs", writeRefLabel, grid, row);
		row = addRow("Comment", mapCommentLabel, grid, row);

		setContent(grid);
	}

	private static int addRow(String name, Node content, GridPane grid, int row) {
		Label label = new Label(name+":");
		label.setMinWidth(Label.USE_PREF_SIZE);
		grid.add(label, 0, row);
		GridPane.setValignment(label, VPos.TOP);

		grid.add(content, 1, row);

		return row + 1;
	}

	@Override
	public void onMappingChange() {
		update(selectionProvider.getSelectedField());
	}

	@Override
	public void onFieldSelect(FieldInstance field) {
		update(field);
	}

	private void update(FieldInstance field) {
		if (field == null) {
			ownerLabel.setText("-");
			nameLabel.setText("-");
			typeLabel.setText("-");
			accessLabel.setText("-");
			sigLabel.setText("-");
			parentLabel.setText("-");
			childLabel.setText("-");
			readRefLabel.setText("-");
			writeRefLabel.setText("-");
			mapCommentLabel.setText("-");
		} else {
			ownerLabel.setText(ClassInfoTab.getName(field.getCls()));
			nameLabel.setText(ClassInfoTab.getName(field));
			typeLabel.setText(ClassInfoTab.getName(field.getType()));
			accessLabel.setText(Util.formatAccessFlags(field.getAccess(), AFElementType.Method));

			FieldNode asmNode = field.getAsmNode();
			sigLabel.setText(asmNode == null || asmNode.signature == null ? "-" : asmNode.signature);

			parentLabel.setText(!field.getParents().isEmpty() ? ClassInfoTab.format(field.getParents()) : "-");
			childLabel.setText(!field.isFinal() ? ClassInfoTab.format(field.getChildren()) : "-");
			readRefLabel.setText(ClassInfoTab.format(field.getReadRefs()));
			writeRefLabel.setText(ClassInfoTab.format(field.getWriteRefs()));
			mapCommentLabel.setText(field.getMappedComment() != null ? field.getMappedComment() : "-");
		}
	}

	private final ISelectionProvider selectionProvider;

	private final Label ownerLabel = new Label();
	private final Label nameLabel = new Label();
	private final Label typeLabel = new Label();
	private final Label accessLabel = new Label();
	private final Label sigLabel = new Label();
	private final Label parentLabel = new Label();
	private final Label childLabel = new Label();
	private final Label readRefLabel = new Label();
	private final Label writeRefLabel = new Label();
	private final Label mapCommentLabel = new Label();
}
