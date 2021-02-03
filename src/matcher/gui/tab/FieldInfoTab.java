package matcher.gui.tab;

import static matcher.gui.tab.ClassInfoTab.format;
import static matcher.gui.tab.ClassInfoTab.getName;
import static matcher.gui.tab.MethodInfoTab.formatClass;

import org.objectweb.asm.tree.FieldNode;

import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.GridPane;
import matcher.NameType;
import matcher.Util;
import matcher.Util.AFElementType;
import matcher.gui.Gui;
import matcher.gui.GuiConstants;
import matcher.gui.IGuiComponent;
import matcher.gui.ISelectionProvider;
import matcher.type.FieldInstance;

public class FieldInfoTab extends Tab implements IGuiComponent {
	public FieldInfoTab(Gui gui, ISelectionProvider selectionProvider, boolean unmatchedTmp) {
		super("field info");

		this.gui = gui;
		this.selectionProvider = selectionProvider;
		this.unmatchedTmp = unmatchedTmp;

		for (int i = 0; i < NameType.AUX_COUNT; i++) {
			auxNameLabels[i] = new Label();
		}

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

		for (int i = 0; i < NameType.AUX_COUNT; i++) {
			row = addRow("AUX name "+(i+1), auxNameLabels[i], grid, row);
		}

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

		ScrollPane scroll = new ScrollPane(grid);
		setContent(scroll);
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

	@Override
	public void onViewChange() {
		update(selectionProvider.getSelectedField());
	}

	private void update(FieldInstance field) {
		if (field == null) {
			ownerLabel.setText("-");
			nameLabel.setText("-");
			tmpNameLabel.setText("-");
			mappedNameLabel.setText("-");

			for (int i = 0; i < NameType.AUX_COUNT; i++) {
				auxNameLabels[i].setText("-");
			}

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
			NameType nameType = gui.getNameType().withUnmatchedTmp(unmatchedTmp);

			ownerLabel.setText(getName(field.getCls(), nameType));
			nameLabel.setText(field.getName());
			tmpNameLabel.setText(field.hasLocalTmpName() ? field.getName(NameType.LOCTMP_PLAIN) : "-");
			mappedNameLabel.setText(field.hasMappedName() ? field.getName(NameType.MAPPED) : "-");

			for (int i = 0; i < NameType.AUX_COUNT; i++) {
				auxNameLabels[i].setText(field.hasAuxName(i) ? field.getName(NameType.getAux(i)) : "-");
			}

			uidLabel.setText(field.getUid() >= 0 ? Integer.toString(field.getUid()) : "-");
			nameObfLabel.setText(Boolean.toString(field.isNameObfuscated()));
			typeLabel.setText(getName(field.getType(), nameType));
			accessLabel.setText(Util.formatAccessFlags(field.getAccess(), AFElementType.Method));

			FieldNode asmNode = field.getAsmNode();
			sigLabel.setText(asmNode == null || asmNode.signature == null ? "-" : asmNode.signature);

			parentLabel.setText(!field.getParents().isEmpty() ? formatClass(field.getParents(), nameType) : "-");
			childLabel.setText(!field.isFinal() ?  formatClass(field.getChildren(), nameType) : "-");

			readRefLabel.setText(format(field.getReadRefs(), nameType));
			writeRefLabel.setText(format(field.getWriteRefs(), nameType));
			mapCommentLabel.setText(field.getMappedComment() != null ? field.getMappedComment() : "-");
		}
	}

	private final Gui gui;
	private final ISelectionProvider selectionProvider;
	private final boolean unmatchedTmp;

	private final Label ownerLabel = new Label();
	private final Label nameLabel = new Label();
	private final Label tmpNameLabel = new Label();
	private final Label mappedNameLabel = new Label();
	private final Label[] auxNameLabels = new Label[NameType.AUX_COUNT];
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
