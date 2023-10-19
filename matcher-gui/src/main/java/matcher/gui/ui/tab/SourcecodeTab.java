package matcher.gui.ui.tab;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

import matcher.gui.MatcherGui;
import matcher.gui.srcprocess.Decompiler;
import matcher.gui.srcprocess.HtmlUtil;
import matcher.gui.srcprocess.SrcDecorator;
import matcher.gui.srcprocess.SrcDecorator.SrcParseException;
import matcher.gui.ui.ISelectionProvider;
import matcher.model.NameType;
import matcher.model.type.ClassInstance;
import matcher.model.type.FieldInstance;
import matcher.model.type.MatchType;
import matcher.model.type.MemberInstance;
import matcher.model.type.MethodInstance;

public class SourcecodeTab extends WebViewTab {
	public SourcecodeTab(MatcherGui gui, ISelectionProvider selectionProvider, boolean unmatchedTmp) {
		super("source", "ui/templates/CodeViewTemplate.htm");

		this.gui = gui;
		this.selectionProvider = selectionProvider;
		this.unmatchedTmp = unmatchedTmp;

		update();
	}

	@Override
	public void onSelectStateChange(boolean tabSelected) {
		this.tabSelected = tabSelected;
		if (!tabSelected) return;

		if (updateNeeded > 0) update();

		if (selectedMember instanceof MethodInstance) {
			onMethodSelect((MethodInstance) selectedMember);
		} else if (selectedMember instanceof FieldInstance) {
			onFieldSelect((FieldInstance) selectedMember);
		}
	}

	@Override
	public void onClassSelect(ClassInstance cls) {
		selectedClass = cls;
		if (updateNeeded == 0) updateNeeded = 1;
		if (tabSelected) update();
	}

	@Override
	public void onMatchChange(Set<MatchType> types) {
		selectedClass = selectionProvider.getSelectedClass();
		updateNeeded = 2;

		if (tabSelected && selectedClass != null) {
			update();
		}
	}

	@Override
	public void onViewChange(ViewChangeCause cause) {
		selectedClass = selectionProvider.getSelectedClass();

		if (cause == ViewChangeCause.THEME_CHANGED) {
			// Update immediately to prevent flashes when switching
			update();
		} else if (selectedClass != null
				&& (cause == ViewChangeCause.NAME_TYPE_CHANGED
						|| cause == ViewChangeCause.DECOMPILER_CHANGED)) {
			updateNeeded = 2;
			if (tabSelected) update();
		}
	}

	private void update() {
		cancelWebViewTasks();

		final int cDecompId = ++decompId;

		if (selectedClass == null) {
			displayText("no class selected");
			return;
		}

		displayText("decompiling...");

		NameType nameType = gui.getNameType().withUnmatchedTmp(unmatchedTmp);

		//Gui.runAsyncTask(() -> gui.getEnv().decompile(selectedClass, true))
		MatcherGui.runAsyncTask(() -> SrcDecorator.decorate(Decompiler.decompile(gui.getEnv(), gui.getDecompiler().get(), selectedClass, nameType), selectedClass, nameType))
				.whenComplete((res, exc) -> {
					if (cDecompId == decompId) {
						if (exc != null) {
							exc.printStackTrace();

							StringWriter sw = new StringWriter();
							exc.printStackTrace(new PrintWriter(sw));

							if (exc instanceof SrcParseException) {
								SrcParseException parseExc = (SrcParseException) exc;
								displayText("parse error: "+parseExc.problems+"\ndecompiled source:\n"+parseExc.source);
							} else {
								displayText("decompile error: "+sw.toString());
							}
						} else {
							double prevScroll = updateNeeded == 2 ? getScrollTop() : 0;

							displayHtml(res);

							if (updateNeeded == 2 && prevScroll > 0) {
								setScrollTop(prevScroll);
							}
						}
					} else if (exc != null) {
						exc.printStackTrace();
					}

					updateNeeded = 0;
				});
	}

	@Override
	public void onMethodSelect(MethodInstance method) {
		selectedMember = method;

		if (tabSelected && method != null) {
			select(HtmlUtil.getId(method));
		}
	}

	@Override
	public void onFieldSelect(FieldInstance field) {
		selectedMember = field;

		if (tabSelected && field != null) {
			select(HtmlUtil.getId(field));
		}
	}

	private final MatcherGui gui;
	private final ISelectionProvider selectionProvider;
	private final boolean unmatchedTmp;

	private int decompId;
	private int updateNeeded;
	private boolean tabSelected;
	private ClassInstance selectedClass;
	private MemberInstance<?> selectedMember;
}
