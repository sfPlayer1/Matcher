package matcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import matcher.model.NameType;
import matcher.model.classifier.ClassClassifier;
import matcher.model.classifier.ClassifierLevel;
import matcher.model.classifier.FieldClassifier;
import matcher.model.classifier.IRanker;
import matcher.model.classifier.MethodClassifier;
import matcher.model.classifier.MethodVarClassifier;
import matcher.model.classifier.RankResult;
import matcher.model.config.Config;
import matcher.model.config.ProjectConfig;
import matcher.model.type.ClassEnv;
import matcher.model.type.ClassEnvironment;
import matcher.model.type.ClassInstance;
import matcher.model.type.FieldInstance;
import matcher.model.type.InputFile;
import matcher.model.type.MemberInstance;
import matcher.model.type.MethodInstance;
import matcher.model.type.MethodVarInstance;

public class Matcher {
	public static void init() {
		ClassClassifier.init();
		MethodClassifier.init();
		FieldClassifier.init();
		MethodVarClassifier.init();
	}

	public Matcher(ClassEnvironment env) {
		this.env = env;
	}

	public void init(ProjectConfig config, DoubleConsumer progressReceiver) {
		try {
			env.init(config, progressReceiver);

			matchUnobfuscated();
		} catch (Throwable t) {
			reset();
			throw t;
		}
	}

