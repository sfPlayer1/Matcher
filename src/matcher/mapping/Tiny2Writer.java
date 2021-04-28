package matcher.mapping;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class Tiny2Writer implements Closeable, MappingVisitor {
	public static void main(String[] args) throws IOException {
		try (Tiny2Writer writer = new Tiny2Writer(Files.newBufferedWriter(Paths.get("/home/m/tmp5/mappings_out.tiny")), false)) {
			try (Reader reader = Files.newBufferedReader(Paths.get("/home/m/tmp5/mappings.tiny"))) {
				Tiny2Reader.read(reader, writer);
			}
		}
	}

	public Tiny2Writer(Writer writer, boolean escapeNames) {
		this.writer = writer;
		this.escapeNames = escapeNames;
	}

	@Override
	public void close() throws IOException {
		writer.close();
	}

	@Override
	public Set<MappingFlag> getFlags() {
		return flags;
	}

	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) {
		dstNames = new String[dstNamespaces.size()];

		write("tiny\t2\t0\t");
		write(srcNamespace);

		for (String dstNamespace : dstNamespaces) {
			writeTab();
			write(dstNamespace);
		}

		writeLn();
	}

	@Override
	public void visitMetadata(String key, String value) {
		if (key.equals(Tiny2Util.escapedNamesProperty)) {
			escapeNames = true;
			wroteEscapedNamesProperty = true;
		}

		writeTab();
		write(key);

		if (value != null) {
			writeTab();
			write(value);
		}

		writeLn();
	}

	@Override
	public boolean visitContent() {
		if (escapeNames && !wroteEscapedNamesProperty) {
			write("\t");
			write(Tiny2Util.escapedNamesProperty);
			writeLn();
		}

		return true;
	}

	@Override
	public boolean visitClass(String srcName) {
		write("c\t");
		writeName(srcName);

		return true;
	}

	@Override
	public boolean visitField(String srcName, String srcDesc) {
		write("\tf\t");
		writeName(srcDesc);
		writeTab();
		writeName(srcName);

		return true;
	}

	@Override
	public boolean visitMethod(String srcName, String srcDesc) {
		write("\tm\t");
		writeName(srcDesc);
		writeTab();
		writeName(srcName);

		return true;
	}

	@Override
	public boolean visitMethodArg(int argPosition, int lvIndex, String srcName) {
		write("\t\tp\t");
		write(lvIndex);
		writeTab();
		if (srcName != null) writeName(srcName);

		return true;
	}

	@Override
	public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, String srcName) {
		write("\t\tv\t");
		write(lvIndex);
		writeTab();
		write(startOpIdx);
		writeTab();
		if (lvtRowIndex >= 0) write(lvtRowIndex);
		writeTab();
		if (srcName != null) writeName(srcName);

		return true;
	}

	/**
	 * Destination name for the current element.
	 *
	 * @param namespace namespace index, index into the dstNamespaces List in {@link #visitNamespaces}
	 * @param name destination name
	 */
	@Override
	public void visitDstName(MappedElementKind targetKind, int namespace, String name) {
		dstNames[namespace] = name;
	}

	/**
	 * Determine whether the element content (comment, sub-elements) should be visited.
	 *
	 * <p>This is also a notification about all available dst names having been passed on.
	 *
	 * @return true if the contents are to be visited, false otherwise
	 */
	@Override
	public boolean visitElementContent(MappedElementKind targetKind) {
		for (String dstName : dstNames) {
			writeTab();
			if (dstName != null) writeName(dstName);
		}

		writeLn();

		Arrays.fill(dstNames, null);

		return true;
	}

	/**
	 * Comment for the specified element (last content-visited or any parent).
	 *
	 * @param comment comment as a potentially multi-line string
	 */
	@Override
	public void visitComment(MappedElementKind targetKind, String comment) {
		writeTabs(targetKind.level);
		write("\tc\t");
		writeEscaped(comment);
		writeLn();
	}

	private void write(String str) {
		try {
			writer.write(str);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void write(int i) {
		write(Integer.toString(i));
	}

	private void writeEscaped(String str) {
		try {
			Tiny2Util.writeEscaped(str, writer);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void writeName(String str) {
		if (escapeNames) {
			writeEscaped(str);
		} else {
			write(str);
		}
	}

	private void writeLn() {
		write("\n");
	}

	private void writeTab() {
		write("\t");
	}

	private void writeTabs(int count) {
		for (int i = 0; i < count; i++) {
			write("\t");
		}
	}

	private static final Set<MappingFlag> flags = EnumSet.of(MappingFlag.NEEDS_UNIQUENESS, MappingFlag.NEEDS_SRC_FIELD_DESC, MappingFlag.NEEDS_SRC_METHOD_DESC);

	private final Writer writer;
	private boolean escapeNames;
	private boolean wroteEscapedNamesProperty;
	private String[] dstNames;
}
