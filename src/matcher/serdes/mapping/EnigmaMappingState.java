package matcher.serdes.mapping;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class EnigmaMappingState implements IMappingAcceptor{
	EnigmaMappingState(Path dstPath) {
		this.dstPath = dstPath.toAbsolutePath();
	}

	@Override
	public void acceptClass(String srcName, String dstName) {
		if (dstName != null && dstName.isEmpty()) throw new IllegalArgumentException("empty dst name for "+srcName);

		getClass(srcName).mappedName = dstName;
	}

	@Override
	public void acceptClassComment(String srcName, String comment) {
		// not supported
	}

	@Override
	public void acceptMethod(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) {
		getMethod(srcClsName, srcName, srcDesc).mappedName = dstName;
	}

	@Override
	public void acceptMethodComment(String srcClsName, String srcName, String srcDesc, String comment) {
		// not supported
	}

	@Override
	public void acceptMethodArg(String srcClsName, String srcName, String srcDesc, int argIndex, int lvtIndex, String dstArgName) {
		assert dstArgName != null;

		getMethod(srcClsName, srcName, srcDesc).args.add(new EnigmaMappingMethodArg(argIndex, dstArgName));
	}

	@Override
	public void acceptField(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) {
		assert dstName != null;

		getClass(srcClsName).fields.add(new EnigmaMappingField(srcName, srcDesc, dstName));
	}

	@Override
	public void acceptFieldComment(String srcClsName, String srcName, String srcDesc, String comment) {
		// not supported
	}

	void save() throws IOException {
		for (EnigmaMappingClass cls : classes.values()) {
			String name = cls.mappedName != null ? cls.mappedName : cls.name;
			Path path = dstPath.resolve(name+".mapping").toAbsolutePath();
			if (!path.startsWith(dstPath)) throw new RuntimeException("invalid mapped name: "+name);

			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
				processClass(cls, writer);
			}
		}
	}

	private static void processClass(EnigmaMappingClass cls, Writer writer) throws IOException {
		String prefix = repeatTab(cls.level);

		writer.write(prefix);
		writer.write("CLASS ");
		writer.write(cls.name);

		if (cls.mappedName != null) {
			writer.write(' ');
			writer.write(cls.mappedName);
		}

		writer.write('\n');

		if (!cls.innerClasses.isEmpty()) {
			List<EnigmaMappingClass> classes = new ArrayList<>(cls.innerClasses.values());
			classes.sort(Comparator.naturalOrder());

			for (EnigmaMappingClass innerCls : classes) {
				processClass(innerCls, writer);
			}
		}

		if (!cls.fields.isEmpty()) {
			List<EnigmaMappingField> fields = new ArrayList<>(cls.fields);
			fields.sort(Comparator.naturalOrder());

			for (EnigmaMappingField field : fields) {
				writer.write(prefix);
				writer.write("\tFIELD ");
				writer.write(field.name);
				writer.write(' ');
				writer.write(field.mappedName);
				writer.write(' ');
				writer.write(field.desc);
				writer.write('\n');
			}
		}

		if (!cls.methods.isEmpty()) {
			List<EnigmaMappingMethod> methods = new ArrayList<>(cls.methods.values());
			methods.sort(Comparator.naturalOrder());

			for (EnigmaMappingMethod method : methods) {
				writer.write(prefix);
				writer.write("\tMETHOD ");
				writer.write(method.name);

				if (method.mappedName != null) {
					writer.write(' ');
					writer.write(method.mappedName);
				}

				writer.write(' ');
				writer.write(method.desc);
				writer.write('\n');

				if (!method.args.isEmpty()) {
					List<EnigmaMappingMethodArg> args = new ArrayList<>(method.args);
					args.sort(Comparator.naturalOrder());

					for (EnigmaMappingMethodArg arg : args) {
						writer.write(prefix);
						writer.write("\t\tARG ");
						writer.write(Integer.toString(arg.index));
						writer.write(' ');
						writer.write(arg.mappedName);
						writer.write('\n');
					}
				}
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

			return parent.innerClasses.computeIfAbsent(name, cName -> new EnigmaMappingClass(cName, parent.level + 1));
		} else {
			return classes.computeIfAbsent(name, cName -> new EnigmaMappingClass(cName, 0));
		}
	}

	private EnigmaMappingMethod getMethod(String className, String name, String desc) {
		String nameDesc = name+desc;

		return getClass(className).methods.computeIfAbsent(nameDesc, ignore -> new EnigmaMappingMethod(name, desc));
	}

	private static class EnigmaMappingClass implements Comparable<EnigmaMappingClass> {
		EnigmaMappingClass(String name, int level) {
			this.name = name;
			this.level = level;
		}

		@Override
		public int compareTo(EnigmaMappingClass o) {
			if (name.length() != o.name.length()) {
				return Integer.compare(name.length(), o.name.length());
			} else {
				return name.compareTo(o.name);
			}
		}

		final String name;
		String mappedName;
		final int level;
		final Map<String, EnigmaMappingMethod> methods = new HashMap<>();
		final List<EnigmaMappingField> fields = new ArrayList<>();
		final Map<String, EnigmaMappingClass> innerClasses = new HashMap<>();
	}

	private static class EnigmaMappingMethod implements Comparable<EnigmaMappingMethod> {
		EnigmaMappingMethod(String name, String desc) {
			this.name = name;
			this.desc = desc;
			this.nameDesc = name+desc;
		}

		@Override
		public int compareTo(EnigmaMappingMethod o) {
			return nameDesc.compareTo(o.nameDesc);
		}

		final String name;
		final String desc;
		private final String nameDesc;
		String mappedName;
		final List<EnigmaMappingMethodArg> args = new ArrayList<>();
	}

	private static class EnigmaMappingMethodArg implements Comparable<EnigmaMappingMethodArg> {
		EnigmaMappingMethodArg(int index, String mappedName) {
			this.index = index;
			this.mappedName = mappedName;
		}

		@Override
		public int compareTo(EnigmaMappingMethodArg o) {
			return Integer.compare(index, o.index);
		}

		final int index;
		final String mappedName;
	}

	private static class EnigmaMappingField implements Comparable<EnigmaMappingField> {
		EnigmaMappingField(String name, String desc, String mappedName) {
			this.name = name;
			this.desc = desc;
			this.nameDesc = name+desc;
			this.mappedName = mappedName;
		}

		@Override
		public int compareTo(EnigmaMappingField o) {
			return nameDesc.compareTo(o.nameDesc);
		}

		final String name;
		final String desc;
		private final String nameDesc;
		final String mappedName;
	}

	private final Path dstPath;
	private final Map<String, EnigmaMappingClass> classes = new HashMap<>();
}
