package matcher.mapping;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import matcher.NameType;
import matcher.mapping.MappingState.ArgMappingState;
import matcher.mapping.MappingState.ClassMappingState;
import matcher.mapping.MappingState.FieldMappingState;
import matcher.mapping.MappingState.MethodMappingState;
import matcher.mapping.MappingState.VarMappingState;

class Tiny2Impl {
	public static String[] getNamespaces(BufferedReader reader) throws IOException {
		String firstLine = reader.readLine();
		if (firstLine == null) throw new EOFException();

		String[] parts;

		if (firstLine == null
				|| !firstLine.startsWith("tiny\t2\t")
				|| (parts = splitAtTab(firstLine, 0, 5)).length < 5) { //min. tiny + major version + minor version + 2 name spaces
			throw new IOException("invalid/unsupported tiny file (incorrect header)");
		}

		if (parts.length < 3) throw new IOException("invalid tiny v1 namespace definition");

		return Arrays.copyOfRange(parts, 3, parts.length);
	}

	public static void read(BufferedReader reader, String nsSource, String nsTarget, MappingAcceptor mappingAcceptor) throws IOException {
		String firstLine = reader.readLine();
		String[] parts;

		if (firstLine == null
				|| !firstLine.startsWith("tiny\t2\t")
				|| (parts = splitAtTab(firstLine, 0, 5)).length < 5) { //min. tiny + major version + minor version + 2 name spaces
			throw new IOException("invalid/unsupported tiny file (incorrect header)");
		}

		List<String> namespaces = Arrays.asList(Arrays.copyOfRange(parts, 3, parts.length));
		int nsCount = namespaces.size();

		int nsA = namespaces.indexOf(nsSource); // non-0 would have to handle desc in foreign namespace
		if (nsA < 0) throw new IOException("missing source namespace: "+nsSource);
		int nsB = namespaces.indexOf(nsTarget);
		if (nsB < 0) throw new IOException("missing target namespace: "+nsTarget);

		Map<String, String> classMap;

		if (nsA == 0) {
			classMap = null;
		} else { // need to remap descs from ns 0 to ns A -> do initial extra pass to read the class map
			classMap = new HashMap<String, String>();

			int classPartCount = 1 + nsCount;
			StringWriter inputCopy = new StringWriter(8192);
			boolean inHeader = true;
			boolean escapedNames = false;
			String line;

			while ((line = reader.readLine()) != null) {
				inputCopy.append(line);
				inputCopy.append('\n');

				if (line.isEmpty()) continue;

				if (inHeader) {
					if (line.startsWith("\t")) {
						if (line.equals("\tescaped-names")) {
							escapedNames = true;
						}

						continue; // still in header
					} else {
						inHeader = false;
					}
				}

				if (line.startsWith("c\t")) {
					parts = splitAtTab(line, 0, classPartCount);

					String className = unescapeOpt(parts[1 + nsA], escapedNames);

					if (!className.isEmpty()) {
						classMap.put(unescapeOpt(parts[1], escapedNames), className);
					}
				}
			}

			reader = new BufferedReader(new StringReader(inputCopy.toString()));
		}

		final int partCountHint = 2 + nsCount; // suitable for members, which should be the majority

		int lineNumber = 1;

		boolean inHeader = true;
		boolean inClass = false;
		boolean inMethod = false;
		boolean inField = false;
		boolean inMethodParam = false;
		boolean inMethodVar = false;

		boolean escapedNames = false;

		String className = null;
		String memberName = null;
		String memberDesc = null;
		int varLvIndex = 0;
		int varStartOpIdx = 0;
		int varLvtIndex = 0;
		String line;

		while ((line = reader.readLine()) != null) {
			lineNumber++;
			if (line.isEmpty()) continue;

			int indent = 0;

			while (indent < line.length() && line.charAt(indent) == '\t') {
				indent++;
			}

			parts = splitAtTab(line, indent, partCountHint);
			String section = parts[0];

			if (indent == 0) {
				inHeader = inClass = inMethod = inField = inMethodParam = inMethodVar = false;

				if (section.equals("c")) { // class: c <names>...
					if (parts.length != nsCount + 1) throw new IOException("invalid class decl in line "+lineNumber);

					className = unescapeOpt(parts[1 + nsA], escapedNames);
					String mappedName = unescapeOpt(parts[1 + nsB], escapedNames);

					if (!className.isEmpty()) {
						if (!mappedName.isEmpty()) {
							mappingAcceptor.acceptClass(className, mappedName, true);
						}
					} else { // className is empty -> fall back to primary name
						className = nsB == 0 ? mappedName : unescapeOpt(parts[1], escapedNames);
						assert !className.isEmpty();
					}

					inClass = true;
				}
			} else if (indent == 1) {
				inMethod = inField = inMethodParam = inMethodVar = false;

				if (inHeader) { // header k/v
					if (section.equals("escaped-names")) {
						escapedNames = true;
					}
				} else if (inClass && (section.equals("m") || section.equals("f"))) { // method/field: m/f <descA> <names>...
					boolean isMethod = section.equals("m");
					if (parts.length != nsCount + 2) throw new IOException("invalid "+(isMethod ? "method" : "field")+" decl in line "+lineNumber);

					memberDesc = unescapeOpt(parts[1], escapedNames);
					if (classMap != null) memberDesc = MappingReader.mapDesc(memberDesc, classMap);
					memberName = unescapeOpt(parts[2 + nsA], escapedNames);
					String mappedName = unescapeOpt(parts[2 + nsB], escapedNames);

					if (!memberName.isEmpty()) {
						if (!mappedName.isEmpty()) {
							if (isMethod) {
								mappingAcceptor.acceptMethod(className, memberName, memberDesc, null, mappedName, null);
							} else {
								mappingAcceptor.acceptField(className, memberName, memberDesc, null, mappedName, null);
							}
						}
					} else { // memberName is empty -> fall back to primary name
						memberName = nsB == 0 ? mappedName : unescapeOpt(parts[2], escapedNames);
						assert !memberName.isEmpty();
					}

					if (isMethod) {
						inMethod = true;
					} else {
						inField = true;
					}
				} else if (inClass && section.equals("c")) { // class comment: c <comment>
					if (parts.length != 2) throw new IOException("invalid class comment in line "+lineNumber);
					mappingAcceptor.acceptClassComment(className, unescape(parts[1]));
				}
			} else if (indent == 2) {
				inMethodParam = inMethodVar = false;

				if (inMethod && section.equals("p")) { // method parameter: p <lv-index> <names>...
					if (parts.length != nsCount + 2) throw new IOException("invalid method parameter decl in line "+lineNumber);

					varLvIndex = Integer.parseInt(parts[1]);
					String mappedName = unescapeOpt(parts[2 + nsB], escapedNames);

					if (!mappedName.isEmpty()) {
						mappingAcceptor.acceptMethodArg(className, memberName, memberDesc, -1, varLvIndex, null, mappedName);
					}

					inMethodParam = true;
				} else if (inMethod && section.equals("v")) { // method variable: v <lv-index> <lv-start-offset> <optional-lvt-index> <names>...
					if (parts.length != nsCount + 4) throw new IOException("invalid method variable decl in line "+lineNumber);

					varLvIndex = Integer.parseInt(parts[1]);
					varStartOpIdx = Integer.parseInt(parts[2]);
					varLvtIndex = Integer.parseInt(parts[3]);
					String mappedName = unescapeOpt(parts[4 + nsB], escapedNames);

					if (!mappedName.isEmpty()) {
						mappingAcceptor.acceptMethodVar(className, memberName, memberDesc, -1, varLvIndex, varStartOpIdx, varLvtIndex, null, mappedName);
					}

					inMethodVar = true;
				} else if ((inMethod || inField) && section.equals("c")) { // method/field comment: c <comment>
					if (parts.length != 2) throw new IOException("invalid member comment in line "+lineNumber);
					String comment = unescape(parts[1]);

					if (inMethod) {
						mappingAcceptor.acceptMethodComment(className, memberName, memberDesc, comment);
					} else {
						mappingAcceptor.acceptFieldComment(className, memberName, memberDesc, comment);
					}
				}
			} else if (indent == 3) {
				if ((inMethodParam || inMethodVar) && section.equals("c")) { // method parameter/variable comment: c <comment>
					if (parts.length != 2) throw new IOException("invalid method var comment in line "+lineNumber);
					String comment = unescape(parts[1]);

					if (inMethodParam) {
						mappingAcceptor.acceptMethodArgComment(className, memberName, memberDesc, -1, varLvIndex, comment);
					} else {
						mappingAcceptor.acceptMethodVarComment(className, memberName, memberDesc, -1, varLvIndex, varStartOpIdx, varLvtIndex, comment);
					}
				}
			}
		}
	}

