package matcher.gui.tab;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

import matcher.NameType;
import matcher.gui.Gui;
import matcher.gui.ISelectionProvider;
import matcher.srcprocess.HtmlUtil;
import matcher.srcprocess.SrcDecorator;
import matcher.srcprocess.SrcDecorator.SrcParseException;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MatchType;
import matcher.type.MethodInstance;

public class SourcecodeTab extends WebViewTab {
	public SourcecodeTab(Gui gui, ISelectionProvider selectionProvider, boolean unmatchedTmp) {
		super("source", "ui/SourceCodeTemplate.htm");

		this.gui = gui;
		this.selectionProvider = selectionProvider;
		this.unmatchedTmp = unmatchedTmp;

		init();
	}

	private void init() {
		displayText("no class selected");
	}

	@Override
	public void onClassSelect(ClassInstance cls) {
		update(cls, false);
	}

	@Override
	public void onMatchChange(Set<MatchType> types) {
		ClassInstance cls = selectionProvider.getSelectedClass();

		if (cls != null) {
			update(cls, true);
		}
	}

	@Override
	public void onViewChange() {
		ClassInstance cls = selectionProvider.getSelectedClass();

		if (cls != null) {
			update(cls, true);
		}
	}

	private void update(ClassInstance cls, boolean isRefresh) {
		cancelWebViewTasks();

		final int cDecompId = ++decompId;

		if (cls == null) {
			displayText("no class selected");
			return;
		}

		if (!isRefresh) {
			displayText("decompiling...");
		}

		NameType nameType = gui.getNameType().withUnmatchedTmp(unmatchedTmp);

		//Gui.runAsyncTask(() -> gui.getEnv().decompile(cls, true))
		Gui.runAsyncTask(() -> SrcDecorator.decorate(gui.getEnv().decompile(gui.getDecompiler().get(), cls, nameType), cls, nameType))
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
							double prevScroll = isRefresh ? getScrollTop() : 0;

							displayHtml(res);

							if (isRefresh && prevScroll > 0) {
								setScrollTop(prevScroll);
							}
						}
					} else if (exc != null) {
						exc.printStackTrace();
					}
				});
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

	private int decompId;
}
