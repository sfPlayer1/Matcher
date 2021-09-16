package matcher.gui.tab;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.objectweb.asm.util.TraceClassVisitor;

import matcher.NameType;
import matcher.gui.Gui;
import matcher.gui.ISelectionProvider;
import matcher.srcprocess.HtmlUtil;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MethodInstance;

public class BytecodeTab extends WebViewTab {
	public BytecodeTab(Gui gui, ISelectionProvider selectionProvider, boolean unmatchedTmp) {
		super("bytecode", "ui/ByteCodeTemplate.htm");

		this.gui = gui;
		this.selectionProvider = selectionProvider;
		this.unmatchedTmp = unmatchedTmp;
	}

	@Override
	public void onClassSelect(ClassInstance cls) {
		update(cls, false);
	}

	@Override
	public void onViewChange() {
		ClassInstance cls = selectionProvider.getSelectedClass();

		if (cls != null) {
			update(cls, true);
		}
	}

	private void update(ClassInstance cls, boolean isRefresh) {
		if (cls == null) {
			displayText("no class selected");
		} else {
			StringWriter writer = new StringWriter();

			try (PrintWriter pw = new PrintWriter(writer)) {
				NameType nameType = gui.getNameType().withUnmatchedTmp(unmatchedTmp);
				cls.accept(new TraceClassVisitor(null, new HtmlTextifier(cls, nameType), pw), nameType);
			}

			double prevScroll = isRefresh ? getScrollTop() : 0;

			displayHtml(writer.toString());

			if (isRefresh && prevScroll > 0) {
				setScrollTop(prevScroll);
			}
		}
	}

	@Override
	public void onMethodSelect(MethodInstance method) {
		if (method != null) select(HtmlUtil.getId(method));
	}

	@Override
	public void onFieldSelect(FieldInstance field) {
		if (field != null) select(HtmlUtil.getId(field));
	}

	private final Gui gui;
	private final ISelectionProvider selectionProvider;
	private final boolean unmatchedTmp;
}
