package matcher.gui;

import java.util.ArrayList;
import java.util.Collection;

import javafx.scene.control.TabPane;
import matcher.gui.tab.BytecodeTab;
import matcher.gui.tab.ClassInfoTab;
import matcher.gui.tab.ClassScoresTab;
import matcher.gui.tab.FieldInfoTab;
import matcher.gui.tab.HierarchyTab;
import matcher.gui.tab.MemberScoresTab;
import matcher.gui.tab.MethodInfoTab;
import matcher.gui.tab.SourcecodeTab;

public class ContentPane extends TabPane implements IFwdGuiComponent {
	public ContentPane(Gui gui, ISelectionProvider selectionProvider, boolean isSource) {
		this.gui = gui;
		this.isSource = isSource;

		init(selectionProvider);
	}

	private void init(ISelectionProvider selectionProvider) {
		// source tab

		SourcecodeTab scTab = new SourcecodeTab(gui, selectionProvider, isSource);
		components.add(scTab);
		getTabs().add(scTab);

		// bytecode tab

		BytecodeTab bcTab = new BytecodeTab(gui, selectionProvider, isSource);
		components.add(bcTab);
		getTabs().add(bcTab);

		// info tab

		ClassInfoTab iTab = new ClassInfoTab(gui, selectionProvider, isSource);
		components.add(iTab);
		getTabs().add(iTab);

		// method info tab

		MethodInfoTab mITab = new MethodInfoTab(gui, selectionProvider, isSource);
		components.add(mITab);
		getTabs().add(mITab);

		// field info tab

		FieldInfoTab fITab = new FieldInfoTab(gui, selectionProvider, isSource);
		components.add(fITab);
		getTabs().add(fITab);

		// hierarchy tab

		if (showHierarchy) {
			HierarchyTab hTab = new HierarchyTab();
			components.add(hTab);
			getTabs().add(hTab);
		}

		// classification scores tab

		if (!isSource) {
			ClassScoresTab csTab = new ClassScoresTab(selectionProvider);
			components.add(csTab);
			getTabs().add(csTab);

			MemberScoresTab msTab = new MemberScoresTab(selectionProvider);
			components.add(msTab);
			getTabs().add(msTab);
		}
	}

	@Override
	public Collection<IGuiComponent> getComponents() {
		return components;
	}

	private static final boolean showHierarchy = false;

	private final Gui gui;
	private final boolean isSource;
	private final Collection<IGuiComponent> components = new ArrayList<>();
}
