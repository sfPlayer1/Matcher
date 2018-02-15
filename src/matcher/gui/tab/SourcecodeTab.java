package matcher.gui.tab;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

import javafx.scene.control.Tab;
import javafx.scene.web.WebView;
import matcher.gui.Gui;
import matcher.gui.IGuiComponent;
import matcher.gui.ISelectionProvider;
import matcher.srcprocess.SrcDecorator;
import matcher.srcprocess.SrcDecorator.SrcParseException;
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
		displayText("no class selected");
		setContent(webView);
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
			displayText("no class selected");
			return;
		}

		//double prevScroll = text.getScrollTop();

		if (!isRefresh) {
			displayText("decompiling...");
		}

		//Gui.runAsyncTask(() -> gui.getEnv().decompile(cls, true))
		Gui.runAsyncTask(() -> SrcDecorator.decorate(gui.getEnv().decompile(cls, true), cls, true))
		.whenComplete((res, exc) -> {
			if (cDecompId == decompId) {
				if (exc != null) {
					exc.printStackTrace();

					StringWriter sw = new StringWriter();
					exc.printStackTrace(new PrintWriter(sw));

					if (exc instanceof SrcParseException) {
						displayText("parse error: "+sw.toString()+"decompiled source:\n"+((SrcParseException) exc).source);
					} else {
						displayText("decompile error: "+sw.toString());
					}

				} else {
					//boolean fixScroll = isRefresh && Math.abs(text.getScrollTop() - prevScroll) < 1;
					//System.out.println("fix scroll: "+fixScroll+", to "+text.getScrollTop());

					displayText(res);

					//if (fixScroll) text.setScrollTop(prevScroll);
				}
			} else if (exc != null) {
				exc.printStackTrace();
			}
		});
	}

	private void displayText(String text) {
		webView.getEngine().loadContent(template.replace("%text%", text));
	}

	private static String readTemplate(String name) {
		char[] buffer = new char[4000];
		int offset = 0;

		try (InputStream is = SourcecodeTab.class.getClassLoader().getResourceAsStream(name)) {
			if (is == null) throw new FileNotFoundException(name);

			Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
			int len;

			while ((len = reader.read(buffer, offset, buffer.length - offset)) != -1) {
				offset += len;

				if (offset == buffer.length) buffer = Arrays.copyOf(buffer, buffer.length * 2);
			}

			return new String(buffer, 0, offset);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static final String template = readTemplate("ui/SourceCodeTemplate.htm");

	private final Gui gui;
	private final ISelectionProvider selectionProvider;
	//private final TextArea text = new TextArea();
	private final WebView webView = new WebView();

	private int decompId;
}
