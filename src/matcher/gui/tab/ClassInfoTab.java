package matcher.gui.tab;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import matcher.type.ClassInstance;
import matcher.type.Matchable;

public class ClassInfoTab extends Tab implements IGuiComponent {
	public ClassInfoTab(Gui gui, ISelectionProvider selectionProvider, boolean unmatchedTmp) {
		super("info");

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

		row = addRow("Name", nameLabel, grid, row);
		row = addRow("Tmp name", tmpNameLabel, grid, row);
		row = addRow("Mapped name", mappedNameLabel, grid, row);

		for (int i = 0; i < NameType.AUX_COUNT; i++) {
			row = addRow("AUX name "+(i+1), auxNameLabels[i], grid, row);
		}

		row = addRow("UID", uidLabel, grid, row);
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

			for (int i = 0; i < NameType.AUX_COUNT; i++) {
				auxNameLabels[i].setText("-");
			}

			uidLabel.setText("-");
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
			NameType nameType = gui.getNameType().withUnmatchedTmp(unmatchedTmp);

			nameLabel.setText(cls.getName());
			tmpNameLabel.setText(cls.hasLocalTmpName() ? cls.getName(NameType.LOCTMP_PLAIN) : "-");
			mappedNameLabel.setText(cls.hasMappedName() ? cls.getName(NameType.MAPPED) : "-");

			for (int i = 0; i < NameType.AUX_COUNT; i++) {
				auxNameLabels[i].setText(cls.hasAuxName(i) ? cls.getName(NameType.getAux(i)) : "-");
			}

			uidLabel.setText(cls.getUid() >= 0 ? Integer.toString(cls.getUid()) : "-");
			nameObfLabel.setText(Boolean.toString(cls.isNameObfuscated()));
			accessLabel.setText(Util.formatAccessFlags(cls.getAccess(), AFElementType.Class));

			if (cls.getSignature() == null) {
				sigLabel.setText("-");
			} else {
				String sig = cls.getSignature().toString(nameType.withMapped(false));
				String sigMapped = cls.getSignature().toString(nameType.withMapped(true));
				sigLabel.setText(sig.equals(sigMapped) ? sig : sig+" - "+sigMapped);
			}

			outerLabel.setText(cls.getOuterClass() != null ? getName(cls.getOuterClass(), nameType) : "-");
			superLabel.setText(cls.getSuperClass() != null ? getName(cls.getSuperClass(), nameType) : "-");
			subLabel.setText(cls.isInterface() ? "-" : format(cls.getChildClasses(), nameType));
			ifaceLabel.setText(format(cls.getInterfaces(), nameType));
			implLabel.setText(cls.isInterface() ? format(cls.getImplementers(), nameType) : "-");
			refMethodLabel.setText(format(cls.getMethodTypeRefs(), nameType));
			refFieldLabel.setText(format(cls.getFieldTypeRefs(), nameType));
			mapCommentLabel.setText(nullToMissing(cls.getMappedComment()));
		}
	}

	static String format(Collection<? extends Matchable<?>> c, NameType nameType) {
		return format(c.stream(), nameType);
	}

	static String format(Stream<? extends Matchable<?>> stream, NameType nameType) {
		return stream.map(v -> getName(v, nameType)).sorted().collect(Collectors.joining("\n"));
	}

	static String getName(Matchable<?> m, NameType nameType) {
		String ret = m.getDisplayName(nameType.withMapped(false), true);
		String mapped = m.getDisplayName(nameType.withMapped(true), true);

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
	private final Label[] auxNameLabels = new Label[NameType.AUX_COUNT];
	private final Label uidLabel = new Label();
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