	private static String[] splitAtTab(String s, int offset, int partCountHint) {
		String[] ret = new String[Math.max(1, partCountHint)];
		int partCount = 0;
		int pos;

		while ((pos = s.indexOf('\t', offset)) >= 0) {
			if (partCount == ret.length) ret = Arrays.copyOf(ret, ret.length * 2);
			ret[partCount++] = s.substring(offset, pos);
			offset = pos + 1;
		}

		if (partCount == ret.length) ret = Arrays.copyOf(ret, ret.length + 1);
		ret[partCount++] = s.substring(offset);

		return partCount == ret.length ? ret : Arrays.copyOf(ret, partCount);
	}

	private static String unescapeOpt(String str, boolean escapedNames) {
		return escapedNames ? unescape(str) : str;
	}

	private static String unescape(String str) {
		int pos = str.indexOf('\\');
		if (pos < 0) return str;

		StringBuilder ret = new StringBuilder(str.length() - 1);
		int start = 0;

		do {
			ret.append(str, start, pos);
			pos++;
			int type;

			if (pos >= str.length()) {
				throw new RuntimeException("incomplete escape sequence at the end");
			} else if ((type = escaped.indexOf(str.charAt(pos))) < 0) {
				throw new RuntimeException("invalid escape character: \\"+str.charAt(pos));
			} else {
				ret.append(toEscape.charAt(type));
			}

			start = pos + 1;
		} while ((pos = str.indexOf('\\', start)) >= 0);

		ret.append(str, start, str.length());

		return ret.toString();
	}

