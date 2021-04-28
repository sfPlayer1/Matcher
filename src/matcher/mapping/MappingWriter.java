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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import matcher.mapping.MappingState.ArgMappingState;
import matcher.mapping.MappingState.VarMappingState;

public class MappingWriter implements FlatMappingVisitor, Closeable {
	public MappingWriter(Path file, MappingFormat format) throws IOException {
		this.file = file;
		this.format = format;

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
	}

	@Override
	public Set<MappingFlag> getFlags() {
		Set<MappingFlag> ret = EnumSet.of(MappingFlag.NEEDS_UNIQUENESS);

		ret.add(MappingFlag.NEEDS_SRC_METHOD_DESC);

		if (format == MappingFormat.SRG || format == MappingFormat.MCP) {
			ret.add(MappingFlag.NEEDS_DST_METHOD_DESC);
		} else {
			ret.add(MappingFlag.NEEDS_SRC_FIELD_DESC);
		}

		return ret;
	}

	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) {
		try {
			switch (format) {
			case TINY:
			case TINY_GZIP:
				writer.write("v1\t");
				writer.write(srcNamespace);

				for (String dstNamespace : dstNamespaces) {
					writer.write('\t');
					writer.write(dstNamespace);
				}

				writer.write('\n');
				break;
			case MCP:
			case SRG:
				// not supported
				break;
			default:
				state.srcNamespace = srcNamespace;
				state.dstNamespaces.clear();
				state.dstNamespaces.addAll(dstNamespaces);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void visitMetadata(String key, String value) {
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

	@Override
	public boolean visitClass(String srcName, String[] dstNames) {
		try {
			switch (format) {
			case TINY:
			case TINY_GZIP:
				if (isAnyPresent(dstNames)) {
					writer.write("CLASS\t");
					writer.write(srcName);

					for (String dstName : dstNames) {
						writer.write('\t');

						if (dstName != null) {
							writer.write(dstName);
						}
					}

					writer.write('\n');
				}
				break;
			case SRG:
				if (isFirstPresent(dstNames)) {
					writer.write("CL: ");
					writer.write(srcName);
					writer.write(' ');
					writer.write(dstNames[0]);
					writer.write('\n');
				}
				break;
			default:
				if (isFirstPresent(dstNames)) state.getClass(srcName).mappedName = dstNames[0];
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return true;
	}

	@Override
	public void visitClassComment(String srcName, String[] dstNames, String comment) {
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
	public boolean visitMethod(String srcClsName, String srcName, String srcDesc, String[] dstClsNames, String[] dstNames, String[] dstDescs) {
		try {
			switch (format) {
			case TINY:
			case TINY_GZIP:
				if (isAnyPresent(dstNames)) {
					writer.write("METHOD\t");
					writer.write(srcClsName);
					writer.write('\t');
					writer.write(srcDesc);
					writer.write('\t');
					writer.write(srcName);

					for (String dstName : dstNames) {
						writer.write('\t');
						if (dstName != null) writer.write(dstName);
					}

					writer.write('\n');
				}
				break;
			case SRG:
				if (isFirstPresent(dstNames)) {
					writer.write("MD: ");
					writer.write(srcClsName);
					writer.write('/');
					writer.write(srcName);
					writer.write(' ');
					writer.write(srcDesc);
					writer.write(' ');
					writer.write(isFirstPresent(dstClsNames) ? dstClsNames[0] : srcClsName); // TODO: handle null better
					writer.write('/');
					writer.write(dstNames[0]);
					writer.write(' ');
					writer.write(dstDescs[0]); // TODO: handle null
					writer.write('\n');
				}
				break;
			default:
				if (isFirstPresent(dstNames)) state.getClass(srcClsName).getMethod(srcName, srcDesc).mappedName = dstNames[0];
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return true;
	}

	@Override
	public void visitMethodComment(String srcClsName, String srcName, String srcDesc,
			String[] dstClsNames, String[] dstNames, String[] dstDescs,
			String comment) {
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
	public boolean visitMethodArg(String srcClsName, String srcMethodName, String srcMethodDesc, int argPosition, int lvIndex, String srcArgName,
			String[] dstClsNames, String[] dstMethodNames, String[] dstMethodDescs, String[] dstArgNames) {
		switch (format) {
		case TINY:
		case TINY_GZIP:
		case SRG:
			// not supported
			return false;
		default:
			if (srcArgName != null || isFirstPresent(dstArgNames)) {
				ArgMappingState argState = state.getClass(srcClsName).getMethod(srcMethodName, srcMethodDesc).getArg(argPosition, lvIndex);
				argState.name = srcArgName;
				argState.mappedName = dstArgNames[0];
			}
		}

		return true;
	}

	@Override
	public void visitMethodArgComment(String srcClsName, String srcMethodName, String srcMethodDesc, int argIndex, int lvIndex, String srcArgName,
			String[] dstClsNames, String[] dstMethodNames, String[] dstMethodDescs, String[] dstArgNames,
			String comment) {
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
	public boolean visitMethodVar(String srcClsName, String srcMethodName, String srcMethodDesc,
			int asmIndex, int lvIndex, int startOpIdx, String srcVarName,
			String[] dstClsNames, String[] dstMethodNames, String[] dstMethodDescs, String[] dstVarNames) {
		switch (format) {
		case TINY:
		case TINY_GZIP:
		case SRG:
			// not supported
			return false;
		default:
			if (srcVarName != null || isFirstPresent(dstVarNames)) {
				VarMappingState varState = state.getClass(srcClsName).getMethod(srcMethodName, srcMethodDesc).getVar(asmIndex, lvIndex, startOpIdx);
				varState.name = srcVarName;
				varState.mappedName = dstVarNames[0];
			}
		}

		return true;
	}

	@Override
	public void visitMethodVarComment(String srcClsName, String srcMethodName, String srcMethodDesc,
			int asmIndex, int lvIndex, int startOpIdx, String srcVarName,
			String[] dstClsNames, String[] dstMethodNames, String[] dstMethodDescs, String[] dstVarNames,
			String comment) {
		switch (format) {
		case TINY:
		case TINY_GZIP:
		case SRG:
			// not supported
			break;
		default:
			state.getClass(srcClsName).getMethod(srcMethodName, srcMethodDesc).getVar(asmIndex, lvIndex, startOpIdx).comment = comment;
		}
	}

	@Override
	public boolean visitField(String srcClsName, String srcName, String srcDesc, String[] dstClsNames, String[] dstNames, String[] dstDescs) {
		try {
			switch (format) {
			case TINY:
			case TINY_GZIP:
				if (isAnyPresent(dstNames)) {
					writer.write("FIELD\t");
					writer.write(srcClsName);
					writer.write('\t');
					writer.write(srcDesc);
					writer.write('\t');
					writer.write(srcName);

					for (String dstName : dstNames) {
						writer.write('\t');
						if (dstName != null) writer.write(dstName);
					}

					writer.write('\n');
				}
				break;
			case SRG:
				if (isFirstPresent(dstNames)) {
					writer.write("FD: ");
					writer.write(srcClsName);
					writer.write('/');
					writer.write(srcName);
					writer.write(' ');
					writer.write(isFirstPresent(dstClsNames) ? dstClsNames[0] : srcClsName);
					writer.write('/');
					writer.write(dstNames[0]);
					writer.write('\n');
				}
				break;
			default:
				if (isFirstPresent(dstNames)) state.getClass(srcClsName).getField(srcName, srcDesc).mappedName = dstNames[0];
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return true;
	}

	@Override
	public void visitFieldComment(String srcClsName, String srcName, String srcDesc,
			String[] dstClsNames, String[] dstNames, String[] dstDescs,
			String comment) {
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
				Tiny2Impl.write(file, state);
				break;
			default:
				throw new UnsupportedOperationException();
			}
		}
	}

	private static boolean isAnyPresent(String[] strs) {
		if (strs == null) return false;

		for (String s : strs) {
			if (s != null) return true;
		}

		return false;
	}

	private static boolean isFirstPresent(String[] strs) {
		return strs != null && strs.length > 0 && strs[0] != null;
	}

	private final Path file;
	private final MappingFormat format;
	private final Writer writer;
	private final MappingState state;
}
