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
import java.util.stream.Stream;

import matcher.Matcher;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.InputFile;
import matcher.type.LocalClassEnv;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class MatchesIo {
	public static void read(Path path, List<Path> inputDirs, boolean verifyInputs, Matcher matcher, DoubleConsumer progressReceiver) {
		ClassEnvironment env = matcher.getEnv();

		try (BufferedReader reader = Files.newBufferedReader(path)) {
			ParserState state = ParserState.START;
			List<InputFile> cpFiles = new ArrayList<>();
			List<InputFile> inputFilesA = new ArrayList<>();
			List<InputFile> inputFilesB = new ArrayList<>();
			List<InputFile> cpFilesA = new ArrayList<>();
			List<InputFile> cpFilesB = new ArrayList<>();
			String nonObfuscatedClassPatternA = "";
			String nonObfuscatedClassPatternB = "";
			String nonObfuscatedMemberPatternA = "";
			String nonObfuscatedMemberPatternB = "";
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
						long size = InputFile.unknownSize;
						byte[] hash = null;

						if (pos == -1) {
							pos = 2;
						} else if (!verifyInputs) {
							if (pos == 2 || pos + 1 >= line.length()) throw new IOException("invalid matches file");
							pos = line.indexOf('\t', pos + 1);
							if (pos == -1 || pos == pos + 1 || pos + 1 >= line.length()) throw new IOException("invalid matches file");
							pos++;
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
							if (line.startsWith("\tnon-obf cls a\t")) {
								nonObfuscatedClassPatternA = line.substring("\tnon-obf cls a\t".length());
							} else if (line.startsWith("\tnon-obf cls b\t")) {
								nonObfuscatedClassPatternB = line.substring("\tnon-obf cls b\t".length());
							} else if (line.startsWith("\tnon-obf mem a\t")) {
								nonObfuscatedMemberPatternA = line.substring("\tnon-obf mem a\t".length());
							} else if (line.startsWith("\tnon-obf mem b\t")) {
								nonObfuscatedMemberPatternB = line.substring("\tnon-obf mem b\t".length());
							} else {
								throw new IOException("invalid header: "+line);
							}
						}
					}
				} else {
					if (state != ParserState.CONTENT) {
						state = ParserState.CONTENT;

						if (inputDirs != null) {
							matcher.initFromMatches(inputDirs, inputFilesA, inputFilesB, cpFiles, cpFilesA, cpFilesB,
									nonObfuscatedClassPatternA, nonObfuscatedClassPatternB, nonObfuscatedMemberPatternA, nonObfuscatedMemberPatternB,
									progressReceiver);
							inputDirs = null;
						}
					}

					if (line.startsWith("c\t")) { // class
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
						} else if (!currentClass.isMatchable() || !target.isMatchable()) {
							System.err.println("Unmatchable a/b class "+idA+"/"+idB);
							currentClass = null;
						} else {
							matcher.match(currentClass, target);
						}
					} else if (line.startsWith("cu\t")) { // class unmatchable
						char side;
						if (line.length() < 6 || (side = line.charAt(3)) != 'a' && side != 'b' || line.charAt(4) != '\t') throw new IOException("invalid matches file");

						String id = line.substring(5);
						ClassInstance cls = side == 'a' ? env.getLocalClsByIdA(id) : env.getLocalClsByIdB(id);
						currentClass = null;
						currentMethod = null;

						if (cls == null) {
							System.err.println("Unknown "+side+" class "+id);
						} else {
							if (cls.hasMatch()) matcher.unmatch(cls);
							cls.setMatchable(false);
						}
					} else if (line.startsWith("\tm\t") || line.startsWith("\tf\t")) { // method or field
						currentMethod = null;

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
								} else if (!a.isMatchable() || !b.isMatchable()) {
									System.err.println("Unmatchable a/b method "+idA+"/"+idB);
									currentMethod = null;
								} else {
									matcher.match(a, b);
								}
							} else {
								FieldInstance a = currentClass.getField(idA);
								FieldInstance b;

								if (a == null) {
									System.err.println("Unknown a field "+idA+" in class "+currentClass);
								} else if ((b = currentClass.getMatch().getField(idB)) == null) {
									System.err.println("Unknown b field "+idB+" in class "+currentClass.getMatch());
								} else if (!a.isMatchable() || !b.isMatchable()) {
									System.err.println("Unmatchable a/b field "+idA+"/"+idB);
								} else {
									matcher.match(a, b);
								}
							}
						}
					} else if (line.startsWith("\tmu\t") || line.startsWith("\tfu\t")) { // method or field unmatchable
						currentMethod = null;

						if (currentClass != null) {
							char side;
							if (line.length() < 7 || (side = line.charAt(4)) != 'a' && side != 'b' || line.charAt(5) != '\t') throw new IOException("invalid matches file");

							String id = line.substring(6);
							ClassInstance cls = side == 'a' ? currentClass : currentClass.getMatch();
							assert cls != null; // currentClass must have been matched before, so shouldn't be null
							MemberInstance<?> member = line.charAt(1) == 'm' ? cls.getMethod(id) : cls.getField(id);

							if (member == null) {
								System.err.println("Unknown member "+id+" in class "+cls);
							} else {
								if (member.hasMatch()) matcher.unmatch(member);

								if (member instanceof MethodInstance) {
									for (MemberInstance<?> m : member.getAllHierarchyMembers()) {
										m.setMatchable(false);
									}
								} else {
									member.setMatchable(false);
								}
							}
						}
					} else if (line.startsWith("\t\tma\t") || line.startsWith("\t\tmv\t")) { // method arg or method var
						if (currentMethod != null) {
							int pos = line.indexOf('\t', 5);
							if (pos == -1 || pos == 5 || pos + 1 == line.length()) throw new IOException("invalid matches file");

							int idxA = Integer.parseInt(line.substring(5, pos));
							int idxB = Integer.parseInt(line.substring(pos + 1));
							MethodInstance matchedMethod = currentMethod.getMatch();
							assert matchedMethod != null; // matchedMethod must have been matched before, so shouldn't be null

							MethodVarInstance[] varsA, varsB;
							String type;

							if (line.charAt(3) == 'a') {
								type = "arg";
								varsA = currentMethod.getArgs();
								varsB = matchedMethod.getArgs();
							} else {
								type = "var";
								varsA = currentMethod.getVars();
								varsB = matchedMethod.getVars();
							}

							if (idxA < 0 || idxA >= varsA.length) {
								System.err.println("Unknown a method "+type+" "+idxA+" in method "+currentMethod);
							} else if (idxB < 0 || idxB >= varsB.length) {
								System.err.println("Unknown b method "+type+" "+idxB+" in method "+matchedMethod);
							} else if (!varsA[idxA].isMatchable() || !varsB[idxB].isMatchable()) {
								System.err.println("Unmatchable a/b method "+type+" "+idxA+"/"+idxB+" in method "+currentMethod+"/"+matchedMethod);
								currentMethod = null;
							} else {
								matcher.match(varsA[idxA], varsB[idxB]);
							}
						}
					} else if (line.startsWith("\t\tmau\t") || line.startsWith("\t\tmvu\t")) { // method arg or method var unmatchable
						if (currentMethod != null) {
							char side;
							if (line.length() < 9 || (side = line.charAt(6)) != 'a' && side != 'b' || line.charAt(7) != '\t') throw new IOException("invalid matches file");

							MethodInstance method = side == 'a' ? currentMethod : currentMethod.getMatch();
							assert method != null; // currentMethod must have been matched before, so shouldn't be null

							int idx = Integer.parseInt(line.substring(8));

							MethodVarInstance[] vars;
							String type;

							if (line.charAt(3) == 'a') {
								type = "arg";
								vars = method.getArgs();
							} else {
								type = "var";
								vars = method.getVars();
							}

							if (idx < 0 || idx >= vars.length) {
								System.err.println("Unknown a method "+type+" "+idx+" in method "+method);
							} else {
								MethodVarInstance var = vars[idx];

								if (var.hasMatch()) matcher.unmatch(var);
								var.setMatchable(false);
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
		List<ClassInstance> classes = Stream.concat(env.getClassesA().stream()
				.filter(cls -> cls.hasMatch() || !cls.isMatchable())
				.sorted(Comparator.comparing(cls -> cls.getId())),
				env.getClassesB().stream()
				.filter(cls -> !cls.isMatchable())
				.sorted(Comparator.comparing(cls -> cls.getId())))
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

			if (env.getNonObfuscatedClassPatternA() != null) {
				writer.write("\tnon-obf cls a\t");
				writer.write(env.getNonObfuscatedClassPatternA().toString());
				writer.write('\n');
			}

			if (env.getNonObfuscatedClassPatternB() != null) {
				writer.write("\tnon-obf cls b\t");
				writer.write(env.getNonObfuscatedClassPatternB().toString());
				writer.write('\n');
			}

			if (env.getNonObfuscatedMemberPatternA() != null) {
				writer.write("\tnon-obf mem a\t");
				writer.write(env.getNonObfuscatedMemberPatternA().toString());
				writer.write('\n');
			}

			if (env.getNonObfuscatedMemberPatternB() != null) {
				writer.write("\tnon-obf mem b\t");
				writer.write(env.getNonObfuscatedMemberPatternB().toString());
				writer.write('\n');
			}

			LocalClassEnv envA = env.getEnvA();

			for (ClassInstance cls : classes) {
				assert !cls.isShared();

				writeClass(cls, cls.getEnv() == envA ? 'a' : 'b', writer);
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

	private static void writeClass(ClassInstance cls, char side, Writer out) throws IOException {
		if (cls.hasMatch()) {
			out.write("c\t");
			out.write(cls.getId());
			out.write('\t');
			out.write(cls.getMatch().getId());
			out.write('\n');

			for (MethodInstance method : cls.getMethods()) {
				if (method.hasMatch() || !method.isMatchable()) {
					writeMethod(method, 'a', out);
				}
			}

			for (FieldInstance field : cls.getFields()) {
				if (field.hasMatch() || !field.isMatchable()) {
					writeMemberMain(field, 'a', out);
				}
			}

			for (MethodInstance method : cls.getMatch().getMethods()) {
				if (!method.isMatchable()) {
					writeMethod(method, 'b', out);
				}
			}

			for (FieldInstance field : cls.getMatch().getFields()) {
				if (!field.isMatchable()) {
					writeMemberMain(field, 'b', out);
				}
			}
		} else {
			assert !cls.isMatchable();

			out.write("cu\t");
			out.write(side);
			out.write('\t');
			out.write(cls.getId());
			out.write('\n');
		}
	}

	private static void writeMethod(MethodInstance method, char side, Writer out) throws IOException {
		writeMemberMain(method, side, out);

		if (method.hasMatch()) {
			for (MethodVarInstance arg : method.getArgs()) {
				if (arg.hasMatch() || !arg.isMatchable()) {
					writeVar(arg, 'a', out);
				}
			}

			for (MethodVarInstance var : method.getVars()) {
				if (var.hasMatch() || !var.isMatchable()) {
					writeVar(var, 'a', out);
				}
			}

			for (MethodVarInstance arg : method.getMatch().getArgs()) {
				if (!arg.isMatchable()) {
					writeVar(arg, 'b', out);
				}
			}

			for (MethodVarInstance var : method.getMatch().getVars()) {
				if (!var.isMatchable()) {
					writeVar(var, 'b', out);
				}
			}
		}
	}

	private static void writeMemberMain(MemberInstance<?> member, char side, Writer out) throws IOException {
		out.write('\t');
		out.write(member instanceof MethodInstance ? 'm' : 'f');

		if (member.hasMatch()) {
			out.write('\t');
			out.write(member.getId());
			out.write('\t');
			out.write(member.getMatch().getId());
		} else {
			assert !member.isMatchable();

			out.write("u\t");
			out.write(side);
			out.write('\t');
			out.write(member.getId());
		}

		out.write('\n');
	}

	private static void writeVar(MethodVarInstance var, char side, Writer out) throws IOException {
		out.write("\t\tm");
		out.write(var.isArg() ? 'a' : 'v');

		if (var.hasMatch()) {
			out.write('\t');
			out.write(Integer.toString(var.getIndex()));
			out.write('\t');
			out.write(Integer.toString(var.getMatch().getIndex()));
		} else {
			assert !var.isMatchable();

			out.write("u\t");
			out.write(side);
			out.write('\t');
			out.write(Integer.toString(var.getIndex()));
		}

		out.write('\n');
	}

	private enum ParserState {
		START, HEADER, FILES_A, FILES_B, CP_FILES, CP_FILES_A, CP_FILES_B, CONTENT;
	}
}