	private void matchUnobfuscated() {
		for (ClassInstance cls : env.getClassesA()) {
			if (cls.isNameObfuscated() || !cls.isReal()) continue;

			ClassInstance match = env.getLocalClsByIdB(cls.getId());

			if (match != null && !match.isNameObfuscated()) {
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
			List<InputFile> cpFilesA, List<InputFile> cpFilesB,
			String nonObfuscatedClassPatternA, String nonObfuscatedClassPatternB, String nonObfuscatedMemberPatternA, String nonObfuscatedMemberPatternB,
			DoubleConsumer progressReceiver) throws IOException {
		List<Path> pathsA = resolvePaths(inputDirs, inputFilesA);
		List<Path> pathsB = resolvePaths(inputDirs, inputFilesB);
		List<Path> sharedClassPath = resolvePaths(inputDirs, cpFiles);
		List<Path> classPathA = resolvePaths(inputDirs, cpFilesA);
		List<Path> classPathB = resolvePaths(inputDirs, cpFilesB);

		ProjectConfig config = new ProjectConfig.Builder(pathsA, pathsB)
				.classPathA(new ArrayList<>(classPathA))
				.classPathB(new ArrayList<>(classPathB))
				.sharedClassPath(new ArrayList<>(sharedClassPath))
				.nonObfuscatedClassPatternA(nonObfuscatedClassPatternA)
				.nonObfuscatedClassPatternB(nonObfuscatedClassPatternB)
				.nonObfuscatedMemberPatternA(nonObfuscatedMemberPatternA)
				.nonObfuscatedMemberPatternB(nonObfuscatedMemberPatternB)
				.build();
		if (!config.isValid()) throw new IOException("invalid config");
		Config.setProjectConfig(config);
		Config.saveAsLast();

		reset();
		init(config, progressReceiver);
	}

	public static Path resolvePath(Collection<Path> inputDirs, InputFile inputFile) throws IOException {
		List<Path> ret = resolvePaths(inputDirs, Collections.singletonList(inputFile));

		return ret.get(0);
	}

	public static List<Path> resolvePaths(Collection<Path> inputDirs, Collection<InputFile> inputFiles) throws IOException {
		List<Path> ret = new ArrayList<>(inputFiles.size());

		inputFileLoop: for (InputFile inputFile : inputFiles) {
			if (inputFile.pathHint != null) {
				if (inputFile.pathHint.isAbsolute()) {
					if (Files.isRegularFile(inputFile.pathHint) && inputFile.equals(inputFile.pathHint)) {
						ret.add(inputFile.pathHint);
						continue inputFileLoop;
					}
				} else {
					for (Path inputDir : inputDirs) {
						Path file = inputDir.resolve(inputFile.pathHint);

						if (Files.isRegularFile(file) && inputFile.equals(file)) {
							ret.add(file);
							continue inputFileLoop;
						}
					}
				}
			}

			for (Path inputDir : inputDirs) {
				try (Stream<Path> matches = Files.find(inputDir, Integer.MAX_VALUE, (path, attr) -> inputFile.equals(path), FileVisitOption.FOLLOW_LINKS)) {
					Path file = matches.findFirst().orElse(null);

					if (file != null) {
						ret.add(file);
						continue inputFileLoop;
					}
				} catch (UncheckedIOException e) {
					throw e.getCause();
				}
			}

			throw new IOException("can't find input "+inputFile);
		}

		return ret;
	}

	public void match(ClassInstance a, ClassInstance b) {
		if (a == null) throw new NullPointerException("null class A");
		if (b == null) throw new NullPointerException("null class B");
		if (a.getArrayDimensions() != b.getArrayDimensions()) throw new IllegalArgumentException("the classes don't have the same amount of array dimensions");
		if (a.getMatch() == b) return;

		LOGGER.debug("Matching class {} -> {}{}", a, b, (a.hasMappedName() ? " ("+a.getName(NameType.MAPPED_PLAIN)+")" : ""));

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

		// match methods that are not obfuscated or matched via parents/children

		for (MethodInstance src : a.getMethods()) {
			if (!src.isNameObfuscated()) {
				MethodInstance dst = b.getMethod(src.getId());

				if ((dst != null || (dst = b.getMethod(src.getName(), null)) != null) && !dst.isNameObfuscated()) { // full match or name match with no alternatives
					match(src, dst);
					continue;
				}
			}

			MethodInstance matchedDst = src.getHierarchyMatch();
			if (matchedDst == null) continue;

			Set<MethodInstance> dstHierarchyMembers = matchedDst.getAllHierarchyMembers();
			if (dstHierarchyMembers.size() <= 1) continue;

			for (MethodInstance dst : b.getMethods()) {
				if (dstHierarchyMembers.contains(dst)) {
					src.setMatchable(true);
					dst.setMatchable(true);
					match(src, dst);
					break;
				}
			}
		}

		// match fields that are not obfuscated

		for (FieldInstance src : a.getFields()) {
			if (!src.isNameObfuscated()) {
				FieldInstance dst = b.getField(src.getId());

				if ((dst != null || (dst = b.getField(src.getName(), null)) != null) && !dst.isNameObfuscated()) { // full match or name match with no alternatives
					match(src, dst);
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

				unmatchArgsVars(m);
			}
		}

		for (FieldInstance m : cls.getFields()) {
			if (m.getMatch() != null) {
				m.getMatch().setMatch(null);
				m.setMatch(null);
			}
		}
	}

	private static void unmatchArgsVars(MethodInstance m) {
		for (MethodVarInstance arg : m.getArgs()) {
			if (arg.getMatch() != null) {
				arg.getMatch().setMatch(null);
				arg.setMatch(null);
			}
		}

		for (MethodVarInstance var : m.getVars()) {
			if (var.getMatch() != null) {
				var.getMatch().setMatch(null);
				var.setMatch(null);
			}
		}
	}

	public void match(MemberInstance<?> a, MemberInstance<?> b) {
		if (a instanceof MethodInstance) {
			match((MethodInstance) a, (MethodInstance) b);
		} else {
			match((FieldInstance) a, (FieldInstance) b);
		}
	}

	public void match(MethodInstance a, MethodInstance b) {
		if (a == null) throw new NullPointerException("null method A");
		if (b == null) throw new NullPointerException("null method B");
		if (a.getCls().getMatch() != b.getCls()) throw new IllegalArgumentException("the methods don't belong to the same class");
		if (a.getMatch() == b) return;

		LOGGER.debug("Matching method {} -> {}{}", a, b, (a.hasMappedName() ? " ("+a.getName(NameType.MAPPED_PLAIN)+")" : ""));

		Set<MethodInstance> membersA = a.getAllHierarchyMembers();
		Set<MethodInstance> membersB = b.getAllHierarchyMembers();
		assert membersA.contains(a);
		assert membersB.contains(b);

		if (!a.hasMatchedHierarchy(b)) {
			if (a.hasHierarchyMatch()) {
				for (MethodInstance m : membersA) {
					if (m.hasMatch()) {
						unmatchArgsVars(m);
						m.getMatch().setMatch(null);
						m.setMatch(null);
					}
				}
			}

			if (b.hasHierarchyMatch()) {
				for (MethodInstance m : membersB) {
					if (m.hasMatch()) {
						unmatchArgsVars(m);
						m.getMatch().setMatch(null);
						m.setMatch(null);
					}
				}
			}

			ClassEnv reqEnv = a.getCls().getEnv();

			for (MethodInstance ca : membersA) {
				ClassInstance cls = ca.getCls();
				if (!cls.hasMatch() || cls.getEnv() != reqEnv) continue;

				for (MethodInstance cb : cls.getMatch().getMethods()) {
					if (membersB.contains(cb)) {
						assert !ca.hasMatch() && !cb.hasMatch();
						ca.setMatch(cb);
						cb.setMatch(ca);
						break;
					}
				}
			}
		} else {
			if (a.getMatch() != null) {
				unmatchArgsVars(a);
				a.getMatch().setMatch(null);
				a.setMatch(null);
			}

			if (b.getMatch() != null) {
				unmatchArgsVars(b);
				b.getMatch().setMatch(null);
				b.setMatch(null);
			}

			a.setMatch(b);
			b.setMatch(a);
		}

		env.getCache().clear();
	}

	public void match(FieldInstance a, FieldInstance b) {
		if (a == null) throw new NullPointerException("null field A");
		if (b == null) throw new NullPointerException("null field B");
		if (a.getCls().getMatch() != b.getCls()) throw new IllegalArgumentException("the methods don't belong to the same class");
		if (a.getMatch() == b) return;

		LOGGER.debug("Matching field {} -> {}{}", a, b, (a.hasMappedName() ? " ("+a.getName(NameType.MAPPED_PLAIN)+")" : ""));

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

		LOGGER.debug("Matching method arg {} -> {}{}", a, b, (a.hasMappedName() ? " ("+a.getName(NameType.MAPPED_PLAIN)+")" : ""));

		if (a.getMatch() != null) a.getMatch().setMatch(null);
		if (b.getMatch() != null) b.getMatch().setMatch(null);

		a.setMatch(b);
		b.setMatch(a);

		env.getCache().clear();
	}

	public void unmatch(ClassInstance cls) {
		if (cls == null) throw new NullPointerException("null class");
		if (cls.getMatch() == null) return;

		LOGGER.debug("Unmatching class {} (was {}){}", cls, cls.getMatch(), (cls.hasMappedName() ? " ("+cls.getName(NameType.MAPPED_PLAIN)+")" : ""));

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

		LOGGER.debug("Unmatching member {} (was {}){}", m, m.getMatch(), (m.hasMappedName() ? " ("+m.getName(NameType.MAPPED_PLAIN)+")" : ""));

		if (m instanceof MethodInstance) {
			for (MethodVarInstance arg : ((MethodInstance) m).getArgs()) {
				unmatch(arg);
			}

			for (MethodVarInstance var : ((MethodInstance) m).getVars()) {
				unmatch(var);
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

		LOGGER.debug("Unmatching method var {} (was {}){}", a, a.getMatch(), (a.hasMappedName() ? " ("+a.getName(NameType.MAPPED_PLAIN)+")" : ""));

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
			matchedAny |= autoMatchMethodVars(ClassifierLevel.Full, absMethodVarAutoMatchThreshold, relMethodVarAutoMatchThreshold, progressReceiver);
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
		boolean assumeBothOrNoneObfuscated = env.assumeBothOrNoneObfuscated;
		Predicate<ClassInstance> filter = cls -> cls.isReal() && (!assumeBothOrNoneObfuscated || cls.isNameObfuscated()) && !cls.hasMatch() && cls.isMatchable();

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

		LOGGER.info("Auto matched {} classes ({} unmatched, {} total)", matches.size(), (classes.size() - matches.size()), env.getClassesA().size());

		return !matches.isEmpty();
	}

	public static <T, C> void runInParallel(List<T> workSet, Consumer<T> worker, DoubleConsumer progressReceiver) {
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

		LOGGER.info("Auto matched {} methods ({} unmatched)", matches.size(), totalUnmatched.get());

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

		LOGGER.info("Auto matched {} fields ({} unmatched)", matches.size(), totalUnmatched.get());

		return !matches.isEmpty();
	}

	private <T extends MemberInstance<T>> Map<T, T> match(ClassifierLevel level, double absThreshold, double relThreshold,
			Function<ClassInstance, T[]> memberGetter, IRanker<T> ranker, double maxScore,
			DoubleConsumer progressReceiver, AtomicInteger totalUnmatched) {
		List<ClassInstance> classes = env.getClassesA().stream()
				.filter(cls -> cls.isReal() && cls.hasMatch() && memberGetter.apply(cls).length > 0)
				.filter(cls -> {
					for (T member : memberGetter.apply(cls)) {
						if (!member.hasMatch() && member.isMatchable()) return true;
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
				if (member.hasMatch() || !member.isMatchable()) continue;

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
		return autoMatchMethodVars(true, MethodInstance::getArgs, level, absThreshold, relThreshold, progressReceiver);
	}

	public boolean autoMatchMethodVars(DoubleConsumer progressReceiver) {
		return autoMatchMethodVars(autoMatchLevel, absMethodVarAutoMatchThreshold, relMethodVarAutoMatchThreshold, progressReceiver);
	}

	public boolean autoMatchMethodVars(ClassifierLevel level, double absThreshold, double relThreshold, DoubleConsumer progressReceiver) {
		return autoMatchMethodVars(false, MethodInstance::getVars, level, absThreshold, relThreshold, progressReceiver);
	}

	private boolean autoMatchMethodVars(boolean isArg, Function<MethodInstance, MethodVarInstance[]> supplier,
			ClassifierLevel level, double absThreshold, double relThreshold, DoubleConsumer progressReceiver) {
		List<MethodInstance> methods = env.getClassesA().stream()
				.filter(cls -> cls.isReal() && cls.hasMatch() && cls.getMethods().length > 0)
				.flatMap(cls -> Stream.<MethodInstance>of(cls.getMethods()))
				.filter(m -> m.hasMatch() && supplier.apply(m).length > 0)
				.filter(m -> {
					for (MethodVarInstance a : supplier.apply(m)) {
						if (!a.hasMatch() && a.isMatchable()) return true;
					}

					return false;
				})
				.collect(Collectors.toList());
		Map<MethodVarInstance, MethodVarInstance> matches;
		AtomicInteger totalUnmatched = new AtomicInteger();

		if (methods.isEmpty()) {
			matches = Collections.emptyMap();
		} else {
			double maxScore = MethodVarClassifier.getMaxScore(level);
			double maxMismatch = maxScore - getRawScore(absThreshold * (1 - relThreshold), maxScore);
			matches = new ConcurrentHashMap<>(512);

			runInParallel(methods, m -> {
				int unmatched = 0;

				for (MethodVarInstance var : supplier.apply(m)) {
					if (var.hasMatch() || !var.isMatchable()) continue;

					List<RankResult<MethodVarInstance>> ranking = MethodVarClassifier.rank(var, supplier.apply(m.getMatch()), level, env, maxMismatch);

					if (checkRank(ranking, absThreshold, relThreshold, maxScore)) {
						MethodVarInstance match = ranking.get(0).getSubject();

						matches.put(var, match);
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

		LOGGER.info("Auto matched {} method {}s ({} unmatched)", matches.size(), (isArg ? "arg" : "var"), totalUnmatched.get());

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

	public static <T> void sanitizeMatches(Map<T, T> matches) {
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
		int totalMethodVarCount = 0;
		int matchedMethodVarCount = 0;
		int totalFieldCount = 0;
		int matchedFieldCount = 0;

		for (ClassInstance cls : env.getClassesA()) {
			if (inputsOnly && !cls.isInput()) continue;

			totalClassCount++;
			if (cls.hasMatch()) matchedClassCount++;

			for (MethodInstance method : cls.getMethods()) {
				if (method.isReal()) {
					totalMethodCount++;

					if (method.hasMatch()) matchedMethodCount++;

					for (MethodVarInstance arg : method.getArgs()) {
						totalMethodArgCount++;

						if (arg.hasMatch()) matchedMethodArgCount++;
					}

					for (MethodVarInstance var : method.getVars()) {
						totalMethodVarCount++;

						if (var.hasMatch()) matchedMethodVarCount++;
					}
				}
			}

			for (FieldInstance field : cls.getFields()) {
				if (field.isReal()) {
					totalFieldCount++;

					if (field.hasMatch()) matchedFieldCount++;
				}
			}
		}

		return new MatchingStatus(totalClassCount, matchedClassCount,
				totalMethodCount, matchedMethodCount,
				totalMethodArgCount, matchedMethodArgCount,
				totalMethodVarCount, matchedMethodVarCount,
				totalFieldCount, matchedFieldCount);
	}

	public static class MatchingStatus {
		MatchingStatus(int totalClassCount, int matchedClassCount,
				int totalMethodCount, int matchedMethodCount,
				int totalMethodArgCount, int matchedMethodArgCount,
				int totalMethodVarCount, int matchedMethodVarCount,
				int totalFieldCount, int matchedFieldCount) {
			this.totalClassCount = totalClassCount;
			this.matchedClassCount = matchedClassCount;
			this.totalMethodCount = totalMethodCount;
			this.matchedMethodCount = matchedMethodCount;
			this.totalMethodArgCount = totalMethodArgCount;
			this.matchedMethodArgCount = matchedMethodArgCount;
			this.totalMethodVarCount = totalMethodVarCount;
			this.matchedMethodVarCount = matchedMethodVarCount;
			this.totalFieldCount = totalFieldCount;
			this.matchedFieldCount = matchedFieldCount;
		}

		public final int totalClassCount;
		public final int matchedClassCount;
		public final int totalMethodCount;
		public final int matchedMethodCount;
		public final int totalMethodArgCount;
		public final int matchedMethodArgCount;
		public final int totalMethodVarCount;
		public final int matchedMethodVarCount;
		public final int totalFieldCount;
		public final int matchedFieldCount;
	}

	public static final ExecutorService threadPool = Executors.newWorkStealingPool();
	public static final Logger LOGGER = LoggerFactory.getLogger("Matcher");

	private final ClassEnvironment env;
	private final ClassifierLevel autoMatchLevel = ClassifierLevel.Extra;
	private final double absClassAutoMatchThreshold = 0.85;
	private final double relClassAutoMatchThreshold = 0.085;
	private final double absMethodAutoMatchThreshold = 0.85;
	private final double relMethodAutoMatchThreshold = 0.085;
	private final double absFieldAutoMatchThreshold = 0.85;
	private final double relFieldAutoMatchThreshold = 0.085;
	private final double absMethodArgAutoMatchThreshold = 0.85;
	private final double relMethodArgAutoMatchThreshold = 0.085;
	private final double absMethodVarAutoMatchThreshold = 0.85;
	private final double relMethodVarAutoMatchThreshold = 0.085;
}
