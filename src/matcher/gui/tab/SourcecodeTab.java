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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.Set;

import javafx.concurrent.Worker.State;
import javafx.scene.control.Tab;
import javafx.scene.web.WebView;
import matcher.gui.Gui;
import matcher.gui.IGuiComponent;
import matcher.gui.ISelectionProvider;
import matcher.srcprocess.HtmlUtil;
import matcher.srcprocess.SrcDecorator;
import matcher.srcprocess.SrcDecorator.SrcParseException;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MatchType;
import matcher.type.MethodInstance;

public class SourcecodeTab extends Tab implements IGuiComponent {
	public SourcecodeTab(Gui gui, ISelectionProvider selectionProvider, boolean unmatchedTmp) {
		super("source");

		this.gui = gui;
		this.selectionProvider = selectionProvider;
		this.unmatchedTmp = unmatchedTmp;

		webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue == State.SUCCEEDED) {
				Runnable r;

				while ((r = pendingWebViewTasks.poll()) != null) {
					r.run();
				}
			}
		});

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

	@Override
	public void onViewChange() {
		ClassInstance cls = selectionProvider.getSelectedClass();

		if (cls != null) {
			update(cls, true);
		}
	}

	private void update(ClassInstance cls, boolean isRefresh) {
		pendingWebViewTasks.clear();

		final int cDecompId = ++decompId;

		if (cls == null) {
			displayText("no class selected");
			return;
		}

		if (!isRefresh) {
			displayText("decompiling...");
		}

		boolean mapped = gui.isMapCodeViews();
		boolean tmpNamed = gui.isTmpNamed();

		//Gui.runAsyncTask(() -> gui.getEnv().decompile(cls, true))
		Gui.runAsyncTask(() -> SrcDecorator.decorate(gui.getEnv().decompile(cls, mapped, tmpNamed, unmatchedTmp), cls, mapped, tmpNamed, unmatchedTmp))
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
					double prevScroll = isRefresh ? getScrollTop() : 0;

					displayHtml(res);

					if (isRefresh && prevScroll > 0) {
						addWebViewTask(() -> webView.getEngine().executeScript("document.body.scrollTop = "+prevScroll));
					}
				}
			} else if (exc != null) {
				exc.printStackTrace();
			}
		});
	}

	@Override
	public void onMethodSelect(MethodInstance method) {
		if (method != null) jumpTo(HtmlUtil.getId(method));
	}

	@Override
	public void onFieldSelect(FieldInstance field) {
		if (field != null) jumpTo(HtmlUtil.getId(field));
	}

	private void jumpTo(String anchorId) {
		if (unmatchedTmp) System.out.println("jump to "+anchorId);
		addWebViewTask(() -> webView.getEngine().executeScript("var anchorElem = document.getElementById('"+anchorId+"'); if (anchorElem !== null) document.body.scrollTop = anchorElem.getBoundingClientRect().top + window.scrollY;"));
	}

	private void displayText(String text) {
		displayHtml(HtmlUtil.escape(text));
	}

	private void displayHtml(String html) {
		webView.getEngine().loadContent(template.replace("%text%", html));
	}

	private double getScrollTop() {
		Object result;

		if (webView.getEngine().getLoadWorker().getState() == State.SUCCEEDED
				&& (result = webView.getEngine().executeScript("document.body.scrollTop")) instanceof Number) {
			return ((Number) result).doubleValue();
		} else {
			return 0;
		}
	}

	private void addWebViewTask(Runnable r) {
		if (webView.getEngine().getLoadWorker().getState() == State.SUCCEEDED) {
			r.run();
		} else {
			pendingWebViewTasks.add(r);
		}
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
	private final boolean unmatchedTmp;
	//private final TextArea text = new TextArea();
	private final WebView webView = new WebView();
	private final Queue<Runnable> pendingWebViewTasks = new ArrayDeque<>();

	private int decompId;
}
