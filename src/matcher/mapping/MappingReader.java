package matcher.mapping;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class MappingReader {
	public static void read(Path file, MappingFormat format, IMappingAcceptor mappingAcceptor) throws IOException {
		if (format == null) {
			if (Files.isDirectory(file)) {
				format = MappingFormat.ENIGMA;
			} else {
				try (SeekableByteChannel channel = Files.newByteChannel(file)) {
					byte[] header = new byte[3];
					ByteBuffer buffer = ByteBuffer.wrap(header);

					while (buffer.hasRemaining()) {
						if (channel.read(buffer) == -1) throw new IOException("invalid/truncated tiny mapping file");
					}

					if (header[0] == (byte) 0x1f && header[1] == (byte) 0x8b && header[2] == (byte) 0x08) { // gzip with deflate header
						format = MappingFormat.TINY_GZIP;
					} else if ((header[0] & 0xff) < 0x80 && (header[1] & 0xff) < 0x80 && (header[2] & 0xff) < 0x80) {
						String headerStr = new String(header, StandardCharsets.US_ASCII);

						switch (headerStr) {
						case "v1\t":
							format = MappingFormat.TINY;
							break;
						case "PK:":
						case "CL:":
						case "MD:":
						case "FD:":
							format = MappingFormat.SRG;
							break;
						default:
							throw new IOException("invalid/unsupported mapping format");
						}
					} else {
						throw new IOException("invalid/unsupported mapping format");
					}
				}
			}
		}

		switch (format) {
		case TINY:
			readtiny(file, mappingAcceptor);
			break;
		case TINY_GZIP:
			readGzTiny(file, mappingAcceptor);
			break;
		case ENIGMA:
			readEnigma(file, mappingAcceptor);
			break;
		case MCP:
			readMcp(file, mappingAcceptor);
			break;
		case SRG:
			readSrg(file, mappingAcceptor);
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public static void readtiny(Path file, IMappingAcceptor mappingAcceptor) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			readTiny(reader, mappingAcceptor);
		}
	}

	public static void readGzTiny(Path file, IMappingAcceptor mappingAcceptor) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
			readTiny(reader, mappingAcceptor);
		}
	}

	private static void readTiny(BufferedReader reader, IMappingAcceptor mappingAcceptor) throws IOException {
		boolean firstLine = true;
		String line;

		while ((line = reader.readLine()) != null) {
			if (firstLine) {
				firstLine = false;
				if (!line.startsWith("v1\t")) throw new IOException("invalid/unsupported tiny file (incorrect header)");
				continue;
			}

			if (line.isEmpty()) continue;

			if (line.charAt(0) == '#') { // comment
				final String nextUidPrefix = "# INTERMEDIARY-COUNTER ";

				if (line.startsWith(nextUidPrefix)) { // next uid spec: # INTERMEDIARY-COUNTER <type> <id>
					int pos = line.indexOf(' ', nextUidPrefix.length());
					if (pos == -1) throw new IOException("invalid tiny line (malformed intermediary counter): "+line);

					String metaKey;

					switch (line.substring(nextUidPrefix.length(), pos)) {
					case "class": metaKey = Mappings.metaUidNextClass; break;
					case "method": metaKey = Mappings.metaUidNextMethod; break;
					case "field": metaKey = Mappings.metaUidNextField; break;
					default:
						throw new IOException("invalid tiny line (unknown intermediary counter): "+line);
					}

					mappingAcceptor.acceptMeta(metaKey, line.substring(pos + 1));
				}

				continue;
			}

			String[] parts = line.split("\t");
			if (parts.length < 3) throw new IOException("invalid tiny line (missing columns): "+line);

			switch (parts[0]) {
			case "CLASS":
				if (parts.length != 3) throw new IOException("invalid tiny line (extra columns): "+line);
				if (parts[1].isEmpty()) throw new IOException("invalid tiny line (empty src class): "+line);
				if (parts[2].isEmpty()) throw new IOException("invalid tiny line (empty dst class): "+line);

				mappingAcceptor.acceptClass(parts[1], parts[2], false);
				break;
			case "CLS-CMT":
				if (parts.length != 3) throw new IOException("invalid tiny line (extra columns): "+line);
				if (parts[1].isEmpty()) throw new IOException("invalid tiny line (empty src class): "+line);
				if (parts[2].isEmpty()) throw new IOException("invalid tiny line (empty class comment): "+line);

				mappingAcceptor.acceptClassComment(parts[1], unescape(parts[2]));
				break;
			case "METHOD":
				if (parts.length != 5) throw new IOException("invalid tiny line (missing/extra columns): "+line);
				if (parts[1].isEmpty()) throw new IOException("invalid tiny line (empty src class): "+line);
				if (parts[2].isEmpty()) throw new IOException("invalid tiny line (empty src method desc): "+line);
				if (parts[3].isEmpty()) throw new IOException("invalid tiny line (empty src method name): "+line);
				if (parts[4].isEmpty()) throw new IOException("invalid tiny line (empty dst method name): "+line);

				mappingAcceptor.acceptMethod(
						parts[1], parts[3], parts[2],
						null, parts[4], null);
				break;
			case "MTH-CMT":
				if (parts.length != 5) throw new IOException("invalid tiny line (missing/extra columns): "+line);
				if (parts[1].isEmpty()) throw new IOException("invalid tiny line (empty src class): "+line);
				if (parts[2].isEmpty()) throw new IOException("invalid tiny line (empty src method desc): "+line);
				if (parts[3].isEmpty()) throw new IOException("invalid tiny line (empty src method name): "+line);
				if (parts[4].isEmpty()) throw new IOException("invalid tiny line (empty method comment): "+line);

				mappingAcceptor.acceptMethodComment(
						parts[1], parts[3], parts[2],
						unescape(parts[4]));
				break;
			case "MTH-ARG":
			case "MTH-VAR":
				if (parts.length != 6) throw new IOException("invalid tiny line (missing/extra columns): "+line);
				if (parts[1].isEmpty()) throw new IOException("invalid tiny line (empty src class): "+line);
				if (parts[2].isEmpty()) throw new IOException("invalid tiny line (empty src method desc): "+line);
				if (parts[3].isEmpty()) throw new IOException("invalid tiny line (empty src method name): "+line);
				if (parts[4].isEmpty()) throw new IOException("invalid tiny line (empty method arg/var index): "+line);
				if (parts[5].isEmpty()) throw new IOException("invalid tiny line (empty dst method arg/var name): "+line);

				if (parts[0].equals("MTH-ARG")) {
					mappingAcceptor.acceptMethodArg(parts[1], parts[3], parts[2], Integer.parseInt(parts[4]), -1, parts[5]);
				} else {
					mappingAcceptor.acceptMethodVar(parts[1], parts[3], parts[2], Integer.parseInt(parts[4]), -1, parts[5]);
				}

				break;
			case "FIELD":
				if (parts.length != 5) throw new IOException("invalid tiny line (missing/extra columns): "+line);
				if (parts[1].isEmpty()) throw new IOException("invalid tiny line (empty src class): "+line);
				if (parts[2].isEmpty()) throw new IOException("invalid tiny line (empty src field desc): "+line);
				if (parts[3].isEmpty()) throw new IOException("invalid tiny line (empty src field name): "+line);
				if (parts[4].isEmpty()) throw new IOException("invalid tiny line (empty dst field name): "+line);

				mappingAcceptor.acceptField(
						parts[1], parts[3], parts[2],
						null, parts[4], null);
				break;
			case "FLD-CMT":
				if (parts.length != 5) throw new IOException("invalid tiny line (missing/extra columns): "+line);
				if (parts[1].isEmpty()) throw new IOException("invalid tiny line (empty src class): "+line);
				if (parts[2].isEmpty()) throw new IOException("invalid tiny line (empty src field desc): "+line);
				if (parts[3].isEmpty()) throw new IOException("invalid tiny line (empty src field name): "+line);
				if (parts[4].isEmpty()) throw new IOException("invalid tiny line (empty field comment): "+line);

				mappingAcceptor.acceptFieldComment(
						parts[1], parts[3], parts[2],
						unescape(parts[4]));
				break;
			default:
				throw new IOException("invalid tiny line (unknown type): "+line);
			}
		}

		if (firstLine) throw new IOException("invalid tiny mapping file");
	}

	public static void readEnigma(Path dir, IMappingAcceptor mappingAcceptor) throws IOException {
		try (Stream<Path> stream = Files.find(dir,
				Integer.MAX_VALUE,
				(path, attr) -> attr.isRegularFile() && path.getFileName().toString().endsWith(".mapping"),
				FileVisitOption.FOLLOW_LINKS)) {
			stream.forEach(file -> readEnigmaFile(file, mappingAcceptor));
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	private static void readEnigmaFile(Path file, IMappingAcceptor mappingAcceptor) {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String line;
			Queue<String> contextStack = Collections.asLifoQueue(new ArrayDeque<>());
			int indent = 0;

			while ((line = reader.readLine()) != null) {
				if (line.isEmpty()) continue;

				int newIndent = 0;
				while (newIndent < line.length() && line.charAt(newIndent) == '\t') newIndent++;
				int indentChange = newIndent - indent;

				if (indentChange != 0) {
					if (indentChange < 0) {
						for (int i = 0; i < -indentChange; i++) {
							contextStack.remove();
						}

						indent = newIndent;
					} else {
						throw new IOException("invalid enigma line (invalid indentation change): "+line);
					}
				}

				line = line.substring(indent);
				String[] parts = line.split(" ");

				switch (parts[0]) {
				case "CLASS":
					if (parts.length < 2 || parts.length > 3) throw new IOException("invalid enigma line (missing/extra columns): "+line);
					contextStack.add("C"+parts[1]);
					indent++;
					if (parts.length == 3) mappingAcceptor.acceptClass(parts[1], parts[2], false);
					break;
				case "METHOD": {
					if (parts.length < 3 || parts.length > 4) throw new IOException("invalid enigma line (missing/extra columns): "+line);
					if (!parts[parts.length - 1].startsWith("(")) throw new IOException("invalid enigma line (invalid method desc): "+line);
					String context = contextStack.peek();
					if (context == null || context.charAt(0) != 'C') throw new IOException("invalid enigma line (method without class): "+line);
					contextStack.add("M"+parts[1]+parts[parts.length - 1]);
					indent++;
					if (parts.length == 4) mappingAcceptor.acceptMethod(context.substring(1), parts[1], parts[3], null, parts[2], null);
					break;
				}
				case "ARG":
				case "VAR": {
					if (parts.length != 3) throw new IOException("invalid enigma line (missing/extra columns): "+line);
					String methodContext = contextStack.poll();
					if (methodContext == null || methodContext.charAt(0) != 'M') throw new IOException("invalid enigma line (arg without method): "+line);
					String classContext = contextStack.peek();
					if (classContext == null || classContext.charAt(0) != 'C') throw new IllegalStateException();
					contextStack.add(methodContext);
					int methodDescStart = methodContext.indexOf('(');
					assert methodDescStart != -1;

					String srcClsName = classContext.substring(1);
					String srcMethodName = methodContext.substring(1, methodDescStart);
					String srcMethodDesc = methodContext.substring(methodDescStart);
					int index = Integer.parseInt(parts[1]);
					int lvIndex = -1;
					String name = parts[2];

					if (EnigmaMappingState.LEGACY) {
						lvIndex = index;
						index = -1;
					}

					if (parts[0].equals("ARG")) {
						mappingAcceptor.acceptMethodArg(srcClsName, srcMethodName, srcMethodDesc, index, lvIndex, name);
					} else {
						mappingAcceptor.acceptMethodVar(srcClsName, srcMethodName, srcMethodDesc, index, lvIndex, name);
					}

					break;
				}
				case "FIELD":
					if (parts.length != 4) throw new IOException("invalid enigma line (missing/extra columns): "+line);
					String context = contextStack.peek();
					if (context == null || context.charAt(0) != 'C') throw new IOException("invalid enigma line (field without class): "+line);
					mappingAcceptor.acceptField(context.substring(1), parts[1], parts[3], null, parts[2], null);
					break;
				default:
					throw new IOException("invalid enigma line (unknown type): "+line);
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static void readMcp(Path dir, IMappingAcceptor mappingAcceptor) throws IOException {
		Path fieldsCsv = dir.resolve("fields.csv");
		if (!Files.isRegularFile(fieldsCsv)) throw new FileNotFoundException("no fields.csv");
		Path methodsCsv = dir.resolve("methods.csv");
		if (!Files.isRegularFile(fieldsCsv)) throw new FileNotFoundException("no methods.csv");
		Path paramsCsv = dir.resolve("params.csv");
		if (!Files.isRegularFile(fieldsCsv)) throw new FileNotFoundException("no params.csv");

		Path notchSrgSrg = null;
		Path excFile = null;

		for (Path p : Files.newDirectoryStream(dir, Files::isDirectory)) {
			if (!p.endsWith("srgs")) p = p.resolve("srgs");
			Path cSrgFile = p.resolve("notch-srg.srg"); // alternatively joined.srg

			if (Files.isRegularFile(cSrgFile)) {
				if (notchSrgSrg != null) System.err.print("non-unique srg folders: "+notchSrgSrg+", "+cSrgFile);
				notchSrgSrg = cSrgFile;

				excFile = p.resolve("srg.exc"); // alternatively joined.exc
				if (!Files.isRegularFile(excFile)) excFile = null;
			}
		}

		if (notchSrgSrg == null) throw new FileNotFoundException("no notch-srg.srg");

		Map<String, String> fieldNames = new HashMap<>();
		Map<String, String> fieldComments = new HashMap<>();
		readMcpCsv(fieldsCsv, fieldNames, fieldComments);

		Map<String, String> methodNames = new HashMap<>();
		Map<String, String> methodComments = new HashMap<>();
		readMcpCsv(methodsCsv, methodNames, methodComments);

		Map<String, String> paramNames = new HashMap<>();
		readMcpCsv(paramsCsv, paramNames, null);

		Map<String, Integer> maxMethodParamMap = new HashMap<>();

		for (String name : paramNames.keySet()) {
			int sepPos;
			if (!name.startsWith("p_")
					|| name.charAt(name.length() - 1) != '_'
					|| (sepPos = name.indexOf('_', 3)) == -1
					|| sepPos == name.length() - 1) {
				throw new IOException("invalid param name: "+name);
			}

			String key = name.substring(2, sepPos);
			int idx = Integer.parseInt(name.substring(sepPos + 1, name.length() - 1));

			Integer prev = maxMethodParamMap.get(key);

			if (prev == null || prev < idx) {
				maxMethodParamMap.put(key, idx);
			}
		}

		Map<String, String> clsReverseMap = excFile != null ? new HashMap<>() : null;
		readSrg(notchSrgSrg, methodNames, methodComments, fieldNames, fieldComments, paramNames, maxMethodParamMap, clsReverseMap, mappingAcceptor);

		if (excFile != null) {
			// read constructor parameter mappings
			readMcpExc(excFile, clsReverseMap, paramNames, mappingAcceptor);
		}
	}

	private static void readMcpCsv(Path file, Map<String, String> names, Map<String, String> comments) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			List<String> parts = new ArrayList<>();
			StringBuilder part = new StringBuilder();
			int lineNumber = 0;
			String line;

			while ((line = reader.readLine()) != null) {
				++lineNumber;
				if (line.isEmpty()) continue;

				boolean quoted = false;

				for (int i = 0, len = line.length(); i < len; i++) {
					char c = line.charAt(i);

					if (c == '"') {
						if (!quoted) {
							quoted = true;
						} else if (i + 1 != len && line.charAt(i + 1) == '"') {
							part.append('"');
							i++;
						} else {
							quoted = false;
						}
					} else if (c == '\\' && i + 1 != len) {
						char next = line.charAt(i + 1);
						if (next == '"' || next == '\\') {
							part.append(next);
							i++;
						} else if (next == 'n') {
							part.append('\n');
							i++;
						} else {
							part.append(c);
						}
					} else if (!quoted && c == ',') {
						parts.add(part.toString());
						part.setLength(0);
					} else {
						part.append(c);
					}
				}

				parts.add(part.toString());
				part.setLength(0);

				if (parts.size() < 3 || parts.size() > 4 || (parts.size() == 3) != (comments == null)) throw new IOException("invalid part count in line "+lineNumber+" ("+file+")");

				if (lineNumber != 1) {
					names.put(parts.get(0), parts.get(1));

					if (comments != null && !parts.get(3).isEmpty()) {
						comments.put(parts.get(0), parts.get(3));
					}
				}

				parts.clear();
			}
		}
	}

	public static void readSrg(Path file, IMappingAcceptor mappingAcceptor) throws IOException {
		readSrg(file, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), null, mappingAcceptor);
	}

	private static void readSrg(Path file,
			Map<String, String> methodNameMap, Map<String, String> methodCommentMap,
			Map<String, String> fieldNameMap, Map<String, String> fieldCommentMap,
			Map<String, String> paramNameMap,
			Map<String, Integer> maxMethodParamMap,
			Map<String, String> clsReverseMap, IMappingAcceptor mappingAcceptor) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String line;

			while ((line = reader.readLine()) != null) {
				if (line.isEmpty()) continue;

				String[] parts = line.split(" ");
				if (parts.length < 3) throw new IOException("invalid srg line (missing columns): "+line);

				switch (parts[0]) {
				case "PK:":
					if (parts.length != 3) throw new IOException("invalid srg line (extra columns): "+line);
					// ignore
					break;
				case "CL:":
					if (parts.length != 3) throw new IOException("invalid srg line (extra columns): "+line);
					if (parts[1].isEmpty()) throw new IOException("invalid srg line (empty src class): "+line);
					if (parts[2].isEmpty()) throw new IOException("invalid srg line (empty dst class): "+line);

					mappingAcceptor.acceptClass(parts[1], parts[2], true);
					if (clsReverseMap != null) clsReverseMap.put(parts[2], parts[1]);
					break;
				case "MD:": {
					if (parts.length != 5) throw new IOException("invalid srg line (missing/extra columns): "+line);
					if (parts[1].isEmpty()) throw new IOException("invalid srg line (empty src class+method name): "+line);
					if (parts[2].isEmpty()) throw new IOException("invalid srg line (empty src method desc): "+line);
					if (parts[3].isEmpty()) throw new IOException("invalid srg line (empty dst class+method name): "+line);
					if (parts[4].isEmpty()) throw new IOException("invalid srg line (empty dst method desc): "+line);

					int srcSepPos = parts[1].lastIndexOf('/');
					if (srcSepPos <= 0 || srcSepPos == parts[1].length() - 1) throw new IOException("invalid srg line (invalid src class+method name): "+line);

					int dstSepPos = parts[3].lastIndexOf('/');
					if (dstSepPos <= 0 || dstSepPos == parts[3].length() - 1) throw new IOException("invalid srg line (invalid dst class+method name): "+line);

					String srcCls = parts[1].substring(0, srcSepPos);
					String srcName = parts[1].substring(srcSepPos + 1);
					String srcDesc = parts[2];
					String dstCls = parts[3].substring(0, dstSepPos);
					String dstName = parts[3].substring(dstSepPos + 1);
					String dstDesc = parts[4];
					String comment = null;
					String mappedName;

					if ((mappedName = methodNameMap.get(dstName)) != null) {
						int sepPos;
						String prefix = "func_";

						if (!dstName.startsWith(prefix)
								|| (sepPos = dstName.indexOf('_', prefix.length() + 1)) == -1
								|| sepPos == dstName.length() - 1) {
							throw new IllegalArgumentException("invalid method name: "+dstName);
						}

						String id = dstName.substring(prefix.length(), sepPos);
						Integer maxParam = maxMethodParamMap.get(id);

						if (maxParam != null) {
							for (int i = 0; i <= maxParam; i++) {
								String name = paramNameMap.get("p_"+id+"_"+i+"_");

								if (name != null) {
									mappingAcceptor.acceptMethodArg(srcCls, srcName, srcDesc, -1, i, name);
								}
							}
						}

						comment = methodCommentMap.get(dstName);
						dstName = mappedName;
					}

					mappingAcceptor.acceptMethod(srcCls, srcName, srcDesc, dstCls, dstName, dstDesc);
					if (comment != null) mappingAcceptor.acceptMethodComment(srcCls, srcName, srcDesc, comment);
					break;
				}
				case "FD:":
					if (parts.length != 3) throw new IOException("invalid srg line (extra columns): "+line);
					if (parts[1].isEmpty()) throw new IOException("invalid srg line (empty src class+field name): "+line);
					if (parts[2].isEmpty()) throw new IOException("invalid srg line (empty dst class+field name): "+line);

					int srcSepPos = parts[1].lastIndexOf('/');
					if (srcSepPos <= 0 || srcSepPos == parts[1].length() - 1) throw new IOException("invalid srg line (invalid src class+field name): "+line);

					int dstSepPos = parts[2].lastIndexOf('/');
					if (dstSepPos <= 0 || dstSepPos == parts[2].length() - 1) throw new IOException("invalid srg line (invalid dst class+field name): "+line);

					String srcCls = parts[1].substring(0, srcSepPos);
					String srcName = parts[1].substring(srcSepPos + 1);
					String dstCls = parts[2].substring(0, dstSepPos);
					String dstName = parts[2].substring(dstSepPos + 1);
					String comment = null;
					String mappedName;

					if ((mappedName = fieldNameMap.get(dstName)) != null) {
						comment = fieldCommentMap.get(dstName);
						dstName = mappedName;
					}

					mappingAcceptor.acceptField(srcCls, srcName, null, dstCls, dstName, null);
					if (comment != null) mappingAcceptor.acceptFieldComment(srcCls, srcName, null, comment);
					break;
				default:
					throw new IOException("invalid srg line (unknown type): "+line);
				}
			}
		}
	}

	private static void readMcpExc(Path file, Map<String, String> clsReverseMap, Map<String, String> paramNameMap, IMappingAcceptor mappingAcceptor) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			StringBuilder sb = new StringBuilder(128);
			String line;

			while ((line = reader.readLine()) != null) {
				if (line.isEmpty()) continue;
				if (line.charAt(0) == '#') continue; // commented out
				if (line.charAt(line.length() - 1) == '|') continue; // no parameters

				int pos = line.indexOf(".<init>(");
				if (pos == -1) continue; // no constructor method

				String cls = clsReverseMap.get(line.substring(0, pos));
				assert cls != null;

				// determine and map desc
				int pos2 = line.indexOf('=', pos + 9);
				assert pos2 != -1;

				for (int i = pos + 8 - 1; i < pos2; i++) {
					char c = line.charAt(i);

					if (c == 'L') {
						int end = line.indexOf(';', i + 1);
						String srgName = line.substring(i + 1, end);
						String name = clsReverseMap.get(srgName);

						sb.append('L');
						sb.append(name != null ? name : srgName);
						sb.append(';');
						i = end;
					} else {
						sb.append(c);
					}
				}

				String desc = sb.toString();
				sb.setLength(0);

				// extract parameters
				pos = line.lastIndexOf('|');
				assert pos != -1;

				for (int i = pos + 1; i < line.length(); i++) {
					int end = line.indexOf(',', i + 1);
					if (end == -1) end = line.length();

					if (line.charAt(i) != 'p'
							|| line.charAt(i + 1) != '_'
							|| line.charAt(i + 2) != 'i'
							|| line.charAt(end - 1) != '_'
							|| (pos2 = line.indexOf('_', i + 4)) == -1
							|| pos2 >= end - 1) {
						throw new IOException("invalid param name: "+line.substring(i, end));
					}

					String name = paramNameMap.get(line.substring(i, end));

					if (name != null) {
						mappingAcceptor.acceptMethodArg(cls, "<init>", desc, -1, Integer.parseInt(line.substring(pos2 + 1, end - 1)), name);
					}

					i = end;
				}
			}
		}
	}

	private static String unescape(String str) throws IOException {
		StringBuilder ret = null;
		int len = str.length();
		int start = 0;

		for (int i = 0; i < len; i++) {
			char c = str.charAt(i);

			if (c == '\\') {
				if (i == len - 1) throw new IOException("invalid escape sequence: "+str);
				if (ret == null) ret = new StringBuilder(len);

				ret.append(str, start, i);

				switch (str.charAt(i + 1)) {
				case 't': ret.append("\t"); break;
				case 'n': ret.append("\n"); break;
				case 'r': ret.append("\r"); break;
				case '\\': ret.append("\\"); break;
				default: throw new IOException("invalid escape sequence: \\"+str.charAt(i + 1)+" ("+str+")");
				}

				i++;
				start = i + 1;
			}
		}

		if (ret == null) {
			return str;
		} else {
			ret.append(str, start, str.length());

			return ret.toString();
		}
	}
}
