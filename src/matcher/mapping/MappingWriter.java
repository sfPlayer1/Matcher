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

import matcher.NameType;

public class MappingWriter implements IMappingAcceptor, Closeable {
	public MappingWriter(Path file, MappingFormat format, NameType srcType, NameType dstType) throws IOException {
		this.format = format;

		switch (format) {
		case TINY:
		case SRG:
			writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			enigmaState = null;
			break;
		case TINY_GZIP:
			writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8));
			enigmaState = null;
			break;
		case ENIGMA:
			writer = null;
			enigmaState = new EnigmaMappingState(file);
			break;
		default:
			throw new IllegalArgumentException("invalid  mapping format: "+format.name());
		}

		if (format == MappingFormat.TINY || format == MappingFormat.TINY_GZIP) {
			writer.write("v1\t");
			writer.write(getTinyNameType(srcType));
			writer.write('\t');
			writer.write(getTinyNameType(dstType));
			writer.write('\n');
		}
	}

	private static String getTinyNameType(NameType type) {
		switch (type) {
		case MAPPED_PLAIN: return "pomf";
		case PLAIN: return "official";
		case LOCTMP_PLAIN:
		case TMP_PLAIN:
			return "tmp";
		case UID_PLAIN: return "intermediary";
		case MAPPED_LOCTMP_PLAIN:
		case MAPPED_TMP_PLAIN:
			return "pomf-tmp";
		default: throw new IllegalArgumentException();
		}
	}

	@Override
	public void acceptClass(String srcName, String dstName, boolean includesOuterNames) {
		try {
			switch (format) {
			case TINY:
			case TINY_GZIP:
				if (dstName != null) {
					if (includesOuterNames) dstName = dstName.substring(dstName.lastIndexOf('$') + 1);

					writer.write("CLASS\t");
					writer.write(srcName);
					writer.write('\t');
					writer.write(dstName);
					writer.write('\n');
				}
				break;
			case SRG:
				if (dstName != null) {
					if (!includesOuterNames) throw new IllegalArgumentException("srg requires outer name context");

					writer.write("CL: ");
					writer.write(srcName);
					writer.write(' ');
					writer.write(dstName);
					writer.write('\n');
				}
				break;
			case ENIGMA:
				enigmaState.acceptClass(srcName, dstName, includesOuterNames);
				break;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void acceptClassComment(String srcName, String comment) {
		try {
			switch (format) {
			case TINY:
			case TINY_GZIP:
				writer.write("CLS-CMT\t");
				writer.write(srcName);
				writer.write('\t');
				writer.write(escape(comment));
				writer.write('\n');
				break;
			case SRG:
				// not supported
				break;
			case ENIGMA:
				enigmaState.acceptClassComment(srcName, comment);
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
				if (dstName != null) {
					writer.write("METHOD\t");
					writer.write(srcClsName);
					writer.write('\t');
					writer.write(srcDesc);
					writer.write('\t');
					writer.write(srcName);
					writer.write('\t');
					writer.write(dstName);
					writer.write('\n');
				}
				break;
			case SRG:
				if (dstName != null) {
					writer.write("MD: ");
					writer.write(srcClsName);
					writer.write('/');
					writer.write(srcName);
					writer.write(' ');
					writer.write(srcDesc);
					writer.write(' ');
					writer.write(dstClsName != null ? dstClsName : srcClsName);
					writer.write('/');
					writer.write(dstName);
					writer.write(' ');
					writer.write(dstDesc);
					writer.write('\n');
				}
				break;
			case ENIGMA:
				enigmaState.acceptMethod(srcClsName, srcName, srcDesc, dstClsName, dstName, dstDesc);
				break;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void acceptMethodComment(String srcClsName, String srcName, String srcDesc, String comment) {
		try {
			switch (format) {
			case TINY:
			case TINY_GZIP:
				writer.write("MTH-CMT\t");
				writer.write(srcClsName);
				writer.write('\t');
				writer.write(srcDesc);
				writer.write('\t');
				writer.write(srcName);
				writer.write('\t');
				writer.write(escape(comment));
				writer.write('\n');
				break;
			case SRG:
				// not supported
				break;
			case ENIGMA:
				enigmaState.acceptMethodComment(srcClsName, srcName, srcDesc, comment);
				break;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void acceptMethodArg(String srcClsName, String srcName, String srcDesc, int argIndex, int lvIndex, String dstArgName) {
		try {
			switch (format) {
			case TINY:
			case TINY_GZIP:
				writer.write("MTH-ARG\t");
				writer.write(srcClsName);
				writer.write('\t');
				writer.write(srcDesc);
				writer.write('\t');
				writer.write(srcName);
				writer.write('\t');
				writer.write(Integer.toString(argIndex));
				writer.write('\t');
				writer.write(dstArgName);
				writer.write('\n');
				break;
			case SRG:
				// not supported
				break;
			case ENIGMA:
				enigmaState.acceptMethodArg(srcClsName, srcName, srcDesc, argIndex, lvIndex, dstArgName);
				break;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void acceptMethodVar(String srcClsName, String srcName, String srcDesc, int varIndex, int lvIndex, String dstVarName) {
		try {
			switch (format) {
			case TINY:
			case TINY_GZIP:
				writer.write("MTH-VAR\t");
				writer.write(srcClsName);
				writer.write('\t');
				writer.write(srcDesc);
				writer.write('\t');
				writer.write(srcName);
				writer.write('\t');
				writer.write(Integer.toString(varIndex));
				writer.write('\t');
				writer.write(dstVarName);
				writer.write('\n');
				break;
			case SRG:
				// not supported
				break;
			case ENIGMA:
				enigmaState.acceptMethodVar(srcClsName, srcName, srcDesc, varIndex, lvIndex, dstVarName);
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
				if (dstName != null) {
					writer.write("FIELD\t");
					writer.write(srcClsName);
					writer.write('\t');
					writer.write(srcDesc);
					writer.write('\t');
					writer.write(srcName);
					writer.write('\t');
					writer.write(dstName);
					writer.write('\n');
				}
				break;
			case SRG:
				if (dstName != null) {
					writer.write("FD: ");
					writer.write(srcClsName);
					writer.write('/');
					writer.write(srcName);
					writer.write(' ');
					writer.write(dstClsName != null ? dstClsName : srcClsName);
					writer.write('/');
					writer.write(dstName);
					writer.write('\n');
				}
				break;
			case ENIGMA:
				enigmaState.acceptField(srcClsName, srcName, srcDesc, dstClsName, dstName, dstDesc);
				break;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void acceptFieldComment(String srcClsName, String srcName, String srcDesc, String comment) {
		try {
			switch (format) {
			case TINY:
			case TINY_GZIP:
				writer.write("FLD-CMT\t");
				writer.write(srcClsName);
				writer.write('\t');
				writer.write(srcDesc);
				writer.write('\t');
				writer.write(srcName);
				writer.write('\t');
				writer.write(escape(comment));
				writer.write('\n');
				break;
			case SRG:
				// not supported
				break;
			case ENIGMA:
				enigmaState.acceptFieldComment(srcClsName, srcName, srcDesc, comment);
				break;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void acceptMeta(String key, String value) {
		try {
			switch (format) {
			case TINY:
			case TINY_GZIP:
				switch (key) {
				case Mappings.metaUidNextClass:
				case Mappings.metaUidNextMethod:
				case Mappings.metaUidNextField:
					writer.write("# INTERMEDIARY-COUNTER ");
					writer.write(key.equals(Mappings.metaUidNextClass) ? "class" : (key.equals(Mappings.metaUidNextMethod) ? "method" : "field"));
					writer.write(' ');
					writer.write(value);
					writer.write('\n');
					break;
				default:
					// not supported
				}
				break;
			case MCP:
			case SRG:
				// not supported
				break;
			case ENIGMA:
				enigmaState.acceptMeta(key, value);
				break;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void flush() throws IOException {
		if (writer != null) writer.flush();
	}

	@Override
	public void close() throws IOException {
		if (writer != null) writer.close();
		if (enigmaState != null) enigmaState.save();
	}

	private static String escape(String str) {
		StringBuilder ret = null;
		int len = str.length();
		int start = 0;

		for (int i = 0; i < len; i++) {
			char c = str.charAt(i);

			if (c == '\t' || c == '\n' || c == '\r' || c == '\\') {
				if (ret == null) ret = new StringBuilder(len * 2);

				ret.append(str, start, i);

				switch (c) {
				case '\t': ret.append("\\t"); break;
				case '\n': ret.append("\\n"); break;
				case '\r': ret.append("\\r"); break;
				case '\\': ret.append("\\\\"); break;
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

	private final MappingFormat format;
	private final Writer writer;
	private final EnigmaMappingState enigmaState;
}
