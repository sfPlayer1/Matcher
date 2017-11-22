package matcher.gui.tab;

import java.util.Arrays;
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
import matcher.gui.GuiConstants;
import matcher.gui.IGuiComponent;
import matcher.gui.ISelectionProvider;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class MethodInfoTab extends Tab implements IGuiComponent {
	public MethodInfoTab(ISelectionProvider selectionProvider) {
		super("method info");

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
		row = addRow("Args", argLabel, grid, row);
		row = addRow("Ret Type", retTypeLabel, grid, row);
		row = addRow("Access", accessLabel, grid, row);
		row = addRow("Signature", sigLabel, grid, row);
		row = addRow("Parents", parentLabel, grid, row);
		row = addRow("Children", childLabel, grid, row);
		row = addRow("Refs In", refMethodInLabel, grid, row);
		row = addRow("Refs Out", refMethodOutLabel, grid, row);
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
			ownerLabel.setText(ClassInfoTab.getName(method.getCls()));
			nameLabel.setText(ClassInfoTab.getName(method));
			argLabel.setText(Arrays.stream(method.getArgs()).map(MethodInfoTab::getVarName).collect(Collectors.joining("\n")));
			retTypeLabel.setText(ClassInfoTab.getName(method.getRetType()));
			accessLabel.setText(Util.formatAccessFlags(method.getAccess(), AFElementType.Method));

			MethodNode asmNode = method.getAsmNode();
			sigLabel.setText(asmNode == null || asmNode.signature == null ? "-" : asmNode.signature);


			parentLabel.setText(!method.getParents().isEmpty() ? ClassInfoTab.format(method.getParents()) : "-");
			childLabel.setText(!method.isFinal() ? ClassInfoTab.format(method.getChildren()) : "-");
			refMethodInLabel.setText(ClassInfoTab.format(method.getRefsIn()));
			refMethodOutLabel.setText(ClassInfoTab.format(method.getRefsOut()));
			refFieldReadLabel.setText(ClassInfoTab.format(method.getFieldReadRefs()));
			refFieldWriteLabel.setText(ClassInfoTab.format(method.getFieldWriteRefs()));
			mapCommentLabel.setText(method.getMappedComment() != null ? method.getMappedComment() : "-");
		}
	}

	private static String getVarName(MethodVarInstance arg) {
		return arg.getIndex()+": "+ClassInfoTab.getName(arg)+" ("+ClassInfoTab.getName(arg.getType())+")";
	}

	private final ISelectionProvider selectionProvider;

	private final Label ownerLabel = new Label();
	private final Label nameLabel = new Label();
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
