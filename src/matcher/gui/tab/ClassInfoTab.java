package matcher.gui.tab;

import java.util.Collection;
import java.util.stream.Collectors;

import org.objectweb.asm.tree.ClassNode;

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
import matcher.type.ClassInstance;
import matcher.type.IMatchable;

public class ClassInfoTab extends Tab implements IGuiComponent {
	public ClassInfoTab(ISelectionProvider selectionProvider) {
		super("info");

		this.selectionProvider = selectionProvider;

		init();
	}

	private void init() {
		GridPane grid = new GridPane();
		grid.setPadding(new Insets(GuiConstants.padding));
		grid.setHgap(GuiConstants.padding);
		grid.setVgap(GuiConstants.padding);
		int row = 0;

		row = addRow("Name", nameLabel, grid, row);
		row = addRow("Access", accessLabel, grid, row);
		row = addRow("Signature", sigLabel, grid, row);
		row = addRow("Outer Class", outerLabel, grid, row);
		row = addRow("Super Class", superLabel, grid, row);
		row = addRow("Sub Classes", subLabel, grid, row);
		row = addRow("Interfaces", ifaceLabel, grid, row);
		row = addRow("Implementers", implLabel, grid, row);
		row = addRow("Ref. Methods", refMethodLabel, grid, row);
		row = addRow("Ref. Fields", refFieldLabel, grid, row);
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
		update(selectionProvider.getSelectedClass());
	}

	@Override
	public void onClassSelect(ClassInstance cls) {
		update(cls);
	}

	private void update(ClassInstance cls) {
		if (cls == null) {
			nameLabel.setText("-");
			accessLabel.setText("-");
			sigLabel.setText("-");
			outerLabel.setText("-");
			superLabel.setText("-");
			subLabel.setText("-");
			ifaceLabel.setText("-");
			implLabel.setText("-");
			refMethodLabel.setText("-");
			refFieldLabel.setText("-");
			mapCommentLabel.setText("-");
		} else {
			nameLabel.setText(getName(cls));
			accessLabel.setText(Util.formatAccessFlags(cls.getAccess(), AFElementType.Class));

			ClassNode asmNode = cls.getMergedAsmNode();
			sigLabel.setText(asmNode == null || asmNode.signature == null ? "-" : asmNode.signature);

			outerLabel.setText(cls.getOuterClass() != null ? getName(cls.getOuterClass()) : "-");
			superLabel.setText(cls.getSuperClass() != null ? getName(cls.getSuperClass()) : "-");
			subLabel.setText(cls.isInterface() ? "-" : format(cls.getChildClasses()));
			ifaceLabel.setText(format(cls.getInterfaces()));
			implLabel.setText(cls.isInterface() ? format(cls.getImplementers()) : "-");
			refMethodLabel.setText(format(cls.getMethodTypeRefs()));
			refFieldLabel.setText(format(cls.getFieldTypeRefs()));
			mapCommentLabel.setText(cls.getMappedComment() != null ? cls.getMappedComment() : "-");
		}
	}

	static String format(Collection<? extends IMatchable<?>> c) {
		return c.stream().map(ClassInfoTab::getName).sorted().collect(Collectors.joining("\n"));
	}

	static String getName(IMatchable<?> c) {
		String ret = c.getDisplayName(true, false);
		String mapped = c.getDisplayName(true, true);

		if (ret.equals(mapped)) {
			return ret;
		} else {
			return ret+" - "+mapped;
		}
	}

	private final ISelectionProvider selectionProvider;

	private final Label nameLabel = new Label();
	private final Label accessLabel = new Label();
	private final Label sigLabel = new Label();
	private final Label outerLabel = new Label();
	private final Label superLabel = new Label();
	private final Label subLabel = new Label();
	private final Label ifaceLabel = new Label();
	private final Label implLabel = new Label();
	private final Label refMethodLabel = new Label();
	private final Label refFieldLabel = new Label();
	private final Label mapCommentLabel = new Label();
}
