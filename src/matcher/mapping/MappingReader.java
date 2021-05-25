package matcher.mapping;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class MappingReader {
	public static MappingFormat detectFormat(Path file) throws IOException {
		if (Files.isDirectory(file)) {
			return MappingFormat.ENIGMA;
		} else {
			try (SeekableByteChannel channel = Files.newByteChannel(file)) {
				ByteBuffer buffer = ByteBuffer.allocate(4096);

				while (buffer.hasRemaining()) {
					if (channel.read(buffer) == -1) break;
				}

				buffer.flip();
				if (buffer.remaining() < 3) throw new IOException("invalid/truncated mapping file");

				if (buffer.get(0) == (byte) 0x1f && buffer.get(1) == (byte) 0x8b && buffer.get(2) == (byte) 0x08) { // gzip with deflate header
					return MappingFormat.TINY_GZIP;
				}

				String headerStr = StandardCharsets.UTF_8.decode(buffer).toString();

				if (headerStr.length() >= 3) {
					switch (headerStr.substring(0, 3)) {
					case "v1\t":
						return MappingFormat.TINY;
					case "tin":
						return MappingFormat.TINY_2;
					case "tsr": // tsrg2 <nsA> <nsB> ..<nsN>
						return MappingFormat.TSRG2;
					case "PK:":
					case "CL:":
					case "MD:":
					case "FD:":
						return MappingFormat.SRG;
					}
				}

				if (headerStr.contains(" -> ")) {
					return MappingFormat.PROGUARD;
				} else if (headerStr.contains("\n\t")) {
					return MappingFormat.TSRG;
				}
			}
		}

		return null; // unknown format or corrupted
	}

	public static String[] getNamespaces(Path file, MappingFormat format) throws IOException {
		if (format == null) {
			format = detectFormat(file);
			if (format == null) throw new IOException("invalid/unsupported mapping format");
		}

		if (format.hasNamespaces) {
			try (BufferedReader reader = createReader(file, format.isGzipped)) {
				switch (format) {
				case TINY:
				case TINY_GZIP: {
					String firstLine = reader.readLine();
					if (firstLine == null) throw new EOFException();

					String[] parts = firstLine.split("\t");
					if (parts.length < 3) throw new IOException("invalid tiny v1 namespace definition");

					return Arrays.copyOfRange(parts, 1, parts.length);
				}
				case TINY_2:
					return Tiny2Impl.getNamespaces(reader);
				case TSRG2:
					String firstLine = reader.readLine();
					if (firstLine == null) throw new EOFException();

					String[] parts = firstLine.split(" ");
					if (parts.length < 3) throw new IOException("invalid tiny v1 namespace definition");

					return Arrays.copyOfRange(parts, 1, parts.length);
				default:
					throw new IllegalStateException();
				}
			}
		} else {
			return new String[] { MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK };
		}
	}

	public static void read(Path file, MappingFormat format, String nsSource, String nsTarget, MappingVisitor visitor) throws IOException {
		if (format == null) {
			format = detectFormat(file);
			if (format == null) throw new IOException("invalid/unsupported mapping format");
		}

		switch (format) {
		case TINY:
			//readTiny(file, nsSource, nsTarget, new RegularAsFlatMappingVisitor(visitor));
			try (Reader reader = createReader(file, false)) {
				Tiny1Reader.read(reader, new MappingSourceNsSwitch(visitor, nsSource));
			}

			break;
		case TINY_GZIP:
			//readGzTiny(file, nsSource, nsTarget, new RegularAsFlatMappingVisitor(visitor));
			try (Reader reader = createReader(file, true)) {
				Tiny1Reader.read(reader, new MappingSourceNsSwitch(visitor, nsSource));
			}

			break;
		case TINY_2:
			//readTiny2(file, nsSource, nsTarget, new RegularAsFlatMappingVisitor(visitor));
			try (Reader reader = createReader(file, false)) {
				Tiny2Reader.read(reader, new MappingSourceNsSwitch(visitor, nsSource));
			}

			break;
		case ENIGMA:
			EnigmaImpl.read(file, isReverseMapping(nsSource, nsTarget), new RegularAsFlatMappingVisitor(visitor));
			break;
		case MCP:
			readMcp(file, isReverseMapping(nsSource, nsTarget), new RegularAsFlatMappingVisitor(visitor));
			break;
		case SRG:
			readSrg(file, isReverseMapping(nsSource, nsTarget), new RegularAsFlatMappingVisitor(visitor));
			break;
		case TSRG:
			readTSrg(file, isReverseMapping(nsSource, nsTarget), new RegularAsFlatMappingVisitor(visitor));
			break;
		case PROGUARD:
			//readProguard(file, isReverseMapping(nsSource, nsTarget), new RegularAsFlatMappingVisitor(visitor));
			try (Reader reader = createReader(file, false)) {
				ProGuardReader.read(reader, new MappingSourceNsSwitch(visitor, nsSource));
			}

			break;
		default:
			throw new IllegalStateException();
		}
	}

	private static boolean isReverseMapping(String nsSource, String nsTarget) {
		if (nsSource.equals(MappingUtil.NS_SOURCE_FALLBACK) && nsTarget.equals(MappingUtil.NS_TARGET_FALLBACK)) {
			return false;
		} else if (nsSource.equals(MappingUtil.NS_TARGET_FALLBACK) && nsTarget.equals(MappingUtil.NS_SOURCE_FALLBACK)) {
			return true;
		} else {
			throw new IllegalArgumentException("invalid ns: "+nsSource+" -> "+nsTarget);
		}
	}

	public static void readTiny(Path file, String nsSource, String nsTarget, FlatMappingVisitor visitor) throws IOException {
		do {
			try (BufferedReader reader = Files.newBufferedReader(file)) {
				readTiny(reader, nsSource, nsTarget, visitor);
			}
		} while (!visitor.visitEnd());
	}

	public static void readGzTiny(Path file, String nsSource, String nsTarget, FlatMappingVisitor visitor) throws IOException {
		do {
			try (BufferedReader reader = createReader(file, true)) {
				readTiny(reader, nsSource, nsTarget, visitor);
			}
		} while (!visitor.visitEnd());
	}

	private static BufferedReader createReader(Path file, boolean gzip) throws IOException {
		InputStream is = Files.newInputStream(file);
		if (gzip) is = new GZIPInputStream(is);

		return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
	}

	private static void readTiny(BufferedReader reader, String nsSource, String nsTarget, FlatMappingVisitor visitor) throws IOException {
		boolean firstLine = true;
		boolean skipHeader = false;
		boolean inHeader = true;
		int nsA = 0;
		int nsB = 0;
		int nsCount = 0;

		// state for remapping owner/desc from ns0 to nsA after reading all class mappings
		Map<String, String> classMap = null;
		List<String[]> pendingMethods = null;
		List<String[]> pendingFields = null;

		String line;

		while ((line = reader.readLine()) != null) {
			if (firstLine) {
				firstLine = false;
				if (!line.startsWith("v1\t")) throw new IOException("invalid/unsupported tiny file (incorrect header)");
				List<String> parts = Arrays.asList(line.split("\t"));
				if (parts.size() < 3) throw new IOException("missing src or dst namespace declaration");

				if (visitor.visitHeader()) {
					visitor.visitNamespaces(parts.get(1), parts.subList(2, parts.size()));
				} else {
					skipHeader = true;
				}

				parts = parts.subList(1, parts.size());
				nsCount = parts.size();

				nsA = parts.indexOf(nsSource);
				if (nsA < 0) throw new IOException("missing source namespace: "+nsSource);
				nsB = parts.indexOf(nsTarget);
				if (nsB < 0) throw new IOException("missing target namespace: "+nsTarget);

				if (nsA != 0) {
					classMap = new HashMap<>();
					pendingMethods = new ArrayList<>();
					pendingFields = new ArrayList<>();
				}

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

					if (!skipHeader || !inHeader) {
						visitor.visitMetadata(metaKey, line.substring(pos + 1));
					}
				}

				continue;
			}

			String[] parts = line.split("\t");
			if (parts.length < 3) throw new IOException("invalid tiny line (missing columns): "+line);

			if (inHeader) {
				inHeader = false;

				if (!visitor.visitContent()) {
					break;
				}
			}

			switch (parts[0]) {
			case "CLASS":
				if (parts.length != 1 + nsCount) throw new IOException("invalid tiny line (missing/extra columns): "+line);
				if (parts[1 + nsA].isEmpty()) throw new IOException("invalid tiny line (empty src class): "+line);
				if (parts[1 + nsB].isEmpty()) throw new IOException("invalid tiny line (empty dst class): "+line);

				visitor.visitClass(parts[1 + nsA], parts[1 + nsB]);
				if (classMap != null) classMap.put(parts[1], parts[1 + nsA]);
				break;
			case "METHOD":
				if (parts.length != 3 + nsCount) throw new IOException("invalid tiny line (missing/extra columns): "+line);
				if (parts[1].isEmpty()) throw new IOException("invalid tiny line (empty src class): "+line);
				if (parts[2].isEmpty()) throw new IOException("invalid tiny line (empty src method desc): "+line);
				if (parts[3 + nsA].isEmpty()) throw new IOException("invalid tiny line (empty src method name): "+line);
				if (parts[3 + nsB].isEmpty()) throw new IOException("invalid tiny line (empty dst method name): "+line);

				if (pendingMethods == null) {
					visitor.visitMethod(
							parts[1], parts[3 + nsA], parts[2],
							null, parts[3 + nsB], null);
				} else {
					pendingMethods.add(parts);
				}
				break;
			case "FIELD":
				if (parts.length != 3 + nsCount) throw new IOException("invalid tiny line (missing/extra columns): "+line);
				if (parts[1].isEmpty()) throw new IOException("invalid tiny line (empty src class): "+line);
				if (parts[2].isEmpty()) throw new IOException("invalid tiny line (empty src field desc): "+line);
				if (parts[3 + nsA].isEmpty()) throw new IOException("invalid tiny line (empty src field name): "+line);
				if (parts[3 + nsB].isEmpty()) throw new IOException("invalid tiny line (empty dst field name): "+line);

				if (pendingFields == null) {
					visitor.visitField(
							parts[1], parts[3 + nsA], parts[2],
							null, parts[3 + nsB], null);
				} else {
					pendingFields.add(parts);
				}
				break;
			case "CLS-CMT":
			case "MTH-CMT":
			case "MTH-ARG":
			case "MTH-VAR":
			case "FLD-CMT":
				// silently ignore
				break;
			default:
				throw new IOException("invalid tiny line (unknown type): "+line);
			}
		}

		if (firstLine) throw new IOException("invalid tiny mapping file");

		if (classMap != null) { // nsA != 0, remap owner+desc to nsA
			for (String[] parts : pendingMethods) {
				visitor.visitMethod(
						classMap.getOrDefault(parts[1], parts[1]), parts[3 + nsA], MappingUtil.mapDesc(parts[2], classMap),
						null, parts[3 + nsB], null);
			}

			for (String[] parts : pendingFields) {
				visitor.visitField(
						classMap.getOrDefault(parts[1], parts[1]), parts[3 + nsA], MappingUtil.mapDesc(parts[2], classMap),
						null, parts[3 + nsB], null);
			}
		}
	}

	public static void readTiny2(Path file, String nsSource, String nsTarget, FlatMappingVisitor visitor) throws IOException {
		do {
			try (BufferedReader reader = Files.newBufferedReader(file)) {
				Tiny2Impl.read(reader, nsSource, nsTarget, visitor);
			}
		} while (!visitor.visitEnd());
	}

	public static void readMcp(Path dir, boolean reverse, FlatMappingVisitor visitor) throws IOException {
		if (reverse) throw new UnsupportedOperationException(); // TODO: implement

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

		do {
			readSrg(notchSrgSrg, reverse, methodNames, methodComments, fieldNames, fieldComments, paramNames, maxMethodParamMap, clsReverseMap, visitor);

			if (excFile != null) {
				// read constructor parameter mappings
				readMcpExc(excFile, clsReverseMap, paramNames, visitor);
			}

		} while (!visitor.visitEnd());
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

	private static void readMcpExc(Path file, Map<String, String> clsReverseMap, Map<String, String> paramNameMap, FlatMappingVisitor visitor) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String line;

			while ((line = reader.readLine()) != null) {
				if (line.isEmpty()) continue;
				if (line.charAt(0) == '#') continue; // commented out
				if (line.charAt(line.length() - 1) == '|') continue; // no parameters

				final String method = "<init>";
				final String token = "."+method+"(";
				int clsEnd = line.indexOf(token);
				if (clsEnd == -1) continue; // no constructor method

				String dstCls = line.substring(0, clsEnd);
				String cls = clsReverseMap.get(dstCls);
				assert cls != null;

				// determine and map desc
				int descEnd = line.indexOf('=', clsEnd + token.length() + 1); // at least .<init>()
				assert descEnd != -1;

				String dstDesc = line.substring(clsEnd + token.length() - 1, descEnd); // start after ".<init>", end at =
				String desc = MappingUtil.mapDesc(dstDesc, clsReverseMap);

				// extract parameters
				clsEnd = line.lastIndexOf('|');
				assert clsEnd != -1;

				for (int i = clsEnd + 1; i < line.length(); i++) {
					int end = line.indexOf(',', i + 1);
					if (end == -1) end = line.length();

					if (line.charAt(i) != 'p'
							|| line.charAt(i + 1) != '_'
							|| line.charAt(i + 2) != 'i'
							|| line.charAt(end - 1) != '_'
							|| (descEnd = line.indexOf('_', i + 4)) == -1
							|| descEnd >= end - 1) {
						throw new IOException("invalid param name: "+line.substring(i, end));
					}

					String name = paramNameMap.get(line.substring(i, end));

					if (name != null) {
						visitor.visitMethodArg(cls, method, desc, -1, Integer.parseInt(line.substring(descEnd + 1, end - 1)), null, dstCls, method, dstDesc, name);
					}

					i = end;
				}
			}
		}
	}

	public static void readSrg(Path file, boolean reverse, FlatMappingVisitor visitor) throws IOException {
		do {
			readSrg(file, reverse,
					Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
					null, visitor);
		} while (!visitor.visitEnd());
	}

	private static void readSrg(Path file, boolean reverse,
			Map<String, String> methodNameMap, Map<String, String> methodCommentMap,
			Map<String, String> fieldNameMap, Map<String, String> fieldCommentMap,
			Map<String, String> paramNameMap,
			Map<String, Integer> maxMethodParamMap,
			Map<String, String> clsReverseMap, FlatMappingVisitor visitor) throws IOException {
		if (visitor.visitHeader()) {
			String srcNs, dstNs;

			if (!reverse) {
				srcNs = MappingUtil.NS_SOURCE_FALLBACK;
				dstNs = MappingUtil.NS_TARGET_FALLBACK;
			} else {
				srcNs = MappingUtil.NS_TARGET_FALLBACK;
				dstNs = MappingUtil.NS_SOURCE_FALLBACK;
			}

			visitor.visitNamespaces(srcNs, Collections.singletonList(dstNs));
		}

		if (!visitor.visitContent()) return;

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
				case "CL:": {
					if (parts.length != 3) throw new IOException("invalid srg line (extra columns): "+line);
					if (parts[1].isEmpty()) throw new IOException("invalid srg line (empty src class): "+line);
					if (parts[2].isEmpty()) throw new IOException("invalid srg line (empty dst class): "+line);

					String name = parts[1];
					String mappedName = parts[2];

					if (!reverse) {
						visitor.visitClass(name, mappedName);
						if (clsReverseMap != null) clsReverseMap.put(mappedName, name);
					} else {
						visitor.visitClass(mappedName, name);
					}

					break;
				}
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
									visitor.visitMethodArg(srcCls, srcName, srcDesc, -1, i, null, dstCls, dstName, dstDesc, name);
								}
							}
						}

						comment = methodCommentMap.get(dstName);
						dstName = mappedName;
					}

					if (reverse) { // swap src <-> dst
						String tmp = srcCls;
						srcCls = dstCls;
						dstCls = tmp;

						tmp = srcName;
						srcName = dstName;
						dstName = tmp;

						tmp = srcDesc;
						srcDesc = dstDesc;
						dstDesc = tmp;
					}

					if (visitor.visitMethod(srcCls, srcName, srcDesc, dstCls, dstName, dstDesc)
							&& comment != null) {
						visitor.visitMethodComment(srcCls, srcName, srcDesc, dstCls, dstName, dstDesc, comment);
					}

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

					if (reverse) { // swap src <-> dst
						String tmp = srcCls;
						srcCls = dstCls;
						dstCls = tmp;

						tmp = srcName;
						srcName = dstName;
						dstName = tmp;
					}

					if (visitor.visitField(srcCls, srcName, null, dstCls, dstName, null)
							&& comment != null) {
						visitor.visitFieldComment(srcCls, srcName, null, dstCls, dstName, null, comment);
					}

					break;
				default:
					throw new IOException("invalid srg line (unknown type): "+line);
				}
			}
		}
	}

	public static void readTSrg(Path file, boolean reverse, FlatMappingVisitor visitor) throws IOException {
		Map<String, String> classMap;
		List<PendingMemberMapping> pendingMethods;

		if (!reverse) {
			classMap = null;
			pendingMethods = null;
		} else {
			classMap = new HashMap<>();
			pendingMethods = new ArrayList<>();
		}

		String className = null;

		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String line;

			while ((line = reader.readLine()) != null) {
				if (line.isEmpty() || line.endsWith("/")) continue; // ignore empty lines or package mappings

				String[] parts = line.split(" ");

				if (line.charAt(0) != '\t') { // class: <src> <dst>
					if (parts.length != 2) throw new IOException("invalid tsrg line (extra columns): "+line);
					if (parts[0].isEmpty()) throw new IOException("invalid tsrg line (empty src class): "+line);
					if (parts[1].isEmpty()) throw new IOException("invalid tsrg line (empty dst class): "+line);

					String name = parts[0];
					String mappedName = parts[1];

					if (!reverse) {
						visitor.visitClass(name, mappedName);
						className = name;
					} else {
						visitor.visitClass(mappedName, name);
						classMap.put(name, mappedName);
						className = mappedName;
					}
				} else if (parts.length == 2) { // field: \t<src> <dst>
					if (className == null) throw new IOException("invalid tsrg line (missing class name): "+line);
					if (parts[0].length() < 2) throw new IOException("invalid tsrg line (empty src field): "+line);
					if (parts[1].isEmpty()) throw new IOException("invalid tsrg line (empty dst field): "+line);

					String name = parts[0].substring(1);
					String mappedName = parts[1];

					if (!reverse) {
						visitor.visitField(className, name, null, null, mappedName, null);
					} else {
						visitor.visitField(className, mappedName, null, null, name, null);
					}
				} else if (parts.length == 3) { // method: \t<src> <src-desc> <dst>
					if (className == null) throw new IOException("invalid tsrg line (missing class name): "+line);
					if (parts[0].length() < 2) throw new IOException("invalid tsrg line (empty src method): "+line);
					if (parts[1].isEmpty()) throw new IOException("invalid tsrg line (empty src desc): "+line);
					if (parts[1].charAt(0) != '(') throw new IOException("invalid tsrg line (invalid src desc): "+line);
					if (parts[2].isEmpty()) throw new IOException("invalid tsrg line (empty dst method): "+line);

					String name = parts[0].substring(1);
					String desc = parts[1];
					String mappedName = parts[2];

					if (!reverse) {
						visitor.visitMethod(className, name, desc, null, mappedName, null);
					} else {
						pendingMethods.add(new PendingMemberMapping(className, name, desc, mappedName));
					}
				} else {
					throw new IOException("invalid tsrg line (extra columns): "+line);
				}
			}
		}

		if (reverse) { // remap desc
			for (PendingMemberMapping m : pendingMethods) {
				visitor.visitMethod(
						m.owner, m.mappedName, MappingUtil.mapDesc(m.desc, classMap),
						null, m.name, null);
			}
		}
	}

	public static void readProguard(Path file, boolean reverse, FlatMappingVisitor visitor) throws IOException {
		Map<String, String> classMap;
		List<PendingMemberMapping> pendingMethods, pendingFields;

		if (!reverse) {
			classMap = null;
			pendingMethods = pendingFields = null;
		} else {
			classMap = new HashMap<>();
			pendingMethods = new ArrayList<>();
			pendingFields = new ArrayList<>();
		}

		String className = null;

		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String line;

			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;

				String[] parts = line.split(" ");

				if (line.endsWith(":")) { // class: <deobf> -> <obf>:
					if (parts.length != 3) throw new IOException("invalid proguard line (extra columns): "+line);
					if (parts[0].isEmpty()) throw new IOException("invalid proguard line (empty src class): "+line);
					if (!parts[1].equals("->")) throw new IOException("invalid proguard line (invalid separator): "+line);
					if (parts[2].isEmpty()) throw new IOException("invalid proguard line (empty dst class): "+line);

					String name = parts[0].replace('.', '/');
					String mappedName = parts[2].substring(0, parts[2].length() - 1).replace('.', '/');

					if (!reverse) {
						visitor.visitClass(name, mappedName);
						className = name;
					} else {
						visitor.visitClass(mappedName, name);
						classMap.put(name, mappedName);
						className = mappedName;
					}
				} else { // method or field: <type> <deobf> -> <obf>
					if (className == null) throw new IOException("invalid proguard line (missing class name): "+line);
					if (parts.length != 4) throw new IOException("invalid proguard line (extra columns): "+line);
					if (parts[0].isEmpty()) throw new IOException("invalid proguard line (empty type): "+line);
					if (parts[1].isEmpty()) throw new IOException("invalid proguard line (empty src member): "+line);
					if (!parts[2].equals("->")) throw new IOException("invalid proguard line (invalid separator): "+line);
					if (parts[3].isEmpty()) throw new IOException("invalid proguard line (empty dst member): "+line);

					if (parts[1].indexOf('(') < 0) { // field: <type> <deobf> -> <obf>
						String name = parts[1];
						String desc = pgTypeToAsm(parts[0]);
						String mappedName = parts[3];

						if (!reverse) {
							visitor.visitField(className, name, desc, null, mappedName, null);
						} else {
							pendingFields.add(new PendingMemberMapping(className, name, desc, mappedName));
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
							String desc = pgDescToAsm(argDesc, retType);
							String mappedName = parts[3];

							if (!reverse) {
								visitor.visitMethod(className, name, desc, null, mappedName, null);
							} else {
								pendingMethods.add(new PendingMemberMapping(className, name, desc, mappedName));
							}
						}
					}
				}
			}
		}

		if (reverse) { // remap desc
			for (PendingMemberMapping m : pendingMethods) {
				visitor.visitMethod(
						m.owner, m.mappedName, MappingUtil.mapDesc(m.desc, classMap),
						null, m.name, null);
			}

			for (PendingMemberMapping m : pendingFields) {
				visitor.visitField(
						m.owner, m.mappedName, MappingUtil.mapDesc(m.desc, classMap),
						null, m.name, null);
			}
		}
	}

	private static class PendingMemberMapping {
		PendingMemberMapping(String owner, String name, String desc, String mappedName) {
			this.owner = owner;
			this.name = name;
			this.desc = desc;
			this.mappedName = mappedName;
		}

		final String owner;
		final String name;
		final String desc;
		final String mappedName;
	}

	private static String pgDescToAsm(String pgArgDesc, String pgRetType) {
		StringBuilder ret = new StringBuilder();
		ret.append('(');

		if (pgArgDesc.length() > 2) { // not just ()
			int startPos = 1;
			boolean abort = false;

			do {
				int endPos = pgArgDesc.indexOf(',', startPos);

				if (endPos < 0) {
					endPos = pgArgDesc.length() - 1;
					abort = true;
				}

				pgTypeToAsm(pgArgDesc.substring(startPos, endPos), ret);
				startPos = endPos + 1;
			} while (!abort);
		}

		ret.append(')');
		if (pgRetType != null) pgTypeToAsm(pgRetType, ret);

		return ret.toString();
	}

	private static String pgTypeToAsm(String type) {
		StringBuilder sb = new StringBuilder();
		pgTypeToAsm(type, sb);

		return sb.toString();
	}

	private static void pgTypeToAsm(String type, StringBuilder sb) {
		assert !type.isEmpty();

		int arrayStart = type.indexOf('[');

		if (arrayStart != -1) {
			assert type.substring(arrayStart).matches("(\\[\\])+");

			int arrayDimensions = (type.length() - arrayStart) / 2; // 2 chars each: []

			for (int i = 0; i < arrayDimensions; i++) {
				sb.append('[');
			}

			type = type.substring(0, arrayStart);
		}

		switch (type) {
		case "void": sb.append('V'); break;
		case "boolean": sb.append('Z'); break;
		case "char": sb.append('C'); break;
		case "byte": sb.append('B'); break;
		case "short": sb.append('S'); break;
		case "int": sb.append('I'); break;
		case "float": sb.append('F'); break;
		case "long": sb.append('J'); break;
		case "double": sb.append('D'); break;
		default:
			sb.append('L');
			sb.append(type.replace('.', '/'));
			sb.append(';');
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
