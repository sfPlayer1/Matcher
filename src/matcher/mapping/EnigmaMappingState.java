package matcher.mapping;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class EnigmaMappingState implements IMappingAcceptor{
	EnigmaMappingState(Path dstPath) {
		this.dstPath = dstPath.toAbsolutePath();
	}

	@Override
	public void acceptClass(String srcName, String dstName) {
		if (dstName.isEmpty()) throw new IllegalArgumentException("empty dst name for "+srcName);

		EnigmaMappingClass cls = getClass(srcName);

		cls.mappedName = dstName;
		cls.line = "CLASS "+srcName+" "+dstName;
	}

	@Override
	public void acceptClassComment(String srcName, String comment) {
		// not supported
	}

	@Override
	public void acceptMethod(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) {
		getMethod(srcClsName, srcName, srcDesc).line = "\tMETHOD "+srcName+" "+dstName+" "+srcDesc;
	}

	@Override
	public void acceptMethodComment(String srcClsName, String srcName, String srcDesc, String comment) {
		// not supported
	}

	@Override
	public void acceptMethodArg(String srcClsName, String srcName, String srcDesc, int argIndex, String dstArgName) {
		getMethod(srcClsName, srcName, srcDesc).argLines.add("\t\tARG "+argIndex+" "+dstArgName);
	}

	@Override
	public void acceptField(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) {
		getClass(srcName).fieldLines.add("\tFIELD "+srcName+" "+dstName+" "+srcDesc);
	}

	@Override
	public void acceptFieldComment(String srcClsName, String srcName, String srcDesc, String comment) {
		// not supported
	}

	void save() throws IOException {
		for (EnigmaMappingClass cls : classes.values()) {
			Path path = dstPath.resolve(cls.mappedName+".mapping").toAbsolutePath();
			if (!path.startsWith(dstPath)) throw new RuntimeException("invalid mapped name: "+cls.mappedName);

			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
				processClass(cls, writer);
			}
		}
	}

	private static void processClass(EnigmaMappingClass cls, Writer writer) throws IOException {
		String prefix = repeatTab(cls.level);

		writer.write(prefix);

		if (cls.line == null) {
			writer.write("CLASS "+cls.mappedName);
		} else {
			writer.write(cls.line);
		}

		writer.write('\n');

		for (EnigmaMappingClass innerCls : cls.innerClasses.values()) {
			processClass(innerCls, writer);
		}

		for (String line : cls.fieldLines) {
			writer.write(prefix);
			writer.write(line);
			writer.write('\n');
		}

		for (EnigmaMappingMethod method : cls.methods.values()) {
			writer.write(prefix);
			writer.write(method.line);
			writer.write('\n');

			for (String line : method.argLines) {
				writer.write(prefix);
				writer.write(line);
				writer.write('\n');
			}
		}
	}

	private static String repeatTab(int times) {
		if (times == 0) return "";
		if (times == 1) return "\t";

		StringBuilder ret = new StringBuilder(times);
		while (times-- > 0) ret.append('\t');

		return ret.toString();
	}

	private EnigmaMappingClass getClass(String name) {
		int pos = name.lastIndexOf('$');

		if (pos > 0 && pos < name.length() - 1) {
			EnigmaMappingClass parent = getClass(name.substring(0, pos));

			return parent.innerClasses.computeIfAbsent(name, ignore -> new EnigmaMappingClass(name, parent.level + 1));
		} else {
			return classes.computeIfAbsent(name, cName -> new EnigmaMappingClass(cName, 0));
		}
	}

	private EnigmaMappingMethod getMethod(String className, String name, String desc) {
		return getClass(className).methods.computeIfAbsent(name+desc, ignore -> new EnigmaMappingMethod());
	}

	private static class EnigmaMappingClass {
		EnigmaMappingClass(String name, int level) {
			this.mappedName = name;
			this.level = level;
		}

		String mappedName;
		final int level;
		String line;
		final Map<String, EnigmaMappingMethod> methods = new LinkedHashMap<>();
		final List<String> fieldLines = new ArrayList<>();
		final Map<String, EnigmaMappingClass> innerClasses = new LinkedHashMap<>();
	}

	private static class EnigmaMappingMethod {
		String line;
		final List<String> argLines = new ArrayList<>();
	}

	private final Path dstPath;
	private final Map<String, EnigmaMappingClass> classes = new LinkedHashMap<>();
}