	public static void write(Path file, MappingState state, NameType srcType, NameType dstType) throws IOException {
		boolean needEscapes = checkEscapeNeed(state);

		try (BufferedWriter writer = createWriter(file)) {
			writer.write("tiny\t2\t0\t");
			writer.write(MappingWriter.getTinyTypeName(srcType));
			writer.write('\t');
			writer.write(MappingWriter.getTinyTypeName(dstType));
			writer.write(eol);


			if (needEscapes) {
				writer.write("\tescaped-names");
				writer.write(eol);
			}

			for (Map.Entry<String, String> e : state.metaMap.entrySet()) {
				switch (e.getKey()) {
				// TODO: implement for intermediary allocation counters
				}
			}

			for (ClassMappingState clsState : state.classMap.values()) {
				writer.write("c\t");
				writeOptEscaped(clsState.name, needEscapes, writer);
				writer.write('\t');
				if (clsState.mappedName != null) writeOptEscaped(clsState.mappedName, needEscapes, writer);
				writer.write(eol);

				if (clsState.comment != null) {
					writer.write("\tc\t");
					writeEscaped(clsState.comment, writer);
					writer.write(eol);
				}

				for (MethodMappingState mthState : clsState.methodMap.values()) {
					writer.write("\tm\t");
					writeOptEscaped(mthState.desc, needEscapes, writer);
					writer.write('\t');
					writeOptEscaped(mthState.name, needEscapes, writer);
					writer.write('\t');
					if (mthState.mappedName != null) writeOptEscaped(mthState.mappedName, needEscapes, writer);
					writer.write(eol);

					if (mthState.comment != null) {
						writer.write("\t\tc\t");
						writeEscaped(mthState.comment, writer);
						writer.write(eol);
					}

					for (ArgMappingState argState : mthState.argMap.values()) {
						writer.write("\t\tp\t");
						writer.write(Integer.toString(argState.lvIndex));
						writer.write('\t');
						if (argState.name != null) writeOptEscaped(argState.name, needEscapes, writer);
						writer.write('\t');
						if (argState.mappedName != null) writeOptEscaped(argState.mappedName, needEscapes, writer);
						writer.write(eol);

						if (argState.comment != null) {
							writer.write("\t\t\tc\t");
							writeEscaped(argState.comment, writer);
							writer.write(eol);
						}
					}

					for (VarMappingState varState : mthState.varMap.values()) {
						writer.write("\t\tp\t");
						writer.write(Integer.toString(varState.lvIndex));
						writer.write('\t');
						writer.write(Integer.toString(varState.startOpIdx));
						writer.write('\t');
						writer.write(Integer.toString(varState.asmIndex));
						writer.write('\t');
						if (varState.name != null) writeOptEscaped(varState.name, needEscapes, writer);
						writer.write('\t');
						if (varState.mappedName != null) writeOptEscaped(varState.mappedName, needEscapes, writer);
						writer.write(eol);

						if (varState.comment != null) {
							writer.write("\t\t\tc\t");
							writeEscaped(varState.comment, writer);
							writer.write(eol);
						}
					}
				}

				for (FieldMappingState fldState : clsState.fieldMap.values()) {
					writer.write("\tf\t");
					writeOptEscaped(fldState.desc, needEscapes, writer);
					writer.write('\t');
					writeOptEscaped(fldState.name, needEscapes, writer);
					writer.write('\t');
					if (fldState.mappedName != null) writeOptEscaped(fldState.mappedName, needEscapes, writer);
					writer.write(eol);

					if (fldState.comment != null) {
						writer.write("\t\tc\t");
						writeEscaped(fldState.comment, writer);
						writer.write(eol);
					}
				}
			}
		}
	}

