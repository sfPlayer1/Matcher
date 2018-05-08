package matcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import matcher.classifier.ClassClassifier;
import matcher.classifier.ClassifierLevel;
import matcher.classifier.FieldClassifier;
import matcher.classifier.IRanker;
import matcher.classifier.MethodArgClassifier;
import matcher.classifier.MethodClassifier;
import matcher.classifier.RankResult;
import matcher.config.Config;
import matcher.config.ProjectConfig;
import matcher.serdes.mapping.IMappingAcceptor;
import matcher.serdes.mapping.MappingFormat;
import matcher.serdes.mapping.MappingReader;
import matcher.serdes.mapping.MappingWriter;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.IClassEnv;
import matcher.type.InputFile;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class Matcher {
	public static void init() {
		ClassClassifier.init();
		MethodClassifier.init();
		FieldClassifier.init();
		MethodArgClassifier.init();
	}

	public Matcher(ClassEnvironment env) {
		this.env = env;
	}

	public void init(ProjectConfig config, DoubleConsumer progressReceiver) {
		env.init(config, progressReceiver);

		matchUnobfuscated();
	}

	private void matchUnobfuscated() {
		for (ClassInstance cls : env.getClassesA()) {
			if (cls.isNameObfuscated(true)) continue;

			ClassInstance match = env.getLocalClsByIdB(cls.getId());

			if (match != null && !match.isNameObfuscated(true)) {
				match(cls, match);
			}
		}
	}

	public void reset() {
		env.reset();
	}

	public ClassEnvironment getEnv() {
		return env;
	}

	public ClassifierLevel getAutoMatchLevel() {
		return autoMatchLevel;
	}

	public void initFromMatches(List<Path> inputDirs,
			List<InputFile> inputFilesA, List<InputFile> inputFilesB,
			List<InputFile> cpFiles,
			List<InputFile> cpFilesA, List<InputFile> cpFilesB, DoubleConsumer progressReceiver) throws IOException {
		List<Path> pathsA = resolvePaths(inputDirs, inputFilesA);
		List<Path> pathsB = resolvePaths(inputDirs, inputFilesB);
		List<Path> sharedClassPath = resolvePaths(inputDirs, cpFiles);
		List<Path> classPathA = resolvePaths(inputDirs, cpFilesA);
		List<Path> classPathB = resolvePaths(inputDirs, cpFilesB);

		ProjectConfig config = new ProjectConfig(pathsA, pathsB, classPathA, classPathB, sharedClassPath, false);
		if (!config.isValid()) throw new IOException("invalid config");
		Config.setProjectConfig(config);
		Config.saveAsLast();

		reset();
		init(config, progressReceiver);
	}

	private static List<Path> resolvePaths(List<Path> inputDirs, List<InputFile> inputFiles) throws IOException {
		List<Path> ret = new ArrayList<>(inputFiles.size());

		for (InputFile inputFile : inputFiles) {
			boolean found = false;

			for (Path inputDir : inputDirs) {
				try (Stream<Path> matches = Files.find(inputDir, Integer.MAX_VALUE, (path, attr) -> inputFile.equals(path), FileVisitOption.FOLLOW_LINKS)) {
					Path file = matches.findFirst().orElse(null);

					if (file != null) {
						ret.add(file);
						found = true;
						break;
					}
				} catch (UncheckedIOException e) {
					throw e.getCause();
				}
			}

			if (!found) throw new IOException("can't find input "+inputFile.getFileName());
		}

		return ret;
	}

	public void readMappings(Path path, MappingFormat format, boolean forA, final boolean replace) throws IOException {
		int[] counts = new int[7];
		Set<String> warnedClasses = new HashSet<>();

		final IClassEnv env = forA ? this.env.getEnvA() : this.env.getEnvB();

		try {
			MappingReader.read(path, format, new IMappingAcceptor() {
				@Override
				public void acceptClass(String srcName, String dstName) {
					ClassInstance cls = env.getLocalClsByName(srcName);

					if (cls == null) {
						if (warnedClasses.add(srcName)) System.out.println("can't find mapped class "+srcName+" ("+dstName+")");
					} else {
						if (!cls.hasMappedName() || replace) cls.setMappedName(dstName);
						counts[0]++;
					}
				}

				@Override
				public void acceptClassComment(String srcName, String comment) {
					ClassInstance cls = env.getLocalClsByName(srcName);

					if (cls == null) {
						if (warnedClasses.add(srcName)) System.out.println("can't find mapped class "+srcName);
					} else {
						if (cls.getMappedComment() == null || replace) cls.setMappedComment(comment);
						counts[1]++;
					}
				}

				@Override
				public void acceptMethod(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) {
					ClassInstance cls = env.getLocalClsByName(srcClsName);
					MethodInstance method;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((method = cls.getMethod(srcName, srcDesc)) == null || !method.isReal()) {
						String mappedName = cls.getMappedName(false);
						System.out.println("can't find mapped method "+srcClsName+"/"+srcName+" ("+(mappedName != null ? mappedName+"/" : "")+dstName+")");
					} else {
						if (!method.hasMappedName() || replace) method.setMappedName(dstName);
						counts[2]++;
					}
				}

				@Override
				public void acceptMethodComment(String srcClsName, String srcName, String srcDesc, String comment) {
					ClassInstance cls = env.getLocalClsByName(srcClsName);
					MethodInstance method;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((method = cls.getMethod(srcName, srcDesc)) == null || !method.isReal()) {
						System.out.println("can't find mapped method "+srcClsName+"/"+srcName);
					} else {
						if (method.getMappedComment() == null || replace) method.setMappedComment(comment);
						counts[3]++;
					}
				}

				@Override
				public void acceptMethodArg(String srcClsName, String srcName, String srcDesc, int argIndex, int lvtIndex, String dstArgName) {
					ClassInstance cls = env.getLocalClsByName(srcClsName);
					MethodInstance method;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((method = cls.getMethod(srcName, srcDesc)) == null || !method.isReal()) {
						System.out.println("can't find mapped method "+srcClsName+"/"+srcName);
					} else if (argIndex < -1 || argIndex >= method.getArgs().length) {
						System.out.println("invalid arg index "+argIndex+" for method "+method);
					} else if (lvtIndex < -1 || lvtIndex >= method.getArgs().length * 2 + 1) {
						System.out.println("invalid lvt index "+lvtIndex+" for method "+method);
					} else {
						if (argIndex == -1) {
							if (lvtIndex <= -1) {
								System.out.println("missing arg+lvt index "+lvtIndex+" for method "+method);
								return;
							} else {
								for (MethodVarInstance arg : method.getArgs()) {
									if (arg.getLvtIndex() == lvtIndex) {
										argIndex = arg.getIndex();
										break;
									}
								}

								if (argIndex == -1) {
									System.out.println("invalid lvt index "+lvtIndex+" for method "+method);
									return;
								}
							}
						}

						MethodVarInstance arg = method.getArg(argIndex);

						if (lvtIndex != -1 && arg.getLvtIndex() != lvtIndex) {
							System.out.println("mismatched lvt index "+lvtIndex+" for method "+method);
							return;
						}

						if (arg.getMappedName(false) == null || replace) arg.setMappedName(dstArgName);
						counts[4]++;
					}
				}

				@Override
				public void acceptField(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) {
					ClassInstance cls = env.getLocalClsByName(srcClsName);
					FieldInstance field;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((field = cls.getField(srcName, srcDesc)) == null || !field.isReal()) {
						String mappedName = cls.getMappedName(false);
						System.out.println("can't find mapped field "+srcClsName+"/"+srcName+" ("+(mappedName != null ? mappedName+"/" : "")+dstName+")");
					} else {
						if (!field.hasMappedName() || replace) field.setMappedName(dstName);
						counts[5]++;
					}
				}

				@Override
				public void acceptFieldComment(String srcClsName, String srcName, String srcDesc, String comment) {
					ClassInstance cls = env.getLocalClsByName(srcClsName);
					FieldInstance field;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((field = cls.getField(srcName, srcDesc)) == null || !field.isReal()) {
						System.out.println("can't find mapped field "+srcClsName+"/"+srcName);
					} else {
						if (field.getMappedComment() == null || replace) field.setMappedComment(comment);
						counts[6]++;
					}
				}
			});
		} catch (Throwable t) {
			clearMappings();
			throw t;
		}

		System.out.printf("Loaded mappings for %d classes, %d methods (%d args) and %d fields (comments: %d/%d/%d).%n",
				counts[0], counts[2], counts[4], counts[5], counts[1], counts[3], counts[6]);
	}

	public void clearMappings() {
		for (ClassInstance cls : env.getClassesA()) {
			cls.setMappedName(null);
			cls.setMappedComment(null);

			for (MethodInstance method : cls.getMethods()) {
				method.setMappedName(null);
				method.setMappedComment(null);

				for (MethodVarInstance arg : method.getArgs()) {
					arg.setMappedName(null);
				}
			}

			for (FieldInstance field : cls.getFields()) {
				field.setMappedName(null);
				field.setMappedComment(null);
			}
		}
	}

	public boolean saveMappings(Path file, MappingFormat format) throws IOException {
		List<ClassInstance> classes = env.getClassesB().stream()
				.sorted(Comparator.comparing(ClassInstance::getId))
				.collect(Collectors.toList());
		if (classes.isEmpty()) return false;

		try (MappingWriter writer = new MappingWriter(file, format)) {
			for (ClassInstance cls : classes) {
				String clsName = cls.getName();
				String mappedClsName = cls.getMappedName(false);

				if (mappedClsName == null && cls.getMappedComment() == null) {
					boolean found = false;

					for (MethodInstance m : cls.getMethods()) {
						if (outputMethodMapping(m)) {
							found = true;
							break;
						}
					}

					if (!found) {
						for (FieldInstance f : cls.getFields()) {
							if (outputFieldMapping(f)) {
								found = true;
								break;
							}
						}
					}

					if (!found) continue; // no data for the class, skip
				}

				writer.acceptClass(clsName, mappedClsName);

				if (cls.getMappedComment() != null) writer.acceptClassComment(clsName, cls.getMappedComment());

				Stream.of(cls.getMethods())
				.filter(Matcher::outputMethodMapping)
				.sorted(Comparator.<MemberInstance<?>, String>comparing(MemberInstance::getName).thenComparing(MemberInstance::getDesc))
				.forEachOrdered(m -> {
					String name = m.getName();
					String desc = m.getDesc();
					writer.acceptMethod(clsName, name, desc, mappedClsName, m.getMappedName(false), getMappedDesc(m));

					String comment = m.getMappedComment();
					if (comment != null) writer.acceptMethodComment(clsName, name, desc, comment);

					for (MethodVarInstance arg : m.getArgs()) {
						String argName = arg.getMappedName(false);
						if (argName != null) writer.acceptMethodArg(clsName, name, desc, arg.getIndex(), arg.getLvtIndex(), argName);
					}
				});

				Stream.of(cls.getFields())
				.filter(Matcher::outputFieldMapping)
				.sorted(Comparator.<MemberInstance<?>, String>comparing(MemberInstance::getName).thenComparing(MemberInstance::getDesc))
				.forEachOrdered(m -> {
					String name = m.getName();
					String desc = m.getDesc();
					writer.acceptField(clsName, name, desc, mappedClsName, m.getMappedName(false), getMappedDesc(m));

					String comment = m.getMappedComment();
					if (comment != null) writer.acceptFieldComment(clsName, name, desc, comment);
				});
			}
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}

		return true;
	}

	private static boolean outputMethodMapping(MethodInstance method) {
		return method.hasMappedName() || method.getMappedComment() != null || method.hasMappedArg();
	}

	private static boolean outputFieldMapping(FieldInstance field) {
		return field.hasMappedName() || field.getMappedComment() != null;
	}

	private static String getMappedDesc(MethodInstance member) {
		String ret = "(";

		for (MethodVarInstance arg : member.getArgs()) {
			ret += getMappedDesc(arg.getType());
		}

		ret += ")" + getMappedDesc(member.getRetType());

		return ret;
	}

	private static String getMappedDesc(FieldInstance member) {
		return getMappedDesc(member.getType());
	}

	private static String getMappedDesc(ClassInstance cls) {
		String mappedName = cls.getMappedName(false);

		if (mappedName == null) {
			return cls.getId();
		} else if (cls.isArray()) {
			return cls.getId().substring(0, cls.getId().lastIndexOf('[') + 1) + getMappedDesc(cls.getElementClass());
		} else {
			return "L"+mappedName+";";
		}
	}

	public void match(ClassInstance a, ClassInstance b) {
		if (a == null) throw new NullPointerException("null class A");
		if (b == null) throw new NullPointerException("null class B");
		if (a.getArrayDimensions() != b.getArrayDimensions()) throw new IllegalArgumentException("the classes don't have the same amount of array dimensions");
		if (a.getMatch() == b) return;

		String mappedName = a.getMappedName(false);
		System.out.println("match class "+a+" -> "+b+(mappedName != null ? " ("+mappedName+")" : ""));

		if (a.getMatch() != null) {
			a.getMatch().setMatch(null);
			unmatchMembers(a);
		}

		if (b.getMatch() != null) {
			b.getMatch().setMatch(null);
			unmatchMembers(b);
		}

		a.setMatch(b);
		b.setMatch(a);

		// match array classes

		if (a.isArray()) {
			ClassInstance elemA = a.getElementClass();

			if (!elemA.hasMatch()) match(elemA, b.getElementClass());
		} else {
			for (ClassInstance arrayA : a.getArrays()) {
				int dims = arrayA.getArrayDimensions();

				for (ClassInstance arrayB : b.getArrays()) {
					if (arrayB.hasMatch() || arrayB.getArrayDimensions() != dims) continue;

					assert arrayA.getElementClass() == a && arrayB.getElementClass() == b;

					match(arrayA, arrayB);
					break;
				}
			}
		}

		// match methods that are matched via parents/children

		for (MethodInstance src : a.getMethods()) {
			MethodInstance matchedSrc = src.getMatchedHierarchyMember();
			if (matchedSrc == null) continue;

			Set<MethodInstance> dstHierarchyMembers = matchedSrc.getMatch().getAllHierarchyMembers();
			if (dstHierarchyMembers.size() <= 1) continue;

			for (MethodInstance dst : b.getMethods()) {
				if (dstHierarchyMembers.contains(dst)) {
					match(src, dst);
					break;
				}
			}
		}

		env.getCache().clear();
	}

	private static void unmatchMembers(ClassInstance cls) {
		for (MethodInstance m : cls.getMethods()) {
			if (m.getMatch() != null) {
				m.getMatch().setMatch(null);
				m.setMatch(null);

				for (MethodVarInstance arg : m.getArgs()) {
					if (arg.getMatch() != null) {
						arg.getMatch().setMatch(null);
						arg.setMatch(null);
					}
				}
			}
		}

		for (FieldInstance m : cls.getFields()) {
			if (m.getMatch() != null) {
				m.getMatch().setMatch(null);
				m.setMatch(null);
			}
		}
	}

	public void match(MethodInstance a, MethodInstance b) {
		if (a == null) throw new NullPointerException("null method A");
		if (b == null) throw new NullPointerException("null method B");
		if (a.getCls().getMatch() != b.getCls()) throw new IllegalArgumentException("the methods don't belong to the same class");
		if (a.getMatch() == b) return;

		String mappedName = a.getMappedName(false);
		System.out.println("match method "+a+" -> "+b+(mappedName != null ? " ("+mappedName+")" : ""));

		if (a.getMatch() != null) a.getMatch().setMatch(null);
		if (b.getMatch() != null) b.getMatch().setMatch(null);
		// TODO: unmatch vars

		a.setMatch(b);
		b.setMatch(a);

		// match parent/child methods

		Set<MethodInstance> srcHierarchyMembers = a.getAllHierarchyMembers();
		if (srcHierarchyMembers.size() <= 1) return;

		IClassEnv reqEnv = a.getCls().getEnv();
		Set<MethodInstance> dstHierarchyMembers = null;

		for (MethodInstance src : srcHierarchyMembers) {
			if (src.hasMatch() || !src.getCls().hasMatch() || src.getCls().getEnv() != reqEnv) continue;

			if (dstHierarchyMembers == null) dstHierarchyMembers = b.getAllHierarchyMembers();

			for (MethodInstance dst : src.getCls().getMatch().getMethods()) {
				if (dstHierarchyMembers.contains(dst)) {
					match(src, dst);
					break;
				}
			}
		}

		env.getCache().clear();
	}

	public void match(FieldInstance a, FieldInstance b) {
		if (a == null) throw new NullPointerException("null field A");
		if (b == null) throw new NullPointerException("null field B");
		if (a.getCls().getMatch() != b.getCls()) throw new IllegalArgumentException("the methods don't belong to the same class");
		if (a.getMatch() == b) return;

		String mappedName = a.getMappedName(false);
		System.out.println("match field "+a+" -> "+b+(mappedName != null ? " ("+mappedName+")" : ""));

		if (a.getMatch() != null) a.getMatch().setMatch(null);
		if (b.getMatch() != null) b.getMatch().setMatch(null);

		a.setMatch(b);
		b.setMatch(a);

		env.getCache().clear();
	}

	public void match(MethodVarInstance a, MethodVarInstance b) {
		if (a == null) throw new NullPointerException("null method var A");
		if (b == null) throw new NullPointerException("null method var B");
		if (a.getMethod().getMatch() != b.getMethod()) throw new IllegalArgumentException("the method vars don't belong to the same method");
		if (a.isArg() != b.isArg()) throw new IllegalArgumentException("the method vars are not of the same kind");
		if (a.getMatch() == b) return;

		String mappedName = a.getMappedName(false);
		System.out.println("match method arg "+a+" -> "+b+(mappedName != null ? " ("+mappedName+")" : ""));

		if (a.getMatch() != null) a.getMatch().setMatch(null);
		if (b.getMatch() != null) b.getMatch().setMatch(null);

		a.setMatch(b);
		b.setMatch(a);

		env.getCache().clear();
	}

	public void unmatch(ClassInstance cls) {
		if (cls == null) throw new NullPointerException("null class");
		if (cls.getMatch() == null) return;

		String mappedName = cls.getMappedName(false);
		System.out.println("unmatch class "+cls+" (was "+cls.getMatch()+")"+(mappedName != null ? " ("+mappedName+")" : ""));

		cls.getMatch().setMatch(null);
		cls.setMatch(null);

		unmatchMembers(cls);

		if (cls.isArray()) {
			unmatch(cls.getElementClass());
		} else {
			for (ClassInstance array : cls.getArrays()) {
				unmatch(array);
			}
		}

		env.getCache().clear();
	}

	public void unmatch(MemberInstance<?> m) {
		if (m == null) throw new NullPointerException("null member");
		if (m.getMatch() == null) return;

		String mappedName = m.getMappedName(false);
		System.out.println("unmatch member "+m+" (was "+m.getMatch()+")"+(mappedName != null ? " ("+mappedName+")" : ""));

		if (m instanceof MethodInstance) {
			for (MethodVarInstance arg : ((MethodInstance) m).getArgs()) {
				unmatch(arg);
			}
		}

		m.getMatch().setMatch(null);
		m.setMatch(null);

		if (m instanceof MethodInstance) {
			for (MemberInstance<?> member : m.getAllHierarchyMembers()) {
				unmatch(member);
			}
		}

		env.getCache().clear();
	}

	public void unmatch(MethodVarInstance a) {
		if (a == null) throw new NullPointerException("null method var");
		if (a.getMatch() == null) return;

		String mappedName = a.getMappedName(false);
		System.out.println("unmatch method var "+a+" (was "+a.getMatch()+")"+(mappedName != null ? " ("+mappedName+")" : ""));

		a.getMatch().setMatch(null);
		a.setMatch(null);

		env.getCache().clear();
	}

	public void autoMatchAll(DoubleConsumer progressReceiver) {
		if (autoMatchClasses(ClassifierLevel.Initial, absClassAutoMatchThreshold, relClassAutoMatchThreshold, progressReceiver)) {
			autoMatchClasses(ClassifierLevel.Initial, absClassAutoMatchThreshold, relClassAutoMatchThreshold, progressReceiver);
		}

		autoMatchLevel(ClassifierLevel.Intermediate, progressReceiver);
		autoMatchLevel(ClassifierLevel.Full, progressReceiver);
		autoMatchLevel(ClassifierLevel.Extra, progressReceiver);

		boolean matchedAny;

		do {
			matchedAny = autoMatchMethodArgs(ClassifierLevel.Full, absMethodArgAutoMatchThreshold, relMethodArgAutoMatchThreshold, progressReceiver);
		} while (matchedAny);

		env.getCache().clear();
	}

	private void autoMatchLevel(ClassifierLevel level, DoubleConsumer progressReceiver) {
		boolean matchedAny;
		boolean matchedClassesBefore = true;

		do {
			matchedAny = autoMatchMethods(level, absMethodAutoMatchThreshold, relMethodAutoMatchThreshold, progressReceiver);
			matchedAny |= autoMatchFields(level, absFieldAutoMatchThreshold, relFieldAutoMatchThreshold, progressReceiver);

			if (!matchedAny && !matchedClassesBefore) {
				break;
			}

			matchedAny |= matchedClassesBefore = autoMatchClasses(level, absClassAutoMatchThreshold, relClassAutoMatchThreshold, progressReceiver);
		} while (matchedAny);
	}

	public boolean autoMatchClasses(DoubleConsumer progressReceiver) {
		return autoMatchClasses(autoMatchLevel, absClassAutoMatchThreshold, relClassAutoMatchThreshold, progressReceiver);
	}

	public boolean autoMatchClasses(ClassifierLevel level, double absThreshold, double relThreshold, DoubleConsumer progressReceiver) {
		Predicate<ClassInstance> filter = cls -> cls.getUri() != null && cls.isNameObfuscated(false) && cls.getMatch() == null;

		List<ClassInstance> classes = env.getClassesA().stream()
				.filter(filter)
				.collect(Collectors.toList());

		ClassInstance[] cmpClasses = env.getClassesB().stream()
				.filter(filter)
				.collect(Collectors.toList()).toArray(new ClassInstance[0]);

		double maxScore = ClassClassifier.getMaxScore(level);
		double maxMismatch = maxScore - getRawScore(absThreshold * (1 - relThreshold), maxScore);
		Map<ClassInstance, ClassInstance> matches = new ConcurrentHashMap<>(classes.size());

		runInParallel(classes, cls -> {
			List<RankResult<ClassInstance>> ranking = ClassClassifier.rank(cls, cmpClasses, level, env, maxMismatch);

			if (checkRank(ranking, absThreshold, relThreshold, maxScore)) {
				ClassInstance match = ranking.get(0).getSubject();

				matches.put(cls, match);
			}
		}, progressReceiver);

		sanitizeMatches(matches);

		for (Map.Entry<ClassInstance, ClassInstance> entry : matches.entrySet()) {
			match(entry.getKey(), entry.getValue());
		}

		System.out.println("Auto matched "+matches.size()+" classes ("+(classes.size() - matches.size())+" unmatched, "+env.getClassesA().size()+" total)");

		return !matches.isEmpty();
	}

	private static <T, C> void runInParallel(List<T> workSet, Consumer<T> worker, DoubleConsumer progressReceiver) {
		if (workSet.isEmpty()) return;

		AtomicInteger itemsDone = new AtomicInteger();
		int updateRate = Math.max(1, workSet.size() / 200);

		try {
			List<Future<Void>> futures = threadPool.invokeAll(workSet.stream().<Callable<Void>>map(workItem -> () -> {
				worker.accept(workItem);

				int cItemsDone = itemsDone.incrementAndGet();

				if ((cItemsDone % updateRate) == 0) {
					progressReceiver.accept((double) cItemsDone / workSet.size());
				}

				return null;
			}).collect(Collectors.toList()));

			for (Future<Void> future : futures) {
				future.get();
			}
		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public boolean autoMatchMethods(DoubleConsumer progressReceiver) {
		return autoMatchMethods(autoMatchLevel, absMethodAutoMatchThreshold, relMethodAutoMatchThreshold, progressReceiver);
	}

	public boolean autoMatchMethods(ClassifierLevel level, double absThreshold, double relThreshold, DoubleConsumer progressReceiver) {
		AtomicInteger totalUnmatched = new AtomicInteger();
		Map<MethodInstance, MethodInstance> matches = match(level, absThreshold, relThreshold,
				cls -> cls.getMethods(), MethodClassifier::rank, MethodClassifier.getMaxScore(level),
				progressReceiver, totalUnmatched);

		for (Map.Entry<MethodInstance, MethodInstance> entry : matches.entrySet()) {
			match(entry.getKey(), entry.getValue());
		}

		System.out.println("Auto matched "+matches.size()+" methods ("+totalUnmatched.get()+" unmatched)");

		return !matches.isEmpty();
	}

	public boolean autoMatchFields(DoubleConsumer progressReceiver) {
		return autoMatchFields(autoMatchLevel, absFieldAutoMatchThreshold, relFieldAutoMatchThreshold, progressReceiver);
	}

	public boolean autoMatchFields(ClassifierLevel level, double absThreshold, double relThreshold, DoubleConsumer progressReceiver) {
		AtomicInteger totalUnmatched = new AtomicInteger();
		double maxScore = FieldClassifier.getMaxScore(level);

		Map<FieldInstance, FieldInstance> matches = match(level, absThreshold, relThreshold,
				cls -> cls.getFields(), FieldClassifier::rank, maxScore,
				progressReceiver, totalUnmatched);

		for (Map.Entry<FieldInstance, FieldInstance> entry : matches.entrySet()) {
			match(entry.getKey(), entry.getValue());
		}

		System.out.println("Auto matched "+matches.size()+" fields ("+totalUnmatched.get()+" unmatched)");

		return !matches.isEmpty();
	}

	private <T extends MemberInstance<T>> Map<T, T> match(ClassifierLevel level, double absThreshold, double relThreshold,
			Function<ClassInstance, T[]> memberGetter, IRanker<T> ranker, double maxScore,
			DoubleConsumer progressReceiver, AtomicInteger totalUnmatched) {
		List<ClassInstance> classes = env.getClassesA().stream()
				.filter(cls -> cls.getUri() != null && cls.getMatch() != null && memberGetter.apply(cls).length > 0)
				.filter(cls -> {
					for (T member : memberGetter.apply(cls)) {
						if (member.getMatch() == null) return true;
					}

					return false;
				})
				.collect(Collectors.toList());
		if (classes.isEmpty()) return Collections.emptyMap();

		double maxMismatch = maxScore - getRawScore(absThreshold * (1 - relThreshold), maxScore);
		Map<T, T> ret = new ConcurrentHashMap<>(512);

		runInParallel(classes, cls -> {
			int unmatched = 0;

			for (T member : memberGetter.apply(cls)) {
				if (member.getMatch() != null) continue;

				List<RankResult<T>> ranking = ranker.rank(member, memberGetter.apply(cls.getMatch()), level, env, maxMismatch);

				if (checkRank(ranking, absThreshold, relThreshold, maxScore)) {
					T match = ranking.get(0).getSubject();

					ret.put(member, match);
				} else {
					unmatched++;
				}
			}

			if (unmatched > 0) totalUnmatched.addAndGet(unmatched);
		}, progressReceiver);

		sanitizeMatches(ret);

		return ret;
	}

	public boolean autoMatchMethodArgs(DoubleConsumer progressReceiver) {
		return autoMatchMethodArgs(autoMatchLevel, absMethodArgAutoMatchThreshold, relMethodArgAutoMatchThreshold, progressReceiver);
	}

	public boolean autoMatchMethodArgs(ClassifierLevel level, double absThreshold, double relThreshold, DoubleConsumer progressReceiver) {
		List<MethodInstance> methods = env.getClassesA().stream()
				.filter(cls -> cls.getUri() != null && cls.getMatch() != null && cls.getMethods().length > 0)
				.flatMap(cls -> Stream.<MethodInstance>of(cls.getMethods()))
				.filter(m -> m.getMatch() != null && m.getArgs().length > 0)
				.filter(m -> {
					for (MethodVarInstance a : m.getArgs()) {
						if (a.getMatch() == null) return true;
					}

					return false;
				})
				.collect(Collectors.toList());
		Map<MethodVarInstance, MethodVarInstance> matches;
		AtomicInteger totalUnmatched = new AtomicInteger();

		if (methods.isEmpty()) {
			matches = Collections.emptyMap();
		} else {
			double maxScore = MethodArgClassifier.getMaxScore(level);
			double maxMismatch = maxScore - getRawScore(absThreshold * (1 - relThreshold), maxScore);
			matches = new ConcurrentHashMap<>(512);

			runInParallel(methods, m -> {
				int unmatched = 0;

				for (MethodVarInstance arg : m.getArgs()) {
					if (arg.getMatch() != null) continue;

					List<RankResult<MethodVarInstance>> ranking = MethodArgClassifier.rank(arg, m.getMatch().getArgs(), level, env, maxMismatch);

					if (checkRank(ranking, absThreshold, relThreshold, maxScore)) {
						MethodVarInstance match = ranking.get(0).getSubject();

						matches.put(arg, match);
					} else {
						unmatched++;
					}
				}

				if (unmatched > 0) totalUnmatched.addAndGet(unmatched);
			}, progressReceiver);

			sanitizeMatches(matches);
		}

		for (Map.Entry<MethodVarInstance, MethodVarInstance> entry : matches.entrySet()) {
			match(entry.getKey(), entry.getValue());
		}

		System.out.println("Auto matched "+matches.size()+" method args ("+totalUnmatched.get()+" unmatched)");

		return !matches.isEmpty();
	}

	public static boolean checkRank(List<? extends RankResult<?>> ranking, double absThreshold, double relThreshold, double maxScore) {
		if (ranking.isEmpty()) return false;

		double score = getScore(ranking.get(0).getScore(), maxScore);
		if (score < absThreshold) return false;

		if (ranking.size() == 1) {
			return true;
		} else {
			double nextScore = getScore(ranking.get(1).getScore(), maxScore);

			return nextScore < score * (1 - relThreshold);
		}
	}

	public static double getScore(double rawScore, double maxScore) {
		double ret = rawScore / maxScore;

		return ret * ret;
	}

	private static double getRawScore(double score, double maxScore) {
		return Math.sqrt(score) * maxScore;
	}

	private static <T> void sanitizeMatches(Map<T, T> matches) {
		Set<T> matched = Collections.newSetFromMap(new IdentityHashMap<>(matches.size()));
		Set<T> conflictingMatches = Collections.newSetFromMap(new IdentityHashMap<>());

		for (T cls : matches.values()) {
			if (!matched.add(cls)) {
				conflictingMatches.add(cls);
			}
		}

		if (!conflictingMatches.isEmpty()) {
			matches.values().removeAll(conflictingMatches);
		}
	}

	public MatchingStatus getStatus(boolean inputsOnly) {
		int totalClassCount = 0;
		int matchedClassCount = 0;
		int totalMethodCount = 0;
		int matchedMethodCount = 0;
		int totalMethodArgCount = 0;
		int matchedMethodArgCount = 0;
		int totalFieldCount = 0;
		int matchedFieldCount = 0;

		for (ClassInstance cls : env.getClassesA()) {
			if (inputsOnly && !cls.isInput()) continue;

			totalClassCount++;
			if (cls.getMatch() != null) matchedClassCount++;

			for (MethodInstance method : cls.getMethods()) {
				if (method.isReal()) {
					totalMethodCount++;

					if (method.getMatch() != null) matchedMethodCount++;

					for (MethodVarInstance arg : method.getArgs()) {
						totalMethodArgCount++;

						if (arg.getMatch() != null) matchedMethodArgCount++;
					}
				}
			}

			for (FieldInstance field : cls.getFields()) {
				if (field.isReal()) {
					totalFieldCount++;

					if (field.getMatch() != null) matchedFieldCount++;
				}
			}
		}

		return new MatchingStatus(totalClassCount, matchedClassCount, totalMethodCount, matchedMethodCount, totalMethodArgCount, matchedMethodArgCount, totalFieldCount, matchedFieldCount);
	}

	public boolean propagateNames(DoubleConsumer progressReceiver) {
		int total = env.getClassesB().size();
		int current = 0;
		Set<MethodInstance> checked = Util.newIdentityHashSet();
		int propagatedMethodNames = 0;
		int propagatedArgNames = 0;

		for (ClassInstance cls : env.getClassesB()) {
			if (cls.getMethods().length > 0) {
				for (MethodInstance method : cls.getMethods()) {
					if (method.getAllHierarchyMembers().size() <= 1) continue;
					if (checked.contains(method)) continue;

					String name = method.getMappedName(false);
					if (name != null && method.hasAllArgsMapped()) continue;

					checked.addAll(method.getAllHierarchyMembers());

					// collect names from all hierarchy members

					final int argCount = method.getArgs().length;
					String[] argNames = new String[argCount];
					int missingArgNames = argCount;

					collectLoop: for (MethodInstance m : method.getAllHierarchyMembers()) {
						if (name == null && (name = m.getMappedName(false)) != null) {
							if (missingArgNames == 0) break;
						}

						if (missingArgNames > 0) {
							assert m.getArgs().length == argCount;

							for (int i = 0; i < argCount; i++) {
								if (argNames[i] == null && (argNames[i] = m.getArg(i).getMappedName(false)) != null) {
									missingArgNames--;

									if (name != null && missingArgNames == 0) break collectLoop;
								}
							}
						}
					}

					if (name == null && missingArgNames == argCount) continue; // nothing found

					// apply names to all hierarchy members that don't have any yet

					for (MethodInstance m : method.getAllHierarchyMembers()) {
						if (name != null && !m.hasMappedName()) {
							m.setMappedName(name);
							propagatedMethodNames++;
						}

						for (int i = 0; i < argCount; i++) {
							MethodVarInstance arg;

							if (argNames[i] != null && (arg = m.getArg(i)).getMappedName(false) == null) {
								arg.setMappedName(argNames[i]);
								propagatedArgNames++;
							}
						}
					}
				}
			}

			if (((++current & (1 << 4) - 1)) == 0) {
				progressReceiver.accept((double) current / total);
			}
		}

		System.out.printf("Propagated %d method names, %d method arg names.", propagatedMethodNames, propagatedArgNames);

		return propagatedMethodNames > 0 || propagatedArgNames > 0;
	}

	public static class MatchingStatus {
		MatchingStatus(int totalClassCount, int matchedClassCount,
				int totalMethodCount, int matchedMethodCount,
				int totalMethodArgCount, int matchedMethodArgCount,
				int totalFieldCount, int matchedFieldCount) {
			this.totalClassCount = totalClassCount;
			this.matchedClassCount = matchedClassCount;
			this.totalMethodCount = totalMethodCount;
			this.matchedMethodCount = matchedMethodCount;
			this.totalMethodArgCount = totalMethodArgCount;
			this.matchedMethodArgCount = matchedMethodArgCount;
			this.totalFieldCount = totalFieldCount;
			this.matchedFieldCount = matchedFieldCount;
		}

		public final int totalClassCount;
		public final int matchedClassCount;
		public final int totalMethodCount;
		public final int matchedMethodCount;
		public final int totalMethodArgCount;
		public final int matchedMethodArgCount;
		public final int totalFieldCount;
		public final int matchedFieldCount;
	}

	private static final ExecutorService threadPool = Executors.newWorkStealingPool();

	private final ClassEnvironment env;
	private final ClassifierLevel autoMatchLevel = ClassifierLevel.Full;
	private final double absClassAutoMatchThreshold = 0.85;
	private final double relClassAutoMatchThreshold = 0.085;
	private final double absMethodAutoMatchThreshold = 0.85;
	private final double relMethodAutoMatchThreshold = 0.085;
	private final double absFieldAutoMatchThreshold = 0.85;
	private final double relFieldAutoMatchThreshold = 0.085;
	private final double absMethodArgAutoMatchThreshold = 0.85;
	private final double relMethodArgAutoMatchThreshold = 0.085;
}
