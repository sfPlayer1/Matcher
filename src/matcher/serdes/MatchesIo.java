package matcher.serdes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;

import matcher.Matcher;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.InputFile;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class MatchesIo {
	public static void read(Path path, List<Path> inputDirs, Matcher matcher, DoubleConsumer progressReceiver) {
		ClassEnvironment env = matcher.getEnv();

		try (BufferedReader reader = Files.newBufferedReader(path)) {
			ParserState state = ParserState.START;
			List<InputFile> cpFiles = new ArrayList<>();
			List<InputFile> inputFilesA = new ArrayList<>();
			List<InputFile> inputFilesB = new ArrayList<>();
			List<InputFile> cpFilesA = new ArrayList<>();
			List<InputFile> cpFilesB = new ArrayList<>();
			ClassInstance currentClass = null;
			MethodInstance currentMethod = null;
			String line;

			while ((line = reader.readLine()) != null) {
				if (line.isEmpty()) continue;

				if (state == ParserState.START) {
					if (!line.startsWith("Matches saved")) throw new IOException("invalid matches file, incorrect header");
					state = ParserState.HEADER;
				} else if (state != ParserState.CONTENT && line.startsWith("\t")) {
					if (line.startsWith("\t\t")) {
						// class path entry: >>size>hash>filename or >>filename
						List<InputFile> inputFiles;

						switch (state) {
						case FILES_A:
							inputFiles = inputFilesA;
							break;
						case FILES_B:
							inputFiles = inputFilesB;
							break;
						case CP_FILES:
							inputFiles = cpFiles;
							break;
						case CP_FILES_A:
							inputFiles = cpFilesA;
							break;
						case CP_FILES_B:
							inputFiles = cpFilesB;
							break;
						default:
							throw new IllegalStateException(state.name());
						}

						int pos = line.indexOf('\t', 2);
						long size;
						byte[] hash;

						if (pos == -1) {
							size = -1;
							hash = null;
							pos = 2;
						} else {
							if (pos == 2 || pos + 1 >= line.length()) throw new IOException("invalid matches file");
							size = Long.parseLong(line.substring(2, pos));
							int pos2 = line.indexOf('\t', pos + 1);
							if (pos2 == -1 || pos2 == pos + 1 || pos2 + 1 >= line.length()) throw new IOException("invalid matches file");
							hash = Base64.getDecoder().decode(line.substring(pos + 1, pos2));
							pos = pos2 + 1;
						}

						inputFiles.add(new InputFile(line.substring(pos), size, hash));
					} else {
						switch (line.substring(1, line.length() - 1)) {
						case "a":
							state = ParserState.FILES_A;
							break;
						case "b":
							state = ParserState.FILES_B;
							break;
						case "cp":
							state = ParserState.CP_FILES;
							break;
						case "cp a":
							state = ParserState.CP_FILES_A;
							break;
						case "cp b":
							state = ParserState.CP_FILES_B;
							break;
						default:
							throw new IOException("invalid header: "+line);
						}
					}
				} else {
					if (state != ParserState.CONTENT) {
						state = ParserState.CONTENT;

						if (inputDirs != null) {
							matcher.initFromMatches(inputDirs, inputFilesA, inputFilesB, cpFiles, cpFilesA, cpFilesB, progressReceiver);
							inputDirs = null;
						}
					}

					if (line.startsWith("c\t")) {
						int pos = line.indexOf('\t', 2);
						if (pos == -1 || pos == 2 || pos + 1 == line.length()) throw new IOException("invalid matches file");
						String idA = line.substring(2, pos);
						String idB = line.substring(pos + 1);
						currentClass = env.getLocalClsByIdA(idA);
						currentMethod = null;
						ClassInstance target;

						if (currentClass == null) {
							System.err.println("Unknown a class "+idA);
						} else if ((target = env.getLocalClsByIdB(idB)) == null) {
							System.err.println("Unknown b class "+idA);
							currentClass = null;
						} else {
							matcher.match(currentClass, target);
						}
					} else if (line.startsWith("\tm\t") || line.startsWith("\tf\t")) {
						if (currentClass != null) {
							int pos = line.indexOf('\t', 3);
							if (pos == -1 || pos == 3 || pos + 1 == line.length()) throw new IOException("invalid matches file");
							String idA = line.substring(3, pos);
							String idB = line.substring(pos + 1);

							if (line.charAt(1) == 'm') {
								MethodInstance a = currentMethod = currentClass.getMethod(idA);
								MethodInstance b;

								if (a == null) {
									System.err.println("Unknown a method "+idA+" in class "+currentClass);
								} else if ((b = currentClass.getMatch().getMethod(idB)) == null) {
									System.err.println("Unknown b method "+idB+" in class "+currentClass.getMatch());
								} else {
									matcher.match(a, b);
								}
							} else {
								currentMethod = null;
								FieldInstance a = currentClass.getField(idA);
								FieldInstance b;

								if (a == null) {
									System.err.println("Unknown a field "+idA+" in class "+currentClass);
								} else if ((b = currentClass.getMatch().getField(idB)) == null) {
									System.err.println("Unknown b field "+idB+" in class "+currentClass.getMatch());
								} else {
									matcher.match(a, b);
								}
							}
						}
					} else if (line.startsWith("\t\tma\t")) {
						if (currentMethod != null) {
							int pos = line.indexOf('\t', 5);
							if (pos == -1 || pos == 5 || pos + 1 == line.length()) throw new IOException("invalid matches file");

							int idxA = Integer.parseInt(line.substring(5, pos));
							int idxB = Integer.parseInt(line.substring(pos + 1));
							MethodInstance matchedMethod = currentMethod.getMatch();

							if (idxA < 0 || idxA >= currentMethod.getArgs().length) {
								System.err.println("Unknown a method arg "+idxA+" in method "+currentMethod);
							} else if (matchedMethod == null) {
								System.err.println("Arg for unmatched method "+currentMethod);
							} else if (idxB < 0 || idxB >= matchedMethod.getArgs().length) {
								System.err.println("Unknown b method arg "+idxB+" in method "+matchedMethod);
							} else {
								matcher.match(currentMethod.getArg(idxA), matchedMethod.getArg(idxB));
							}
						}
					}
				}
			}

			if (state != ParserState.CONTENT) throw new IOException("invalid matches file");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}


	public static boolean write(Matcher matcher, Path path) throws IOException {
		ClassEnvironment env = matcher.getEnv();
		List<ClassInstance> classes = env.getClassesA().stream()
				.filter(cls -> cls.getMatch() != null)
				.sorted(Comparator.comparing(cls -> cls.getId()))
				.collect(Collectors.toList());
		if (classes.isEmpty()) return false;

		try (Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			writer.write("Matches saved ");
			writer.write(ZonedDateTime.now().toString());
			writer.write(", input files:\n\ta:\n");
			writeInputFiles(env.getInputFilesA(), writer);
			writer.write("\tb:\n");
			writeInputFiles(env.getInputFilesB(), writer);
			writer.write("\tcp:\n");
			writeInputFiles(env.getClassPathFiles(), writer);
			writer.write("\tcp a:\n");
			writeInputFiles(env.getClassPathFilesA(), writer);
			writer.write("\tcp b:\n");
			writeInputFiles(env.getClassPathFilesB(), writer);

			for (ClassInstance cls : classes) {
				assert !cls.isShared();

				writer.write("c\t");
				writer.write(cls.getId());
				writer.write('\t');
				writer.write(cls.getMatch().getId());
				writer.write('\n');

				writeMethods(cls.getMethods(), writer);
				writeFields(cls.getFields(), writer);
			}
		}

		return true;
	}

	private static void writeInputFiles(Iterable<InputFile> files, Writer out) throws IOException {
		for (InputFile file : files) {
			out.write("\t\t");
			out.write(Long.toString(file.size));
			out.write('\t');
			out.write(Base64.getEncoder().encodeToString(file.sha256));
			out.write('\t');
			out.write(file.path.getFileName().toString().replace('\n', ' '));
			out.write('\n');
		}
	}

	private static void writeMethods(MethodInstance[] methods, Writer out) throws IOException {
		for (MethodInstance method : methods) {
			if (method.getMatch() == null) continue;

			writeMemberMain(method, out, true);

			for (MethodVarInstance arg : method.getArgs()) {
				if (arg.getMatch() == null) continue;

				out.write("\t\tma\t");
				out.write(Integer.toString(arg.getIndex()));
				out.write('\t');
				out.write(Integer.toString(arg.getMatch().getIndex()));
				out.write('\n');
			}
		}
	}

	private static void writeFields(FieldInstance[] fields, Writer out) throws IOException {
		for (FieldInstance field : fields) {
			if (field.getMatch() == null) continue;

			writeMemberMain(field, out, false);
		}
	}

	private static void writeMemberMain(MemberInstance<?> member, Writer out, boolean isMethod) throws IOException {
		out.write('\t');
		out.write(isMethod ? 'm' : 'f');
		out.write('\t');
		out.write(member.getId());
		out.write('\t');
		out.write(member.getMatch().getId());
		out.write('\n');
	}

	private static enum ParserState {
		START, HEADER, FILES_A, FILES_B, CP_FILES, CP_FILES_A, CP_FILES_B, CONTENT;
	}
}
