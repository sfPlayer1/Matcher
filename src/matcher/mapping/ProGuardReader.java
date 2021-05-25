package matcher.mapping;

import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ProGuardReader {
	public static List<String> getNamespaces() {
		return List.of(MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK);
	}

	public static void read(Reader reader, MappingVisitor visitor) throws IOException {
		read(reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor);
	}

	public static void read(Reader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		BufferedReader br = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);

		read(br, sourceNs, targetNs, visitor);
	}

	private static void read(BufferedReader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		CharArrayReader parentReader = null;

		if (visitor.getFlags().contains(MappingFlag.NEEDS_MULTIPLE_PASSES)) {
			char[] buffer = new char[100_000];
			int pos = 0;
			int len;

			while ((len = reader.read(buffer, pos, buffer.length - pos)) >= 0) {
				pos += len;

				if (pos == buffer.length) buffer = Arrays.copyOf(buffer, buffer.length * 2);
			}

			parentReader = new CharArrayReader(buffer, 0, pos);
			reader = new BufferedReader(parentReader);
		}

		StringBuilder tmp = null;

		for (;;) {
			boolean visitHeader = visitor.visitHeader();

			if (visitHeader) {
				visitor.visitNamespaces(sourceNs, Collections.singletonList(targetNs));
			}

			if (visitor.visitContent()) {
				if (tmp == null) tmp = new StringBuilder();

				String line;
				boolean visitClass = false;

				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) continue;

					if (line.endsWith(":")) { // class: <deobf> -> <obf>:
						int pos = line.indexOf(" -> ");
						if (pos < 0) throw new IOException("invalid proguard line (invalid separator): "+line);
						if (pos == 0) throw new IOException("invalid proguard line (empty src class): "+line);
						if (pos + 4 + 1 >= line.length()) throw new IOException("invalid proguard line (empty dst class): "+line);

						String name = line.substring(0, pos).replace('.', '/');
						visitClass = visitor.visitClass(name);

						if (visitClass) {
							String mappedName = line.substring(pos + 4, line.length() - 1).replace('.', '/');
							visitor.visitDstName(MappedElementKind.CLASS, 0, mappedName);
							visitClass = visitor.visitElementContent(MappedElementKind.CLASS);
						}
					} else if (visitClass) { // method or field: <type> <deobf> -> <obf>
						String[] parts = line.split(" ");

						if (parts.length != 4) throw new IOException("invalid proguard line (extra columns): "+line);
						if (parts[0].isEmpty()) throw new IOException("invalid proguard line (empty type): "+line);
						if (parts[1].isEmpty()) throw new IOException("invalid proguard line (empty src member): "+line);
						if (!parts[2].equals("->")) throw new IOException("invalid proguard line (invalid separator): "+line);
						if (parts[3].isEmpty()) throw new IOException("invalid proguard line (empty dst member): "+line);

						if (parts[1].indexOf('(') < 0) { // field: <type> <deobf> -> <obf>
							String name = parts[1];
							String desc = pgTypeToAsm(parts[0], tmp);

							if (visitor.visitField(name, desc)) {
								String mappedName = parts[3];
								visitor.visitDstName(MappedElementKind.FIELD, 0, mappedName);
								visitor.visitElementContent(MappedElementKind.FIELD);
							}
						} else { // method: [<lineStart>:<lineEndIncl>:]<rtype> [<clazz>.]<deobf><arg-desc>[:<deobf-lineStart>[:<deobf-lineEnd>]] -> <obf>
							// lineStart, lineEndIncl, rtype
							String part0 = parts[0];
							int pos = part0.indexOf(':');

							String retType;

							if (pos == -1) { // no obf line numbers
								retType = part0;
							} else {
								int pos2 = part0.indexOf(':', pos + 1);
								assert pos2 != -1;

								retType = part0.substring(pos2 + 1);
							}

							// clazz, deobf, arg-desc, obf
							String part1 = parts[1];
							pos = part1.indexOf('(');
							int pos3 = part1.indexOf(')', pos + 1); // arg-desc, obf
							assert pos3 != -1;

							if (part1.lastIndexOf('.', pos - 1) < 0 && part1.length() == pos3 + 1) { // no inlined method
								String name = part1.substring(0, pos);
								String argDesc = part1.substring(pos, pos3 + 1);
								String desc = pgDescToAsm(argDesc, retType, tmp);

								if (visitor.visitMethod(name, desc)) {
									String mappedName = parts[3];
									visitor.visitDstName(MappedElementKind.METHOD, 0, mappedName);
									visitor.visitElementContent(MappedElementKind.METHOD);
								}
							}
						}
					}
				}
			}

			if (visitor.visitEnd()) break;

			if (parentReader == null) {
				throw new IllegalStateException("repeated visitation requested without NEEDS_MULTIPLE_PASSES");
			} else {
				parentReader.reset();
				reader = new BufferedReader(parentReader);
			}
		}
	}

	private static String pgDescToAsm(String pgArgDesc, String pgRetType, StringBuilder tmp) {
		tmp.setLength(0);
		tmp.append('(');

		if (pgArgDesc.length() > 2) { // not just ()
			int startPos = 1;
			boolean abort = false;

			do {
				int endPos = pgArgDesc.indexOf(',', startPos);

				if (endPos < 0) {
					endPos = pgArgDesc.length() - 1;
					abort = true;
				}

				appendPgTypeToAsm(pgArgDesc.substring(startPos, endPos), tmp);
				startPos = endPos + 1;
			} while (!abort);
		}

		tmp.append(')');
		if (pgRetType != null) appendPgTypeToAsm(pgRetType, tmp);

		return tmp.toString();
	}

	private static String pgTypeToAsm(String type, StringBuilder tmp) {
		tmp.setLength(0);
		appendPgTypeToAsm(type, tmp);

		return tmp.toString();
	}

	private static void appendPgTypeToAsm(String type, StringBuilder out) {
		assert !type.isEmpty();

		int arrayStart = type.indexOf('[');

		if (arrayStart != -1) {
			assert type.substring(arrayStart).matches("(\\[\\])+");

			int arrayDimensions = (type.length() - arrayStart) / 2; // 2 chars each: []

			for (int i = 0; i < arrayDimensions; i++) {
				out.append('[');
			}

			type = type.substring(0, arrayStart);
		}

		switch (type) {
		case "void": out.append('V'); break;
		case "boolean": out.append('Z'); break;
		case "char": out.append('C'); break;
		case "byte": out.append('B'); break;
		case "short": out.append('S'); break;
		case "int": out.append('I'); break;
		case "float": out.append('F'); break;
		case "long": out.append('J'); break;
		case "double": out.append('D'); break;
		default:
			out.append('L');
			out.append(type.replace('.', '/'));
			out.append(';');
		}
	}
}
