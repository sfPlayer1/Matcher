package matcher.gui.tab;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

import javafx.concurrent.Worker.State;
import javafx.scene.control.Tab;
import javafx.scene.web.WebView;

import matcher.config.Config;
import matcher.gui.IGuiComponent;
import matcher.srcprocess.HtmlUtil;

abstract class WebViewTab extends Tab implements IGuiComponent {
	protected WebViewTab(String text, String templatePath) {
		super(text);

		this.template = readTemplate(templatePath);

		webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue == State.SUCCEEDED) {
				Runnable r;

				while ((r = pendingWebViewTasks.poll()) != null) {
					r.run();
				}
			}
		});

		setContent(webView);
	}

	protected void displayText(String text) {
		displayHtml(HtmlUtil.escape(text));
	}

	protected void displayHtml(String html) {
		html = template.replace("%text%", html)
				.replace("%theme_path%", Config.getTheme().getUrl().toExternalForm());

		// System.out.println(html);
		webView.getEngine().loadContent(html);
	}

	protected void select(String anchorId) {
		addWebViewTask(() -> webView.getEngine().executeScript("var newAnchor = document.getElementById('"+anchorId+"');"
				+ "if (newAnchor !== null) document.body.scrollTop = newAnchor.getBoundingClientRect().top + window.scrollY;"
				+ "if (window.hasOwnProperty('anchorElem') && window.anchorElem !== null) window.anchorElem.classList.remove('selected');"
				+ "if (newAnchor !== null) newAnchor.classList.add('selected');"
				+ "window.anchorElem = newAnchor;"));
	}

	protected double getScrollTop() {
		Object result;

		if (webView.getEngine().getLoadWorker().getState() == State.SUCCEEDED
				&& (result = webView.getEngine().executeScript("document.body.scrollTop")) instanceof Number) {
			return ((Number) result).doubleValue();
		} else {
			return 0;
		}
	}

	protected void setScrollTop(double value) {
		addWebViewTask(() -> webView.getEngine().executeScript("document.body.scrollTop = "+value));
	}

	private void addWebViewTask(Runnable r) {
		if (webView.getEngine().getLoadWorker().getState() == State.SUCCEEDED) {
			r.run();
		} else {
			pendingWebViewTasks.add(r);
		}
	}

	protected void cancelWebViewTasks() {
		pendingWebViewTasks.clear();
	}

	private static String readTemplate(String name) {
		char[] buffer = new char[4000];
		int offset = 0;

		try (InputStream is = SourcecodeTab.class.getResourceAsStream("/"+name)) {
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

	private final String template;
	private final WebView webView = new WebView();
	private final Queue<Runnable> pendingWebViewTasks = new ArrayDeque<>();
}
