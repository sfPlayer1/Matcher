package matcher.gui.tab;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import matcher.gui.Gui;
import matcher.gui.IGuiComponent;
import matcher.gui.ISelectionProvider;
import matcher.srcremap.SrcRemapper;
import matcher.srcremap.SrcRemapper.ParseException;
import matcher.type.ClassInstance;
import matcher.type.MatchType;

public class SourcecodeTab extends Tab implements IGuiComponent {
	public SourcecodeTab(Gui gui, ISelectionProvider selectionProvider) {
		super("source");

		this.gui = gui;
		this.selectionProvider = selectionProvider;

		init();
	}

	private void init() {
		text.setEditable(false);

		setContent(text);
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

	private void update(ClassInstance cls, boolean isRefresh) {
		final int cDecompId = ++decompId;

		if (cls == null) {
			text.setText("");
			return;
		}

		double prevScroll = text.getScrollTop();

		if (!isRefresh) {
			text.setText("decompiling...");
		}

		//Gui.runAsyncTask(() -> gui.getEnv().decompile(cls, true))
		Gui.runAsyncTask(() -> SrcRemapper.decorate(gui.getEnv().decompile(cls, true), cls, true))
		.whenComplete((res, exc) -> {
			if (cDecompId == decompId) {
				if (exc != null) {
					exc.printStackTrace();

					StringWriter sw = new StringWriter();
					exc.printStackTrace(new PrintWriter(sw));

					if (exc instanceof ParseException) {
						text.setText("parse error: "+sw.toString()+"decompiled source:\n"+((ParseException) exc).source);
					} else {
						text.setText("decompile error: "+sw.toString());
					}

				} else {
					boolean fixScroll = isRefresh && Math.abs(text.getScrollTop() - prevScroll) < 1;
					System.out.println("fix scroll: "+fixScroll+", to "+text.getScrollTop());

					text.setText(res);

					if (fixScroll) text.setScrollTop(prevScroll);
				}
			} else if (exc != null) {
				exc.printStackTrace();
			}
		});
	}

	private final Gui gui;
	private final ISelectionProvider selectionProvider;
	private final TextArea text = new TextArea();

	private int decompId;
}
