package matcher.gui.tab;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.objectweb.asm.util.TraceClassVisitor;

import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import matcher.Util;
import matcher.gui.IGuiComponent;
import matcher.type.ClassInstance;

public class BytecodeTab extends Tab implements IGuiComponent {
	public BytecodeTab() {
		super("bytecode");

		init();
	}

	private void init() {
		text.setEditable(false);

		setContent(text);
	}

	@Override
	public void onClassSelect(ClassInstance cls) {
		update(cls);
	}

	private void update(ClassInstance cls) {
		if (cls == null) {
			text.setText("");
		} else {
			StringWriter writer = new StringWriter();

			try (PrintWriter pw = new PrintWriter(writer)) {
				synchronized (Util.asmNodeSync) {
					cls.getMergedAsmNode().accept(new TraceClassVisitor(pw));
				}
			}

			text.setText(writer.toString());
		}
	}

	private final TextArea text = new TextArea();
}