	private static BufferedWriter createWriter(Path file) throws IOException {
		return Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	}

	private static boolean checkEscapeNeed(MappingState state) {
		for (ClassMappingState clsState : state.classMap.values()) {
			if (needEscape(clsState.name) || clsState.mappedName != null && needEscape(clsState.mappedName)) {
				return true;
			}

			for (MethodMappingState mthState : clsState.methodMap.values()) {
				if (needEscape(mthState.desc) || needEscape(mthState.name) || mthState.mappedName != null && needEscape(mthState.mappedName)) {
					return true;
				}

				for (ArgMappingState argState : mthState.argMap.values()) {
					if (argState.name != null && needEscape(argState.name) || argState.mappedName != null && needEscape(argState.mappedName)) {
						return true;
					}
				}

				for (VarMappingState varState : mthState.varMap.values()) {
					if (varState.name != null && needEscape(varState.name) || varState.mappedName != null && needEscape(varState.mappedName)) {
						return true;
					}
				}
			}

			for (FieldMappingState fldState : clsState.fieldMap.values()) {
				if (needEscape(fldState.desc) || needEscape(fldState.name) || fldState.mappedName != null && needEscape(fldState.mappedName)) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean needEscape(String s) {
		for (int pos = 0, len = s.length(); pos < len; pos++) {
			char c = s.charAt(pos);
			if (toEscape.indexOf(c) >= 0) return true;
		}

		return false;
	}

	private static void writeOptEscaped(String s, boolean escape, Writer out) throws IOException {
		if (escape) {
			writeEscaped(s, out);
		} else {
			out.write(s);
		}
	}

	private static void writeEscaped(String s, Writer out) throws IOException {
		final int len = s.length();
		int start = 0;

		for (int pos = 0; pos < len; pos++) {
			char c = s.charAt(pos);
			int idx = toEscape.indexOf(c);

			if (idx >= 0) {
				out.write(s, start, pos - start);
				out.write('\\');
				out.write(escaped.charAt(idx));
				start = pos + 1;
			}
		}

		out.write(s, start, len - start);
	}

	private static final char eol = '\n';
	private static final String toEscape = "\\\n\r\0\t";
	private static final String escaped = "\\nr0t";
}
