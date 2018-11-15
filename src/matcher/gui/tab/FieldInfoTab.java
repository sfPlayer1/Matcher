package matcher.gui.tab;

import static matcher.gui.tab.ClassInfoTab.nullToMissing;

import java.util.Collection;

import org.objectweb.asm.tree.FieldNode;

import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.GridPane;
import matcher.Util;
import matcher.Util.AFElementType;
import matcher.gui.Gui;
import matcher.gui.GuiConstants;
import matcher.gui.IGuiComponent;
import matcher.gui.ISelectionProvider;
import matcher.type.FieldInstance;
import matcher.type.IMatchable;

public class FieldInfoTab extends Tab implements IGuiComponent {
	public FieldInfoTab(Gui gui, ISelectionProvider selectionProvider, boolean unmatchedTmp) {
		super("field info");

		this.gui = gui;
		this.selectionProvider = selectionProvider;
		this.unmatchedTmp = unmatchedTmp;

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
		row = addRow("Tmp name", tmpNameLabel, grid, row);
		row = addRow("Mapped name", mappedNameLabel, grid, row);
		row = addRow("UID", uidLabel, grid, row);
		row = addRow("Name obf.", nameObfLabel, grid, row);
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
			tmpNameLabel.setText("-");
			mappedNameLabel.setText("-");
			uidLabel.setText("-");
			nameObfLabel.setText("-");
			typeLabel.setText("-");
			accessLabel.setText("-");
			sigLabel.setText("-");
			parentLabel.setText("-");
			childLabel.setText("-");
			readRefLabel.setText("-");
			writeRefLabel.setText("-");
			mapCommentLabel.setText("-");
		} else {
			ownerLabel.setText(getName(field.getCls()));
			nameLabel.setText(field.getName());
			tmpNameLabel.setText(nullToMissing(field.getTmpName(unmatchedTmp)));
			mappedNameLabel.setText(nullToMissing(field.getMappedName()));
			uidLabel.setText(field.getUid() < 0 ? "-" : Integer.toString(field.getUid()));
			nameObfLabel.setText(Boolean.toString(field.isNameObfuscated()));
			typeLabel.setText(getName(field.getType()));
			accessLabel.setText(Util.formatAccessFlags(field.getAccess(), AFElementType.Method));

			FieldNode asmNode = field.getAsmNode();
			sigLabel.setText(asmNode == null || asmNode.signature == null ? "-" : asmNode.signature);

			parentLabel.setText(!field.getParents().isEmpty() ? format(field.getParents()) : "-");
			childLabel.setText(!field.isFinal() ? format(field.getChildren()) : "-");
			readRefLabel.setText(format(field.getReadRefs()));
			writeRefLabel.setText(format(field.getWriteRefs()));
			mapCommentLabel.setText(field.getMappedComment() != null ? field.getMappedComment() : "-");
		}
	}

	private String format(Collection<? extends IMatchable<?>> c) {
		return ClassInfoTab.format(c, gui.isTmpNamed(), unmatchedTmp);
	}

	private String getName(IMatchable<?> m) {
		return ClassInfoTab.getName(m, gui.isTmpNamed(), unmatchedTmp);
	}

	private final Gui gui;
	private final ISelectionProvider selectionProvider;
	private final boolean unmatchedTmp;

	private final Label ownerLabel = new Label();
	private final Label nameLabel = new Label();
	private final Label tmpNameLabel = new Label();
	private final Label mappedNameLabel = new Label();
	private final Label uidLabel = new Label();
	private final Label nameObfLabel = new Label();
	private final Label typeLabel = new Label();
	private final Label accessLabel = new Label();
	private final Label sigLabel = new Label();
	private final Label parentLabel = new Label();
	private final Label childLabel = new Label();
	private final Label readRefLabel = new Label();
	private final Label writeRefLabel = new Label();
	private final Label mapCommentLabel = new Label();
}
