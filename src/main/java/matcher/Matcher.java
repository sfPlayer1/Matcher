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
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import job4j.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import matcher.classifier.ClassClassifier;
import matcher.classifier.ClassifierLevel;
import matcher.classifier.FieldClassifier;
import matcher.classifier.IRanker;
import matcher.classifier.MethodClassifier;
import matcher.classifier.MethodVarClassifier;
import matcher.classifier.RankResult;
import matcher.config.Config;
import matcher.config.ProjectConfig;
import matcher.jobs.JobCategories;
import matcher.jobs.MatcherJob;
import matcher.type.ClassEnv;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.InputFile;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

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

	public void init(ProjectConfig config) {
		var job = new MatcherJob<Void>(JobCategories.LOAD_PROJECT) {
			@Override
			protected void registerSubJobs() {
				Job<Void> subJob = new MatcherJob<Void>(JobCategories.INIT_ENV) {
					@Override
					protected Void execute(DoubleConsumer progressReceiver) {
						AtomicBoolean shouldCancel = new AtomicBoolean(false);
						addCancelListener(() -> shouldCancel.set(true));
						env.init(config, progressReceiver, shouldCancel);
						return null;
					}
				};
				addSubJob(subJob, true);

				subJob = new MatcherJob<Void>(JobCategories.MATCH_UNOBFUSCATED) {
					@Override
					protected Void execute(DoubleConsumer progressReceiver) {
						AtomicBoolean shouldCancel = new AtomicBoolean(false);
						addCancelListener(() -> shouldCancel.set(true));
						matchUnobfuscated(progressReceiver, shouldCancel);
						return null;
					}
				};
				addSubJob(subJob, false);
			}

			@Override
			protected Void execute(DoubleConsumer progressReceiver) {
				for (Job<?> subJob : getSubJobs(false)) {
					subJob.run();
				}

				return null;
			}
		};

		job.addCompletionListener((result, error) -> {
			if (error.isPresent()) {
				reset();
				throw new RuntimeException(error.get());
			}
		});
		job.runAndAwait();
	}

	private void matchUnobfuscated(DoubleConsumer progressReceiver, AtomicBoolean cancelListener) {
		final float classesCount = env.getClassesA().size();
		float classesDone = 0;

		for (ClassInstance cls : env.getClassesA()) {
			if (cancelListener.get()) {
				break;
			}

			double progress = classesDone++ / classesCount;
			progressReceiver.accept(progress);

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

	public void initFromMatches(List<Path> inputDirs,
			List<InputFile> inputFilesA, List<InputFile> inputFilesB,
			List<InputFile> cpFiles,
			List<InputFile> cpFilesA, List<InputFile> cpFilesB,
			String nonObfuscatedClassPatternA, String nonObfuscatedClassPatternB,
			String nonObfuscatedMemberPatternA, String nonObfuscatedMemberPatternB) throws IOException {
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
		init(config);
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

	public <T extends MemberInstance<T>> Map<T, T> match(ClassifierLevel level, double absThreshold, double relThreshold,
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

	public static double getRawScore(double score, double maxScore) {
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

	public static final Logger LOGGER = LoggerFactory.getLogger("Matcher");
	public static volatile ForkJoinPool threadPool = (ForkJoinPool) Executors.newWorkStealingPool(4);
	public volatile boolean debugMode;

	private final ClassEnvironment env;
	public static final ClassifierLevel defaultAutoMatchLevel = ClassifierLevel.Extra;
	public static final double absClassAutoMatchThreshold = 0.85;
	public static final double relClassAutoMatchThreshold = 0.085;
	public static final double absMethodAutoMatchThreshold = 0.85;
	public static final double relMethodAutoMatchThreshold = 0.085;
	public static final double absFieldAutoMatchThreshold = 0.85;
	public static final double relFieldAutoMatchThreshold = 0.085;
	public static final double absMethodArgAutoMatchThreshold = 0.85;
	public static final double relMethodArgAutoMatchThreshold = 0.085;
	public static final double absMethodVarAutoMatchThreshold = 0.85;
	public static final double relMethodVarAutoMatchThreshold = 0.085;
}
