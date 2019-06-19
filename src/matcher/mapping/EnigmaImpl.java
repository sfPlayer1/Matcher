package matcher.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.stream.Stream;

import matcher.mapping.MappingState.ArgMappingState;
import matcher.mapping.MappingState.ClassMappingState;
import matcher.mapping.MappingState.FieldMappingState;
import matcher.mapping.MappingState.MethodMappingState;
import matcher.mapping.MappingState.VarMappingState;
import matcher.type.ClassInstance;

class EnigmaImpl {
	public static void read(Path dir, IMappingAcceptor mappingAcceptor) throws IOException {
		try (Stream<Path> stream = Files.find(dir,
				Integer.MAX_VALUE,
				(path, attr) -> attr.isRegularFile() && path.getFileName().toString().endsWith(".mapping"),
				FileVisitOption.FOLLOW_LINKS)) {
			stream.forEach(file -> readFile(file, mappingAcceptor));
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	private static void readFile(Path file, IMappingAcceptor mappingAcceptor) {
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
				case "CLASS": {
					if (parts.length < 2 || parts.length > 3) throw new IOException("invalid enigma line (missing/extra columns): "+line);

					String srcName = parts[1];

					if (!contextStack.isEmpty() && !ClassInstance.hasOuterName(srcName)) { // recent Enigma doesn't include the outer names, but the class must be an inner class
						assert contextStack.peek().startsWith("C");
						srcName = ClassInstance.getNestedName(contextStack.peek().substring(1), srcName);
					}

					contextStack.add("C"+srcName);
					indent++;
					if (parts.length == 3) mappingAcceptor.acceptClass(srcName, parts[2], false);
					break;
				}
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
					int lvIndex = Integer.parseInt(parts[1]);
					String name = parts[2];

					if (parts[0].equals("ARG")) {
						mappingAcceptor.acceptMethodArg(srcClsName, srcMethodName, srcMethodDesc, -1, lvIndex, null, name);
					} else {
						mappingAcceptor.acceptMethodVar(srcClsName, srcMethodName, srcMethodDesc, -1, lvIndex, -1, -1, null, name);
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

	public static void write(Path file, MappingState state) throws IOException {
		file = file.toAbsolutePath();

		for (ClassMappingState clsState : state.classMap.values()) {
			if (ClassInstance.hasOuterName(clsState.name)) continue;

			String name = clsState.mappedName != null ? clsState.mappedName : clsState.name;
			Path path = file.resolve(name+".mapping").toAbsolutePath();
			if (!path.startsWith(file)) throw new RuntimeException("invalid mapped name: "+name);

			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
				writeClass(clsState, "", writer);
			}
		}
	}

	private static void writeClass(ClassMappingState clsState, String prefix, Writer writer) throws IOException {
		writer.write(prefix);
		writer.write("CLASS ");
		writer.write(ClassInstance.getInnerName(clsState.name));

		if (clsState.mappedName != null) {
			writer.write(' ');
			writer.write(ClassInstance.getInnerName(clsState.mappedName));
		}

		writer.write('\n');

		if (!clsState.innerClasses.isEmpty()) {
			List<ClassMappingState> classes = new ArrayList<>(clsState.innerClasses);
			classes.sort(classComparator);

			for (ClassMappingState innerCls : classes) {
				writeClass(innerCls, prefix.concat("\t"), writer);
			}
		}

		if (!clsState.fieldMap.isEmpty()) {
			List<FieldMappingState> fields = new ArrayList<>(clsState.fieldMap.values());
			fields.sort(fieldComparator);

			for (FieldMappingState field : fields) {
				writer.write(prefix);
				writer.write("\tFIELD ");
				writer.write(field.name);

				if (field.mappedName != null) {
					writer.write(' ');
					writer.write(field.mappedName);
				}

				writer.write(' ');
				writer.write(field.desc);
				writer.write('\n');
			}
		}

		if (!clsState.methodMap.isEmpty()) {
			List<MethodMappingState> methods = new ArrayList<>(clsState.methodMap.values());
			methods.sort(methodComparator);

			for (MethodMappingState method : methods) {
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

				if (!method.argMap.isEmpty()) {
					List<ArgMappingState> args = new ArrayList<>(method.argMap.values());
					args.sort(argComparator);

					for (ArgMappingState arg : args) {
						writer.write(prefix);
						writer.write("\t\tARG ");
						writer.write(Integer.toString(arg.lvIndex));

						if (arg.mappedName != null) {
							writer.write(' ');
							writer.write(arg.mappedName);
						}

						writer.write('\n');
					}
				}

				if (!method.varMap.isEmpty()) {
					List<VarMappingState> vars = new ArrayList<>(method.varMap.values());
					vars.sort(varComparator);

					for (VarMappingState var : vars) {
						writer.write(prefix);
						writer.write("\t\tVAR ");
						writer.write(Integer.toString(var.asmIndex));

						if (var.mappedName != null) {
							writer.write(' ');
							writer.write(var.mappedName);
						}

						writer.write('\n');
					}
				}
			}
		}
	}

	private static final Comparator<ClassMappingState> classComparator = new Comparator<ClassMappingState>() {
		@Override
		public int compare(ClassMappingState a, ClassMappingState b) {
			if (a.name.length() != b.name.length()) {
				return Integer.compare(a.name.length(), b.name.length());
			} else {
				return a.name.compareTo(b.name);
			}
		}
	};

	private static final Comparator<MethodMappingState> methodComparator = new Comparator<MethodMappingState>() {
		@Override
		public int compare(MethodMappingState a, MethodMappingState b) {
			return a.name.concat(a.desc).compareTo(b.name.concat(b.desc));
		}
	};

	private static final Comparator<FieldMappingState> fieldComparator = new Comparator<FieldMappingState>() {
		@Override
		public int compare(FieldMappingState a, FieldMappingState b) {
			return a.name.concat(a.desc).compareTo(b.name.concat(b.desc));
		}
	};

	private static final Comparator<ArgMappingState> argComparator = new Comparator<ArgMappingState>() {
		@Override
		public int compare(ArgMappingState a, ArgMappingState b) {
			return Integer.compare(a.lvIndex, b.lvIndex);
		}
	};

	private static final Comparator<VarMappingState> varComparator = new Comparator<VarMappingState>() {
		@Override
		public int compare(VarMappingState a, VarMappingState b) {
			return Integer.compare(a.asmIndex, b.asmIndex);
		}
	};
}
