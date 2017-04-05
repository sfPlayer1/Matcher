package matcher.mapping;

import java.io.BufferedReader;
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
import java.util.Collections;
import java.util.Queue;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class MappingReader {
	public static void read(Path file, IMappingAcceptor mappingAcceptor) throws IOException {
		MappingFormat format;

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

		switch (format) {
		case TINY:
			readtiny(file, mappingAcceptor);
			break;
		case TINY_GZIP:
			readGztiny(file, mappingAcceptor);
			break;
		case ENIGMA:
			readEnigma(file, mappingAcceptor);
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
			readtiny(reader, mappingAcceptor);
		}
	}

	public static void readGztiny(Path file, IMappingAcceptor mappingAcceptor) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
			readtiny(reader, mappingAcceptor);
		}
	}

	private static void readtiny(BufferedReader reader, IMappingAcceptor mappingAcceptor) throws IOException {
		boolean firstLine = true;
		String line;

		while ((line = reader.readLine()) != null) {
			if (firstLine) {
				firstLine = false;
				if (!line.startsWith("v1\t")) throw new IOException("invalid/unsupported tiny file (incorrect header)");
				continue;
			}

			if (line.isEmpty()) continue;

			String[] parts = line.split("\t");
			if (parts.length < 3) throw new IOException("invalid tiny line (missing columns): "+line);

			switch (parts[0]) {
			case "CLASS":
				if (parts.length != 3) throw new IOException("invalid tiny line (extra columns): "+line);
				if (parts[1].isEmpty()) throw new IOException("invalid tiny line (empty src class): "+line);
				if (parts[2].isEmpty()) throw new IOException("invalid tiny line (empty dst class): "+line);

				mappingAcceptor.acceptClass(parts[1], parts[2]);
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
				if (parts.length != 6) throw new IOException("invalid tiny line (missing/extra columns): "+line);
				if (parts[1].isEmpty()) throw new IOException("invalid tiny line (empty src class): "+line);
				if (parts[2].isEmpty()) throw new IOException("invalid tiny line (empty src method desc): "+line);
				if (parts[3].isEmpty()) throw new IOException("invalid tiny line (empty src method name): "+line);
				if (parts[4].isEmpty()) throw new IOException("invalid tiny line (empty method arg index): "+line);
				if (parts[5].isEmpty()) throw new IOException("invalid tiny line (empty dst method arg name): "+line);

				mappingAcceptor.acceptMethodArg(
						parts[1], parts[3], parts[2],
						Integer.parseInt(parts[4]), parts[5]);
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
					if (parts.length == 3) mappingAcceptor.acceptClass(parts[1], parts[2]);
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
				case "ARG": {
					if (parts.length != 3) throw new IOException("invalid enigma line (missing/extra columns): "+line);
					String methodContext = contextStack.poll();
					if (methodContext == null || methodContext.charAt(0) != 'M') throw new IOException("invalid enigma line (arg without method): "+line);
					String classContext = contextStack.peek();
					if (classContext == null || classContext.charAt(0) != 'C') throw new IllegalStateException();
					contextStack.add(methodContext);
					int methodDescStart = methodContext.indexOf('(');
					assert methodDescStart != -1;
					mappingAcceptor.acceptMethodArg(classContext.substring(1),
							methodContext.substring(1, methodDescStart),
							methodContext.substring(methodDescStart),
							Integer.parseInt(parts[1]),
							parts[2]);
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

	public static void readSrg(Path file, IMappingAcceptor mappingAcceptor) throws IOException {
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

					mappingAcceptor.acceptClass(parts[1], parts[2]);
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

					mappingAcceptor.acceptMethod(
							parts[1].substring(0, srcSepPos), parts[1].substring(srcSepPos + 1), parts[2],
							parts[3].substring(0, dstSepPos), parts[3].substring(dstSepPos + 1), parts[4]);
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

					mappingAcceptor.acceptField(
							parts[1].substring(0, srcSepPos), parts[1].substring(srcSepPos + 1), null,
							parts[2].substring(0, dstSepPos), parts[2].substring(dstSepPos + 1), null);
					break;
				default:
					throw new IOException("invalid srg line (unknown type): "+line);
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
				default: throw new IOException("invalid escape sequence: "+str);
				}

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
