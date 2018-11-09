package matcher.gui.tab;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

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
import matcher.type.ClassInstance;
import matcher.type.IMatchable;

public class ClassInfoTab extends Tab implements IGuiComponent {
	public ClassInfoTab(Gui gui, ISelectionProvider selectionProvider, boolean unmatchedTmp) {
		super("info");

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

		row = addRow("Name", nameLabel, grid, row);
		row = addRow("Tmp name", tmpNameLabel, grid, row);
		row = addRow("Mapped name", mappedNameLabel, grid, row);
		row = addRow("Name obf.", nameObfLabel, grid, row);
		row = addRow("Access", accessLabel, grid, row);
		row = addRow("Signature", sigLabel, grid, row);
		row = addRow("Outer class", outerLabel, grid, row);
		row = addRow("Super class", superLabel, grid, row);
		row = addRow("Sub classes", subLabel, grid, row);
		row = addRow("Interfaces", ifaceLabel, grid, row);
		row = addRow("Implementers", implLabel, grid, row);
		row = addRow("Ref. methods", refMethodLabel, grid, row);
		row = addRow("Ref. fields", refFieldLabel, grid, row);
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

	@Override
	public void onViewChange() {
		update(selectionProvider.getSelectedClass());
	}

	private void update(ClassInstance cls) {
		if (cls == null) {
			nameLabel.setText("-");
			tmpNameLabel.setText("-");
			mappedNameLabel.setText("-");
			nameObfLabel.setText("-");
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
			nameLabel.setText(cls.getName());
			tmpNameLabel.setText(nullToMissing(cls.getTmpName(unmatchedTmp)));
			mappedNameLabel.setText(nullToMissing(cls.getMappedName()));
			nameObfLabel.setText(Boolean.toString(cls.isNameObfuscated(false)));
			accessLabel.setText(Util.formatAccessFlags(cls.getAccess(), AFElementType.Class));

			if (cls.getSignature() == null) {
				sigLabel.setText("-");
			} else {
				String sig = cls.getSignature().toString(false, gui.isTmpNamed(), unmatchedTmp);
				String sigMapped = cls.getSignature().toString(true, gui.isTmpNamed(), unmatchedTmp);
				sigLabel.setText(sig.equals(sigMapped) ? sig : sig+" - "+sigMapped);
			}

			outerLabel.setText(cls.getOuterClass() != null ? getName(cls.getOuterClass()) : "-");
			superLabel.setText(cls.getSuperClass() != null ? getName(cls.getSuperClass()) : "-");
			subLabel.setText(cls.isInterface() ? "-" : format(cls.getChildClasses()));
			ifaceLabel.setText(format(cls.getInterfaces()));
			implLabel.setText(cls.isInterface() ? format(cls.getImplementers()) : "-");
			refMethodLabel.setText(format(cls.getMethodTypeRefs()));
			refFieldLabel.setText(format(cls.getFieldTypeRefs()));
			mapCommentLabel.setText(nullToMissing(cls.getMappedComment()));
		}
	}

	private String format(Collection<? extends IMatchable<?>> c) {
		return format(c, gui.isTmpNamed(), unmatchedTmp);
	}

	private String getName(IMatchable<?> m) {
		return getName(m, gui.isTmpNamed(), unmatchedTmp);
	}

	static String format(Collection<? extends IMatchable<?>> c, boolean tmpNamed, boolean unmatchedTmp) {
		return c.stream().map(v -> ClassInfoTab.getName(v, tmpNamed, unmatchedTmp)).sorted().collect(Collectors.joining("\n"));
	}

	static String getName(IMatchable<?> m, boolean tmpNamed, boolean unmatchedTmp) {
		String ret = m.getDisplayName(true, false, tmpNamed, unmatchedTmp);
		String mapped = m.getDisplayName(true, true, tmpNamed, unmatchedTmp);

		if (Objects.equals(ret, mapped)) {
			return ret;
		} else {
			return ret+" - "+mapped;
		}
	}

	static String nullToMissing(String s) {
		return s != null ? s : "-";
	}

	private final Gui gui;
	private final ISelectionProvider selectionProvider;
	private final boolean unmatchedTmp;

	private final Label nameLabel = new Label();
	private final Label tmpNameLabel = new Label();
	private final Label mappedNameLabel = new Label();
	private final Label nameObfLabel = new Label();
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
