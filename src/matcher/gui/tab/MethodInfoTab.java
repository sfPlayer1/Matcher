package matcher.gui.tab;

import static matcher.gui.tab.ClassInfoTab.format;
import static matcher.gui.tab.ClassInfoTab.getName;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import org.objectweb.asm.tree.MethodNode;

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
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class MethodInfoTab extends Tab implements IGuiComponent {
	public MethodInfoTab(Gui gui, ISelectionProvider selectionProvider, boolean unmatchedTmp) {
		super("method info");

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
		row = addRow("Args", argLabel, grid, row);
		row = addRow("Ret type", retTypeLabel, grid, row);
		row = addRow("Access", accessLabel, grid, row);
		row = addRow("Signature", sigLabel, grid, row);
		row = addRow("Parents", parentLabel, grid, row);
		row = addRow("Children", childLabel, grid, row);
		row = addRow("Hierarchy", hierarchyLabel, grid, row);
		row = addRow("Local vars", varLabel, grid, row);
		row = addRow("Refs in", refMethodInLabel, grid, row);
		row = addRow("Refs out", refMethodOutLabel, grid, row);
		row = addRow("Fields read", refFieldReadLabel, grid, row);
		row = addRow("Fields written", refFieldWriteLabel, grid, row);
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
		update(selectionProvider.getSelectedMethod());
	}

	@Override
	public void onMethodSelect(MethodInstance method) {
		update(method);
	}

	@Override
	public void onViewChange() {
		update(selectionProvider.getSelectedMethod());
	}

	private void update(MethodInstance method) {
		if (method == null) {
			ownerLabel.setText("-");
			nameLabel.setText("-");
			tmpNameLabel.setText("-");
			mappedNameLabel.setText("-");

			for (int i = 0; i < NameType.AUX_COUNT; i++) {
				auxNameLabels[i].setText("-");
			}

			uidLabel.setText("-");
			nameObfLabel.setText("-");
			argLabel.setText("-");
			retTypeLabel.setText("-");
			accessLabel.setText("-");
			sigLabel.setText("-");
			parentLabel.setText("-");
			childLabel.setText("-");
			hierarchyLabel.setText("-");
			varLabel.setText("-");
			refMethodInLabel.setText("-");
			refMethodOutLabel.setText("-");
			refFieldReadLabel.setText("-");
			refFieldWriteLabel.setText("-");
			mapCommentLabel.setText("-");
		} else {
			NameType nameType = gui.getNameType().withUnmatchedTmp(unmatchedTmp);

			ownerLabel.setText(getName(method.getCls(), nameType));
			nameLabel.setText(method.getName());
			tmpNameLabel.setText(method.hasLocalTmpName() ? method.getName(NameType.LOCTMP_PLAIN) : "-");
			mappedNameLabel.setText(method.hasMappedName() ? method.getName(NameType.MAPPED) : "-");

			for (int i = 0; i < NameType.AUX_COUNT; i++) {
				auxNameLabels[i].setText(method.hasAuxName(i) ? method.getName(NameType.getAux(i)) : "-");
			}

			uidLabel.setText(method.getUid() >= 0 ? Integer.toString(method.getUid()) : "-");
			nameObfLabel.setText(Boolean.toString(method.isNameObfuscated()));
			argLabel.setText(Arrays.stream(method.getArgs()).map(a -> getVarName(a, nameType)).collect(Collectors.joining("\n")));
			retTypeLabel.setText(getName(method.getRetType(), nameType));
			accessLabel.setText(Util.formatAccessFlags(method.getAccess(), AFElementType.Method));

			MethodNode asmNode = method.getAsmNode();
			sigLabel.setText(asmNode == null || asmNode.signature == null ? "-" : asmNode.signature);

			parentLabel.setText(!method.getParents().isEmpty() ? formatClass(method.getParents(), nameType) : "-");
			childLabel.setText(!method.isFinal() ? formatClass(method.getChildren(), nameType) : "-");

			if (method.getAllHierarchyMembers() != null && method.getAllHierarchyMembers().size() > 1) {
				hierarchyLabel.setText(format(method.getAllHierarchyMembers().stream().filter(m -> m != method).map(MethodInstance::getCls), nameType));
			} else {
				hierarchyLabel.setText("-");
			}

			varLabel.setText(Arrays.stream(method.getVars()).map(a -> getVarName(a, nameType)).collect(Collectors.joining("\n")));

			refMethodInLabel.setText(format(method.getRefsIn(), nameType));
			refMethodOutLabel.setText(format(method.getRefsOut(), nameType));
			refFieldReadLabel.setText(format(method.getFieldReadRefs(), nameType));
			refFieldWriteLabel.setText(format(method.getFieldWriteRefs(), nameType));
			mapCommentLabel.setText(method.getMappedComment() != null ? method.getMappedComment() : "-");
		}
	}

	private static String getVarName(MethodVarInstance arg, NameType nameType) {
		return arg.getIndex()+": "+getName(arg, nameType)+" ("+getName(arg.getType(), nameType)+")";
	}


	static String formatClass(Collection<? extends MemberInstance<?>> c, NameType nameType) {
		return c.stream().map(v -> getName(v.getCls(), nameType)).sorted().collect(Collectors.joining("\n"));
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
	private final Label argLabel = new Label();
	private final Label retTypeLabel = new Label();
	private final Label accessLabel = new Label();
	private final Label sigLabel = new Label();
	private final Label parentLabel = new Label();
	private final Label childLabel = new Label();
	private final Label hierarchyLabel = new Label();
	private final Label varLabel = new Label();
	private final Label refMethodInLabel = new Label();
	private final Label refMethodOutLabel = new Label();
	private final Label refFieldReadLabel = new Label();
	private final Label refFieldWriteLabel = new Label();
	private final Label mapCommentLabel = new Label();
}
