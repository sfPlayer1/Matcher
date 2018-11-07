package matcher.gui.tab;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import org.objectweb.asm.tree.MethodNode;

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
import matcher.type.IMatchable;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class MethodInfoTab extends Tab implements IGuiComponent {
	public MethodInfoTab(Gui gui, ISelectionProvider selectionProvider, boolean unmatchedTmp) {
		super("method info");

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
		row = addRow("Name obf.", nameObfLabel, grid, row);
		row = addRow("Args", argLabel, grid, row);
		row = addRow("Ret type", retTypeLabel, grid, row);
		row = addRow("Access", accessLabel, grid, row);
		row = addRow("Signature", sigLabel, grid, row);
		row = addRow("Parents", parentLabel, grid, row);
		row = addRow("Children", childLabel, grid, row);
		row = addRow("Refs in", refMethodInLabel, grid, row);
		row = addRow("Refs out", refMethodOutLabel, grid, row);
		row = addRow("Fields read", refFieldReadLabel, grid, row);
		row = addRow("Fields written", refFieldWriteLabel, grid, row);
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
		update(selectionProvider.getSelectedMethod());
	}

	@Override
	public void onMethodSelect(MethodInstance method) {
		update(method);
	}

	private void update(MethodInstance method) {
		if (method == null) {
			ownerLabel.setText("-");
			nameLabel.setText("-");
			tmpNameLabel.setText("-");
			mappedNameLabel.setText("-");
			nameObfLabel.setText("-");
			argLabel.setText("-");
			retTypeLabel.setText("-");
			accessLabel.setText("-");
			sigLabel.setText("-");
			parentLabel.setText("-");
			childLabel.setText("-");
			refMethodInLabel.setText("-");
			refMethodOutLabel.setText("-");
			refFieldReadLabel.setText("-");
			refFieldWriteLabel.setText("-");
			mapCommentLabel.setText("-");
		} else {
			ownerLabel.setText(getName(method.getCls()));
			String name = method.getDisplayName(false, false, false, true);
			String tmpName = method.getDisplayName(false, false, true, unmatchedTmp);
			String mappedName = method.getDisplayName(false, false, gui.isTmpNamed(), unmatchedTmp);
			nameLabel.setText(name);
			tmpNameLabel.setText(tmpName.equals(name) ? "-" : tmpName);
			mappedNameLabel.setText(mappedName.equals(gui.isTmpNamed() ? tmpName : name) ? "-" : mappedName);
			nameObfLabel.setText(Boolean.toString(method.isNameObfuscated(false)));
			argLabel.setText(Arrays.stream(method.getArgs()).map(this::getVarName).collect(Collectors.joining("\n")));
			retTypeLabel.setText(getName(method.getRetType()));
			accessLabel.setText(Util.formatAccessFlags(method.getAccess(), AFElementType.Method));

			MethodNode asmNode = method.getAsmNode();
			sigLabel.setText(asmNode == null || asmNode.signature == null ? "-" : asmNode.signature);


			parentLabel.setText(!method.getParents().isEmpty() ? format(method.getParents()) : "-");
			childLabel.setText(!method.isFinal() ? format(method.getChildren()) : "-");
			refMethodInLabel.setText(format(method.getRefsIn()));
			refMethodOutLabel.setText(format(method.getRefsOut()));
			refFieldReadLabel.setText(format(method.getFieldReadRefs()));
			refFieldWriteLabel.setText(format(method.getFieldWriteRefs()));
			mapCommentLabel.setText(method.getMappedComment() != null ? method.getMappedComment() : "-");
		}
	}

	private String getVarName(MethodVarInstance arg) {
		return arg.getIndex()+": "+getName(arg)+" ("+getName(arg.getType())+")";
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
	private final Label nameObfLabel = new Label();
	private final Label argLabel = new Label();
	private final Label retTypeLabel = new Label();
	private final Label accessLabel = new Label();
	private final Label sigLabel = new Label();
	private final Label parentLabel = new Label();
	private final Label childLabel = new Label();
	private final Label refMethodInLabel = new Label();
	private final Label refMethodOutLabel = new Label();
	private final Label refFieldReadLabel = new Label();
	private final Label refFieldWriteLabel = new Label();
	private final Label mapCommentLabel = new Label();
}
