package matcher.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

public class MappingReader {
	public static void read(Path file, IClassMappingAcceptor cmAcceptor, IMethodMappingAcceptor mmAcceptor, IFieldMappingAcceptor fmAcceptor) throws IOException {
		MappingFormat format;

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

		switch (format) {
		case TINY:
			readtiny(file, cmAcceptor, mmAcceptor, fmAcceptor);
			break;
		case TINY_GZIP:
			readGztiny(file, cmAcceptor, mmAcceptor, fmAcceptor);
			break;
		case SRG:
			readSrg(file, cmAcceptor, mmAcceptor, fmAcceptor);
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public static void readtiny(Path file, IClassMappingAcceptor cmAcceptor, IMethodMappingAcceptor mmAcceptor, IFieldMappingAcceptor fmAcceptor) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			readtiny(reader, cmAcceptor, mmAcceptor, fmAcceptor);
		}
	}

	public static void readGztiny(Path file, IClassMappingAcceptor cmAcceptor, IMethodMappingAcceptor mmAcceptor, IFieldMappingAcceptor fmAcceptor) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
			readtiny(reader, cmAcceptor, mmAcceptor, fmAcceptor);
		}
	}

	private static void readtiny(BufferedReader reader, IClassMappingAcceptor cmAcceptor, IMethodMappingAcceptor mmAcceptor, IFieldMappingAcceptor fmAcceptor) throws IOException {
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

				cmAcceptor.acceptClass(parts[1], parts[2]);
				break;
			case "METHOD":
				if (parts.length != 5) throw new IOException("invalid tiny line (missing/extra columns): "+line);
				if (parts[1].isEmpty()) throw new IOException("invalid tiny line (empty src class): "+line);
				if (parts[2].isEmpty()) throw new IOException("invalid tiny line (empty src method desc): "+line);
				if (parts[3].isEmpty()) throw new IOException("invalid tiny line (empty src method name): "+line);
				if (parts[4].isEmpty()) throw new IOException("invalid tiny line (empty dst method name): "+line);

				mmAcceptor.acceptMethod(
						parts[1], parts[3], parts[2],
						null, parts[4], null);
				break;
			case "FIELD":
				if (parts.length != 5) throw new IOException("invalid tiny line (missing/extra columns): "+line);
				if (parts[1].isEmpty()) throw new IOException("invalid tiny line (empty src class): "+line);
				if (parts[2].isEmpty()) throw new IOException("invalid tiny line (empty src field desc): "+line);
				if (parts[3].isEmpty()) throw new IOException("invalid tiny line (empty src field name): "+line);
				if (parts[4].isEmpty()) throw new IOException("invalid tiny line (empty dst field name): "+line);

				fmAcceptor.acceptField(
						parts[1], parts[3], parts[2],
						null, parts[4], null);
				break;
			default:
				throw new IOException("invalid tiny line (unknown type): "+line);
			}
		}

		if (firstLine) throw new IOException("invalid tiny mapping file");
	}

	public static void readSrg(Path file, IClassMappingAcceptor cmAcceptor, IMethodMappingAcceptor mmAcceptor, IFieldMappingAcceptor fmAcceptor) throws IOException {
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

					cmAcceptor.acceptClass(parts[1], parts[2]);
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

					mmAcceptor.acceptMethod(
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

					fmAcceptor.acceptField(
							parts[1].substring(0, srcSepPos), parts[1].substring(srcSepPos + 1), null,
							parts[2].substring(0, dstSepPos), parts[2].substring(dstSepPos + 1), null);
					break;
				default:
					throw new IOException("invalid srg line (unknown type): "+line);
				}
			}
		}
	}
}
