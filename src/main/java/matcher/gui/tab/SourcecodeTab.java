package matcher.gui.tab;

import java.util.Set;
import java.util.function.DoubleConsumer;

import javafx.application.Platform;
import job4j.JobState;
import job4j.JobSettings.MutableJobSettings;

import matcher.NameType;
import matcher.Util;
import matcher.gui.Gui;
import matcher.gui.ISelectionProvider;
import matcher.jobs.JobCategories;
import matcher.jobs.MatcherJob;
import matcher.srcprocess.HtmlUtil;
import matcher.srcprocess.SrcDecorator;
import matcher.srcprocess.SrcDecorator.SrcParseException;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MatchType;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;

public class SourcecodeTab extends WebViewTab {
	public SourcecodeTab(Gui gui, ISelectionProvider selectionProvider, boolean isSource) {
		super("source", "ui/templates/CodeViewTemplate.htm");

		this.gui = gui;
		this.selectionProvider = selectionProvider;
		this.isSource = isSource;

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

		NameType nameType = gui.getNameType().withUnmatchedTmp(isSource);

		var decompileJob = new MatcherJob<String>(isSource ? JobCategories.DECOMPILE_SOURCE : JobCategories.DECOMPILE_DEST) {
			@Override
			protected void changeDefaultSettings(MutableJobSettings settings) {
				settings.dontPrintStacktraceOnError();
				settings.cancelPreviousJobsWithSameId();
			}

			@Override
			protected String execute(DoubleConsumer progressReceiver) {
				return SrcDecorator.decorate(gui.getEnv().decompile(gui.getDecompiler().get(), selectedClass, nameType), selectedClass, nameType);
			}
		};
		decompileJob.addCompletionListener((code, error) -> Platform.runLater(() -> {
			if (cDecompId == decompId) {
				if (code.isEmpty() && decompileJob.getState() == JobState.CANCELED) {
					// The job got canceled before any code was generated. Ignore any errors.
					return;
				}

				if (error.isPresent()) {
					if (error.get() instanceof SrcParseException) {
						SrcParseException parseExc = (SrcParseException) error.get();
						displayText("parse error: " + parseExc.problems + "\ndecompiled source:\n" + parseExc.source);
					} else {
						displayText("decompile error: " + Util.getStacktrace(error.get()));
					}
				} else if (code.isPresent()) {
					double prevScroll = updateNeeded == 2 ? getScrollTop() : 0;

					displayHtml(code.get());

					if (updateNeeded == 2 && prevScroll > 0) {
						setScrollTop(prevScroll);
					}
				}
			}
		}));
		decompileJob.run();
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

	private final Gui gui;
	private final ISelectionProvider selectionProvider;
	private final boolean isSource;

	private int decompId;
	private int updateNeeded;
	private boolean tabSelected;
	private ClassInstance selectedClass;
	private MemberInstance<?> selectedMember;
}
