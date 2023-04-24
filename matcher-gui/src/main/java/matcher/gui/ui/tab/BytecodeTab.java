package matcher.gui.ui.tab;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.objectweb.asm.util.TraceClassVisitor;

import matcher.NameType;
import matcher.gui.MatcherGui;
import matcher.gui.srcprocess.HtmlUtil;
import matcher.gui.ui.ISelectionProvider;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;

public class BytecodeTab extends WebViewTab {
	public BytecodeTab(MatcherGui gui, ISelectionProvider selectionProvider, boolean unmatchedTmp) {
		super("bytecode", "ui/templates/CodeViewTemplate.htm");

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
		if (selectedClass == null) {
			displayText("no class selected");
		} else {
			StringWriter writer = new StringWriter();

			try (PrintWriter pw = new PrintWriter(writer)) {
				NameType nameType = gui.getNameType().withUnmatchedTmp(unmatchedTmp);
				selectedClass.accept(new TraceClassVisitor(null, new HtmlTextifier(selectedClass, nameType), pw), nameType);
			}

			double prevScroll = updateNeeded == 2 ? getScrollTop() : 0;

			displayHtml(writer.toString());

			if (updateNeeded == 2 && prevScroll > 0) {
				setScrollTop(prevScroll);
			}
		}

		updateNeeded = 0;
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

	private int updateNeeded;
	private boolean tabSelected;
	private ClassInstance selectedClass;
	private MemberInstance<?> selectedMember;
}
