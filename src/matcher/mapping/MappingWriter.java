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
import matcher.mapping.MappingState.ArgMappingState;
import matcher.mapping.MappingState.VarMappingState;

public class MappingWriter implements MappingAcceptor, Closeable {
	public MappingWriter(Path file, MappingFormat format, NameType srcType, NameType dstType) throws IOException {
		this.file = file;
		this.format = format;
		this.srcType = srcType;
		this.dstType = dstType;

		switch (format) {
		case TINY:
		case SRG:
			writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			state = null;
			break;
		case TINY_GZIP:
			writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8));
			state = null;
			break;
		case ENIGMA:
		case TINY_2:
			writer = null;
			state = new MappingState();
			break;
		default:
			throw new IllegalArgumentException("invalid  mapping format: "+format.name());
		}

		if (format == MappingFormat.TINY || format == MappingFormat.TINY_GZIP) {
			writer.write("v1\t");
			writer.write(getTinyTypeName(srcType));
			writer.write('\t');
			writer.write(getTinyTypeName(dstType));
			writer.write('\n');
		}
	}

	static String getTinyTypeName(NameType type) {
		switch (type) {
		case MAPPED:
		case MAPPED_PLAIN:
			return "named";
		case PLAIN:
			return "official";
		case LOCTMP_PLAIN:
		case TMP_PLAIN:
			return "tmp";
		case UID_PLAIN:
			return "intermediary";
		case MAPPED_LOCTMP_PLAIN:
		case MAPPED_TMP_PLAIN:
			return "named-tmp";
		case AUX:
		case AUX2:
		case AUX_PLAIN:
		case AUX2_PLAIN:
			return type.getAuxIndex() > 0 ? String.format("aux%d", type.getAuxIndex()) : "aux";
		case MAPPED_AUX_PLAIN:
			return "named-aux";
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
			default:
				if (!includesOuterNames) throw new IllegalArgumentException("outer name context required");
				if (dstName != null) state.getClass(srcName).mappedName = dstName;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void acceptClassComment(String srcName, String comment) {
		switch (format) {
		case TINY:
		case TINY_GZIP:
		case SRG:
			// not supported
			break;
		default:
			state.getClass(srcName).comment = comment;
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
			default:
				if (dstName != null) state.getClass(srcClsName).getMethod(srcName, srcDesc).mappedName = dstName;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void acceptMethodComment(String srcClsName, String srcName, String srcDesc, String comment) {
		switch (format) {
		case TINY:
		case TINY_GZIP:
		case SRG:
			// not supported
			break;
		default:
			state.getClass(srcClsName).getMethod(srcName, srcDesc).comment = comment;
		}
	}

	@Override
	public void acceptMethodArg(String srcClsName, String srcMethodName, String srcMethodDesc,
			int argIndex, int lvIndex, String srcArgName, String dstArgName) {
		switch (format) {
		case TINY:
		case TINY_GZIP:
		case SRG:
			// not supported
			break;
		default:
			if (srcArgName != null || dstArgName != null) {
				ArgMappingState argState = state.getClass(srcClsName).getMethod(srcMethodName, srcMethodDesc).getArg(argIndex, lvIndex);
				argState.name = srcArgName;
				argState.mappedName = dstArgName;
			}
		}
	}

	@Override
	public void acceptMethodArgComment(String srcClsName, String srcMethodName, String srcMethodDesc,
			int argIndex, int lvIndex, String comment) {
		switch (format) {
		case TINY:
		case TINY_GZIP:
		case SRG:
			// not supported
			break;
		default:
			state.getClass(srcClsName).getMethod(srcMethodName, srcMethodDesc).getArg(argIndex, lvIndex).comment = comment;
		}
	}

	@Override
	public void acceptMethodVar(String srcClsName, String srcMethodName, String srcMethodDesc,
			int varIndex, int lvIndex, int startOpIdx, int asmIndex, String srcVarName, String dstVarName) {
		switch (format) {
		case TINY:
		case TINY_GZIP:
		case SRG:
			// not supported
			break;
		default:
			if (srcVarName != null || dstVarName != null) {
				VarMappingState varState = state.getClass(srcClsName).getMethod(srcMethodName, srcMethodDesc).getVar(varIndex, lvIndex, startOpIdx, asmIndex);
				varState.name = srcVarName;
				varState.mappedName = dstVarName;
			}
		}
	}

	@Override
	public void acceptMethodVarComment(String srcClsName, String srcMethodName, String srcMethodDesc,
			int varIndex, int lvIndex, int startOpIdx, int asmIndex, String comment) {
		switch (format) {
		case TINY:
		case TINY_GZIP:
		case SRG:
			// not supported
			break;
		default:
			state.getClass(srcClsName).getMethod(srcMethodName, srcMethodDesc).getArg(varIndex, lvIndex).comment = comment;
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
			default:
				if (dstName != null) state.getClass(srcClsName).getField(srcName, srcDesc).mappedName = dstName;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void acceptFieldComment(String srcClsName, String srcName, String srcDesc, String comment) {
		switch (format) {
		case TINY:
		case TINY_GZIP:
		case SRG:
			// not supported
			break;
		default:
			state.getClass(srcClsName).getField(srcName, srcDesc).comment = comment;
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
			default:
				state.metaMap.put(key, value);
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

		if (state != null) {
			switch (format) {
			case ENIGMA:
				EnigmaImpl.write(file, state);
				break;
			case TINY_2:
				Tiny2Impl.write(file, state, srcType, dstType);
				break;
			default:
				throw new UnsupportedOperationException();
			}
		}
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

	private final Path file;
	private final MappingFormat format;
	private final NameType srcType;
	private final NameType dstType;
	private final Writer writer;
	private final MappingState state;
}
