package matcher.gui.tab;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.objectweb.asm.util.TraceClassVisitor;

import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import matcher.gui.Gui;
import matcher.gui.IGuiComponent;
import matcher.gui.ISelectionProvider;
import matcher.type.ClassInstance;

public class BytecodeTab extends Tab implements IGuiComponent {
	public BytecodeTab(Gui gui, ISelectionProvider selectionProvider, boolean unmatchedTmp) {
		super("bytecode");

		this.gui = gui;
		this.selectionProvider = selectionProvider;
		this.unmatchedTmp = unmatchedTmp;

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
	public void onViewChange() {
		ClassInstance cls = selectionProvider.getSelectedClass();

		if (cls != null) {
			update(cls, true);
		}
	}

	private void update(ClassInstance cls, boolean isRefresh) {
		if (cls == null) {
			text.setText("");
		} else {
			StringWriter writer = new StringWriter();

			try (PrintWriter pw = new PrintWriter(writer)) {
				cls.accept(new TraceClassVisitor(pw), gui.getNameType().withUnmatchedTmp(unmatchedTmp));
			}

			text.setText(writer.toString());
		}
	}

	private final Gui gui;
	private final ISelectionProvider selectionProvider;
	private final boolean unmatchedTmp;
	private final TextArea text = new TextArea();
}
