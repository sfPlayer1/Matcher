package matcher.gui.tab;

import static matcher.gui.tab.ClassInfoTab.getName;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.GridPane;
import matcher.NameType;
import matcher.gui.Gui;
import matcher.gui.GuiConstants;
import matcher.gui.IGuiComponent;
import matcher.gui.ISelectionProvider;
import matcher.type.MethodVarInstance;

public class VarInfoTab extends Tab implements IGuiComponent {
	public VarInfoTab(Gui gui, ISelectionProvider selectionProvider, boolean unmatchedTmp) {
		super("var info");

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

		row = addRow("Kind", kindLabel, grid, row);
		row = addRow("Method", methodLabel, grid, row);
		row = addRow("Name", nameLabel, grid, row);
		row = addRow("Tmp name", tmpNameLabel, grid, row);
		row = addRow("Mapped name", mappedNameLabel, grid, row);

		for (int i = 0; i < NameType.AUX_COUNT; i++) {
			row = addRow("AUX name "+(i+1), auxNameLabels[i], grid, row);
		}

		row = addRow("UID", uidLabel, grid, row);
		row = addRow("Name obf.", nameObfLabel, grid, row);
		row = addRow("Type", typeLabel, grid, row);
		row = addRow("Matcher ID", matcherIdLabel, grid, row);
		row = addRow("LV index", lvIndexLabel, grid, row);
		row = addRow("LVT index", lvtIndexLabel, grid, row);
		row = addRow("Start Op", startOpLabel, grid, row);
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
		update(selectionProvider.getSelectedMethodVar());
	}

	@Override
	public void onMethodVarSelect(MethodVarInstance var) {
		update(var);
	}

	@Override
	public void onViewChange() {
		update(selectionProvider.getSelectedMethodVar());
	}

	private void update(MethodVarInstance var) {
		if (var == null) {
			kindLabel.setText("-");
			methodLabel.setText("-");
			nameLabel.setText("-");
			tmpNameLabel.setText("-");
			mappedNameLabel.setText("-");

			for (int i = 0; i < NameType.AUX_COUNT; i++) {
				auxNameLabels[i].setText("-");
			}

			uidLabel.setText("-");
			nameObfLabel.setText("-");
			typeLabel.setText("-");
			matcherIdLabel.setText("-");
			lvIndexLabel.setText("-");
			lvtIndexLabel.setText("-");
			startOpLabel.setText("-");
			mapCommentLabel.setText("-");
		} else {
			NameType nameType = gui.getNameType().withUnmatchedTmp(unmatchedTmp);

			kindLabel.setText(var.isArg() ? "parameter" : "local variable");
			methodLabel.setText(getName(var.getMethod(), nameType));
			nameLabel.setText(var.getName());
			tmpNameLabel.setText(var.hasLocalTmpName() ? var.getName(NameType.LOCTMP_PLAIN) : "-");
			mappedNameLabel.setText(var.hasMappedName() ? var.getName(NameType.MAPPED) : "-");

			for (int i = 0; i < NameType.AUX_COUNT; i++) {
				auxNameLabels[i].setText(var.hasAuxName(i) ? var.getName(NameType.getAux(i)) : "-");
			}

			uidLabel.setText(var.getUid() >= 0 ? Integer.toString(var.getUid()) : "-");
			nameObfLabel.setText(Boolean.toString(var.isNameObfuscated()));
			typeLabel.setText(getName(var.getType(), nameType));
			matcherIdLabel.setText(var.getTypedId());
			lvIndexLabel.setText(Integer.toString(var.getLvIndex()));
			lvtIndexLabel.setText(Integer.toString(var.getAsmIndex()));
			startOpLabel.setText(Integer.toString(var.getStartOpIdx()));
			mapCommentLabel.setText(var.getMappedComment() != null ? var.getMappedComment() : "-");
		}
	}

	private final Gui gui;
	private final ISelectionProvider selectionProvider;
	private final boolean unmatchedTmp;

	private final Label kindLabel = new Label();
	private final Label methodLabel = new Label();
	private final Label nameLabel = new Label();
	private final Label tmpNameLabel = new Label();
	private final Label mappedNameLabel = new Label();
	private final Label[] auxNameLabels = new Label[NameType.AUX_COUNT];
	private final Label uidLabel = new Label();
	private final Label nameObfLabel = new Label();
	private final Label typeLabel = new Label();
	private final Label matcherIdLabel = new Label();
	private final Label lvIndexLabel = new Label();
	private final Label lvtIndexLabel = new Label();
	private final Label startOpLabel = new Label();
	private final Label mapCommentLabel = new Label();
}
