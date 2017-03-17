package matcher.mapping;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPOutputStream;

public class MappingWriter implements IClassMappingAcceptor, IMethodMappingAcceptor, IFieldMappingAcceptor, Closeable {
	public MappingWriter(Path file, MappingFormat format) throws IOException {
		this.format = format;

		switch (format) {
		case TINY:
		case SRG:
			writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			break;
		case TINY_GZIP:
			writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8));
			break;
		default:
			throw new IllegalArgumentException("invalid  mapping format: "+format.name());
		}

		if (format == MappingFormat.TINY || format == MappingFormat.TINY_GZIP) {
			writer.write("v1\tmojang\tpomf\n");
		}
	}

	@Override
	public void acceptClass(String srcName, String dstName) {
		try {
			switch (format) {
			case TINY:
			case TINY_GZIP:
				writer.write("CLASS\t");
				writer.write(srcName);
				writer.write('\t');
				writer.write(dstName);
				writer.write('\n');
				break;
			case SRG:
				writer.write("CL: ");
				writer.write(srcName);
				writer.write(' ');
				writer.write(dstName);
				writer.write('\n');
				break;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void acceptMethod(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) {
		try {
			switch (format) {
			case TINY:
			case TINY_GZIP:
				writer.write("METHOD\t");
				writer.write(srcClsName);
				writer.write('\t');
				writer.write(srcDesc);
				writer.write('\t');
				writer.write(srcName);
				writer.write('\t');
				writer.write(dstName);
				writer.write('\n');
				break;
			case SRG:
				writer.write("MD: ");
				writer.write(srcClsName);
				writer.write('/');
				writer.write(srcName);
				writer.write(' ');
				writer.write(srcDesc);
				writer.write(' ');
				writer.write(dstClsName);
				writer.write('/');
				writer.write(dstName);
				writer.write(' ');
				writer.write(dstDesc);
				writer.write('\n');
				break;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void acceptField(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) {
		try {
			switch (format) {
			case TINY:
			case TINY_GZIP:
				writer.write("FIELD\t");
				writer.write(srcClsName);
				writer.write('\t');
				writer.write(srcDesc);
				writer.write('\t');
				writer.write(srcName);
				writer.write('\t');
				writer.write(dstName);
				writer.write('\n');
				break;
			case SRG:
				writer.write("FD: ");
				writer.write(srcClsName);
				writer.write('/');
				writer.write(srcName);
				writer.write(' ');
				writer.write(dstClsName);
				writer.write('/');
				writer.write(dstName);
				writer.write('\n');
				break;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void flush() throws IOException {
		writer.flush();
	}

	@Override
	public void close() throws IOException {
		writer.close();
	}

	private final MappingFormat format;
	private final Writer writer;
}
