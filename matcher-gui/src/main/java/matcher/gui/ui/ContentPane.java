package matcher.gui.ui;

import java.util.ArrayList;
import java.util.Collection;

import javafx.scene.control.TabPane;

import matcher.gui.ui.tab.BytecodeTab;
import matcher.gui.ui.tab.ClassInfoTab;
import matcher.gui.ui.tab.ClassScoresTab;
import matcher.gui.ui.tab.FieldInfoTab;
import matcher.gui.ui.tab.HierarchyTab;
import matcher.gui.ui.tab.MemberScoresTab;
import matcher.gui.ui.tab.MethodInfoTab;
import matcher.gui.ui.tab.MethodVarScoresTab;
import matcher.gui.ui.tab.SourcecodeTab;
import matcher.gui.ui.tab.VarInfoTab;

public class ContentPane extends TabPane implements IFwdGuiComponent {
	public ContentPane(Gui gui, ISelectionProvider selectionProvider, boolean isSource) {
		this.gui = gui;
		this.isSource = isSource;

		setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		init(selectionProvider);
	}

	private void init(ISelectionProvider selectionProvider) {
		// source tab

		SourcecodeTab scTab = new SourcecodeTab(gui, selectionProvider, isSource);
		scTab.onSelectStateChange(true);
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

		// var info tab

		VarInfoTab vITab = new VarInfoTab(gui, selectionProvider, isSource);
		components.add(vITab);
		getTabs().add(vITab);

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

			MethodVarScoresTab mvsTab = new MethodVarScoresTab(selectionProvider);
			components.add(mvsTab);
			getTabs().add(mvsTab);
		}

		// add tab selection change listeners

		getSelectionModel().selectedItemProperty().addListener((ov, oldTab, newTab) -> {
			if (oldTab instanceof IGuiComponent.Selectable) {
				((IGuiComponent.Selectable) oldTab).onSelectStateChange(false);
			}

			if (newTab instanceof IGuiComponent.Selectable) {
				((IGuiComponent.Selectable) newTab).onSelectStateChange(true);
			}
		});
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
