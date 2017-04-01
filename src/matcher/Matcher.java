package matcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import matcher.classifier.ClassClassifier;
import matcher.classifier.FieldClassifier;
import matcher.classifier.IRanker;
import matcher.classifier.MethodClassifier;
import matcher.classifier.RankResult;
import matcher.mapping.IClassMappingAcceptor;
import matcher.mapping.IFieldMappingAcceptor;
import matcher.mapping.IMethodMappingAcceptor;
import matcher.mapping.MappingFormat;
import matcher.mapping.MappingReader;
import matcher.mapping.MappingWriter;
import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.InputFile;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;

public class Matcher {
	public Matcher() {
		this.sharedClasses = new HashMap<>();
		this.extractorA = new ClassFeatureExtractor(this);
		this.extractorB = new ClassFeatureExtractor(this);
	}

	public void init(ProjectConfig config, DoubleConsumer progressReceiver) {
		try {
			// async class reading
			CompletableFuture.allOf(
					CompletableFuture.runAsync(() -> extractorA.processInputs(config.pathsA)),
					CompletableFuture.runAsync(() -> extractorB.processInputs(config.pathsB))).get();
			progressReceiver.accept(0.2);

			// class path indexing
			initClassPath(config.sharedClassPath);
			progressReceiver.accept(0.25);

			// synchronous feature extraction
			extractorA.process();
			progressReceiver.accept(0.8);

			extractorB.process();
			progressReceiver.accept(0.98);
		} catch (InterruptedException | ExecutionException | IOException e) {
			throw new RuntimeException(e);
		} finally {
			classPathIndex.clear();
			openFileSystems.forEach(Util::closeSilently);
			openFileSystems.clear();
		}

		matchUnobfuscated();

		ClassClassifier.init();
		MethodClassifier.init();
		FieldClassifier.init();

		progressReceiver.accept(1);
	}

	private void initClassPath(Collection<Path> sharedClassPath) throws IOException {
		for (Path archive : sharedClassPath) {
			openFileSystems.add(Util.iterateJar(archive, false, file -> {
				String name = file.toAbsolutePath().toString();
				if (!name.startsWith("/") || !name.endsWith(".class") || name.startsWith("//")) throw new RuntimeException("invalid path: "+archive+" ("+name+")");
				name = name.substring(1, name.length() - ".class".length());

				if (extractorA.getClassInstance(name) == null || extractorB.getClassInstance(name) == null) {
					classPathIndex.putIfAbsent(name, file);
				}
			}));
		}
	}

	private void matchUnobfuscated() {
		for (ClassInstance cls : extractorA.getClasses().values()) {
			if (cls.isNameObfuscated()) continue;

			ClassInstance match = extractorB.getClasses().get(cls.getId());

			if (match != null && !match.isNameObfuscated()) {
				match(cls, match);
			}
		}
	}

	public void reset() {
		extractorA.reset();
		extractorB.reset();
		sharedClasses.clear();
	}

	public void addOpenFileSystem(FileSystem fs) {
		openFileSystems.add(fs);
	}

	public ClassInstance getSharedCls(String id) {
		return sharedClasses.get(id);
	}

	public ClassInstance addSharedCls(ClassInstance cls) {
		if (!cls.isShared()) throw new IllegalArgumentException("non-shared class");

		ClassInstance prev = sharedClasses.putIfAbsent(cls.getId(), cls);
		if (prev != null) return prev;

		return cls;
	}

	public Path getSharedClassLocation(String name) {
		return classPathIndex.get(name);
	}

	public List<ClassInstance> getClassesA() {
		return getClasses(extractorA);
	}

	public List<ClassInstance> getClassesB() {
		return getClasses(extractorB);
	}

	public void readMatches(Path path) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			ParserState state = ParserState.START;
			List<InputFile> inputFilesA = new ArrayList<>();
			List<InputFile> inputFilesB = new ArrayList<>();
			ClassInstance currentClass = null;
			String line;

			while ((line = reader.readLine()) != null) {
				if (line.isEmpty()) continue;

				boolean repeat;

				do {
					repeat = false;

					switch (state) {
					case START:
						if (!line.startsWith("Matches saved")) throw new IOException("invalid matches file, incorrect header");
						state = ParserState.FILES_A_START;
						break;
					case FILES_A_START:
					case FILES_B_START:
						inputFilesA = new ArrayList<>();
						state = ParserState.values()[state.ordinal() + 1];
						break;
					case FILES_A:
					case FILES_B:
						if (!line.startsWith("\t\t")) {
							state = state == ParserState.FILES_A ? ParserState.FILES_B_START : ParserState.CONTENT;
							repeat = true;
						} else {
							List<InputFile> inputFiles = state == ParserState.FILES_A ? inputFilesA : inputFilesB;
							int pos = line.indexOf('\t', 2);
							if (pos == -1 || pos == 2 || pos + 1 >= line.length()) throw new IOException("invalid matches file");
							long size = Long.parseLong(line.substring(2, pos));
							int pos2 = line.indexOf('\t', pos + 1);
							if (pos2 == -1 || pos2 == pos + 1 || pos2 + 1 >= line.length()) throw new IOException("invalid matches file");
							byte[] hash = Base64.getDecoder().decode(line.substring(pos + 1, pos2));
							inputFiles.add(new InputFile(line.substring(pos2 + 1), size, hash));
						}

						break;
					case CONTENT:
						if (line.startsWith("c\t")) {
							int pos = line.indexOf('\t', 2);
							if (pos == -1 || pos == 2 || pos + 1 == line.length()) throw new IOException("invalid matches file");
							String idA = line.substring(2, pos);
							String idB = line.substring(pos + 1);
							currentClass = extractorA.getClasses().get(idA);
							ClassInstance target;

							if (currentClass == null) {
								System.err.println("Unknown a class "+idA);
							} else if ((target = extractorB.getClasses().get(idB)) == null) {
								System.err.println("Unknown b class "+idA);
								currentClass = null;
							} else {
								match(currentClass, target);
							}
						} else if (line.startsWith("\tm\t") || line.startsWith("\tf\t")) {
							if (currentClass != null) {
								int pos = line.indexOf('\t', 3);
								if (pos == -1 || pos == 3 || pos + 1 == line.length()) throw new IOException("invalid matches file");
								String idA = line.substring(3, pos);
								String idB = line.substring(pos + 1);

								if (line.charAt(1) == 'm') {
									MethodInstance a = currentClass.getMethod(idA);
									MethodInstance b;

									if (a == null) {
										System.err.println("Unknown a method "+idA+" in class "+currentClass);
									} else if ((b = currentClass.getMatch().getMethod(idB)) == null) {
										System.err.println("Unknown b method "+idB+" in class "+currentClass.getMatch());
									} else {
										match(a, b);
									}
								} else {
									FieldInstance a = currentClass.getField(idA);
									FieldInstance b;

									if (a == null) {
										System.err.println("Unknown a field "+idA+" in class "+currentClass);
									} else if ((b = currentClass.getMatch().getField(idB)) == null) {
										System.err.println("Unknown b field "+idB+" in class "+currentClass.getMatch());
									} else {
										match(a, b);
									}
								}
							}
						}

						break;
					}
				} while (repeat);
			}

			if (state != ParserState.CONTENT) throw new IOException("invalid matches file");
		}
	}

	private static enum ParserState {
		START, FILES_A_START, FILES_A, FILES_B_START, FILES_B, CONTENT;
	}

	public boolean saveMatches(Path path) throws IOException {
		List<ClassInstance> classes = extractorA.getClasses().values().stream()
				.filter(cls -> cls.getMatch() != null)
				.sorted(Comparator.comparing(cls -> cls.getId()))
				.collect(Collectors.toList());
		if (classes.isEmpty()) return false;

		try (Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			writer.write("Matches saved ");
			writer.write(ZonedDateTime.now().toString());
			writer.write(", input files:\n\ta:\n");
			writeInputFiles(extractorA.getInputFiles(), writer);
			writer.write("\tb:\n");
			writeInputFiles(extractorB.getInputFiles(), writer);

			for (ClassInstance cls : classes) {
				assert !cls.isShared();

				writer.write("c\t");
				writer.write(cls.getId());
				writer.write('\t');
				writer.write(cls.getMatch().getId());
				writer.write('\n');

				writeMembers(cls.getMethods(), writer);
				writeMembers(cls.getFields(), writer);
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

	private static void writeMembers(MemberInstance<?>[] members, Writer out) throws IOException {
		for (MemberInstance<?> member : members) {
			if (member.getMatch() == null) continue;

			out.write('\t');
			out.write(member instanceof MethodInstance ? 'm' : 'f');
			out.write('\t');
			out.write(member.getId());
			out.write('\t');
			out.write(member.getMatch().getId());
			out.write('\n');
		}
	}

	public void readMappings(Path path) throws IOException {
		int[] counts = new int[3];
		Set<String> warnedClasses = new HashSet<>();

		IClassMappingAcceptor cmAcceptor = (String srcName, String dstName) -> {
			ClassInstance cls = extractorA.getClassInstance(srcName);

			if (cls == null) {
				if (warnedClasses.add(srcName)) System.out.println("can't find mapped class "+srcName+" ("+dstName+")");
			} else {
				cls.setMappedName(dstName);
				counts[0]++;
			}
		};

		IMethodMappingAcceptor mmAcceptor = (String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) -> {
			ClassInstance cls = extractorA.getClassInstance(srcClsName);
			MethodInstance method;

			if (cls == null) {
				if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
			} else if ((method = cls.getMethod(srcName, srcDesc)) == null) {
				System.out.println("can't find mapped method "+srcClsName+"/"+srcName+" ("+(cls.getMappedName() != null ? cls.getMappedName()+"/" : "")+dstName+")");
			} else {
				method.setMappedName(dstName);
				counts[1]++;
			}
		};

		IFieldMappingAcceptor fmAcceptor = (String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) -> {
			ClassInstance cls = extractorA.getClassInstance(srcClsName);
			FieldInstance field;

			if (cls == null) {
				if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
			} else if ((field = cls.getField(srcName, srcDesc)) == null) {
				System.out.println("can't find mapped field "+srcClsName+"/"+srcName+" ("+(cls.getMappedName() != null ? cls.getMappedName()+"/" : "")+dstName+")");
			} else {
				field.setMappedName(dstName);
				counts[2]++;
			}
		};

		clearMappings();

		try {
			MappingReader.read(path, cmAcceptor, mmAcceptor, fmAcceptor);
		} catch (Throwable t) {
			clearMappings();
			throw t;
		}

		System.out.printf("Loaded mappings for %d classes, %d methods and %d fields.", counts[0], counts[1], counts[2]);
	}

	public void clearMappings() {
		for (ClassInstance cls : extractorA.getClasses().values()) {
			cls.setMappedName(null);

			for (MethodInstance method : cls.getMethods()) {
				method.setMappedName(null);
			}

			for (FieldInstance field : cls.getFields()) {
				field.setMappedName(null);
			}
		}
	}

	public boolean saveMappings(Path file, MappingFormat format) throws IOException {
		List<ClassInstance> classes = extractorB.getClasses().values().stream()
				.filter(ClassInstance::hasMappedName)
				.sorted(Comparator.comparing(ClassInstance::getId))
				.collect(Collectors.toList());
		if (classes.isEmpty()) return false;

		try (MappingWriter writer = new MappingWriter(file, format)) {
			for (ClassInstance cls : classes) {
				String name = cls.getName();
				String mappedName = cls.getMappedName();

				writer.acceptClass(name, mappedName);

				Stream.of(cls.getMethods())
				.filter(MemberInstance::hasMappedName)
				.sorted(Comparator.<MemberInstance<?>, String>comparing(m -> m.getOrigName()).thenComparing(MemberInstance::getDesc))
				.forEachOrdered(m -> writer.acceptMethod(name, m.getOrigName(), m.getDesc(), mappedName, m.getMappedName(), getMappedDesc(m)));

				Stream.of(cls.getFields())
				.filter(MemberInstance::hasMappedName)
				.sorted(Comparator.<MemberInstance<?>, String>comparing(m -> m.getOrigName()).thenComparing(MemberInstance::getDesc))
				.forEachOrdered(m -> writer.acceptField(name, m.getOrigName(), m.getDesc(), mappedName, m.getMappedName(), getMappedDesc(m)));
			}
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}

		return true;
	}

	private static String getMappedDesc(MethodInstance member) {
		String ret = "(";

		for (ClassInstance arg : member.getArgs()) {
			ret += getMappedDesc(arg);
		}

		ret += ")" + getMappedDesc(member.getRetType());

		return ret;
	}

	private static String getMappedDesc(FieldInstance member) {
		return getMappedDesc(member.getType());
	}

	private static String getMappedDesc(ClassInstance cls) {
		if (!cls.hasMappedName()) {
			return cls.getId();
		} else if (cls.isArray()) {
			return cls.getId().substring(0, cls.getId().lastIndexOf('[') + 1) + getMappedDesc(cls.getElementClass());
		} else {
			return "L"+cls.getMappedName()+";";
		}
	}

	public byte[] serializeClass(ClassInstance cls, boolean isA) {
		ClassFeatureExtractor extractor = isA ? extractorA : extractorB;

		return extractor.serializeClass(cls);
	}

	public void match(ClassInstance a, ClassInstance b) {
		if (a == null) throw new NullPointerException("null class A");
		if (b == null) throw new NullPointerException("null class B");
		if (a.getMatch() == b) return;

		System.out.println("match class "+a+" -> "+b+(a.getMappedName() != null ? " ("+a.getMappedName()+")" : ""));

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
	}

	private static void unmatchMembers(ClassInstance cls) {
		for (MethodInstance m : cls.getMethods()) {
			if (m.getMatch() != null) {
				m.getMatch().setMatch(null);
				m.setMatch(null);
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

		System.out.println("match method "+a+" -> "+b+(a.getMappedName() != null ? " ("+a.getMappedName()+")" : ""));

		if (a.getMatch() != null) a.getMatch().setMatch(null);
		if (b.getMatch() != null) b.getMatch().setMatch(null);

		a.setMatch(b);
		b.setMatch(a);
	}

	public void match(FieldInstance a, FieldInstance b) {
		if (a == null) throw new NullPointerException("null method A");
		if (b == null) throw new NullPointerException("null method B");
		if (a.getCls().getMatch() != b.getCls()) throw new IllegalArgumentException("the methods don't belong to the same class");
		if (a.getMatch() == b) return;

		System.out.println("match field "+a+" -> "+b+(a.getMappedName() != null ? " ("+a.getMappedName()+")" : ""));

		if (a.getMatch() != null) a.getMatch().setMatch(null);
		if (b.getMatch() != null) b.getMatch().setMatch(null);

		a.setMatch(b);
		b.setMatch(a);
	}

	public void unmatch(ClassInstance cls) {
		if (cls == null) throw new NullPointerException("null class");
		if (cls.getMatch() == null) return;

		System.out.println("unmatch class "+cls+" (was "+cls.getMatch()+")"+(cls.getMappedName() != null ? " ("+cls.getMappedName()+")" : ""));

		unmatchMembers(cls);
		cls.setMatch(null);
	}

	public void unmatch(MemberInstance<?> m) {
		if (m == null) throw new NullPointerException("null class");
		if (m.getMatch() == null) return;

		System.out.println("unmatch member "+m+" (was "+m.getMatch()+")"+(m.getMappedName() != null ? " ("+m.getMappedName()+")" : ""));

		m.getMatch().setMatch(null);
		m.setMatch(null);
	}

	public boolean autoMatchClasses(double normalizedAbsThreshold, double relThreshold, DoubleConsumer progressReceiver) {
		double absThreshold = normalizedAbsThreshold * ClassClassifier.getMaxScore();

		Predicate<ClassInstance> filter = cls -> cls.getUri() != null && cls.isNameObfuscated() && cls.getMatch() == null;

		List<ClassInstance> classes = extractorA.getClasses().values().stream()
				.filter(filter)
				.collect(Collectors.toList());

		ClassInstance[] cmpClasses = extractorB.getClasses().values().stream()
				.filter(filter)
				.collect(Collectors.toList()).toArray(new ClassInstance[0]);

		Map<ClassInstance, ClassInstance> matches = new ConcurrentHashMap<>(classes.size());

		runInParallel(classes, cls -> {
			List<RankResult<ClassInstance>> ranking = ClassClassifier.rank(cls, cmpClasses, this);

			if (checkRank(ranking, absThreshold, relThreshold)) {
				ClassInstance match = ranking.get(0).getSubject();

				matches.put(cls, match);
			}
		}, progressReceiver);

		sanitizeMatches(matches);

		for (Map.Entry<ClassInstance, ClassInstance> entry : matches.entrySet()) {
			match(entry.getKey(), entry.getValue());
		}

		System.out.println("Auto matched "+matches.size()+" classes ("+(classes.size() - matches.size())+" unmatched, "+extractorA.getClasses().size()+" total)");

		return !matches.isEmpty();
	}

	private <T, C> void runInParallel(List<T> workSet, Consumer<T> worker, DoubleConsumer progressReceiver) {
		if (workSet.isEmpty()) return;

		AtomicInteger itemsDone = new AtomicInteger();
		int updateRate = Math.max(1, workSet.size() / 200);

		workSet.parallelStream().forEach(workItem -> {
			worker.accept(workItem);

			int cItemsDone = itemsDone.incrementAndGet();

			if ((cItemsDone % updateRate) == 0) {
				progressReceiver.accept((double) cItemsDone / workSet.size());
			}
		});
	}

	public boolean autoMatchMethods(double normalizedAbsThreshold, double relThreshold, DoubleConsumer progressReceiver) {
		double absThreshold = normalizedAbsThreshold * MethodClassifier.getMaxScore();

		AtomicInteger totalUnmatched = new AtomicInteger();
		Map<MethodInstance, MethodInstance> matches = match(absThreshold, relThreshold, cls -> cls.getMethods(), MethodClassifier::rank, progressReceiver, totalUnmatched);

		for (Map.Entry<MethodInstance, MethodInstance> entry : matches.entrySet()) {
			match(entry.getKey(), entry.getValue());
		}

		System.out.println("Auto matched "+matches.size()+" methods ("+totalUnmatched.get()+" unmatched)");

		return !matches.isEmpty();
	}

	public boolean autoMatchFields(double normalizedAbsThreshold, double relThreshold, DoubleConsumer progressReceiver) {
		double absThreshold = normalizedAbsThreshold * FieldClassifier.getMaxScore();

		AtomicInteger totalUnmatched = new AtomicInteger();
		Map<FieldInstance, FieldInstance> matches = match(absThreshold, relThreshold, cls -> cls.getFields(), FieldClassifier::rank, progressReceiver, totalUnmatched);

		for (Map.Entry<FieldInstance, FieldInstance> entry : matches.entrySet()) {
			match(entry.getKey(), entry.getValue());
		}

		System.out.println("Auto matched "+matches.size()+" fields ("+totalUnmatched.get()+" unmatched)");

		return !matches.isEmpty();
	}

	private <T extends MemberInstance<T>> Map<T, T> match(double absThreshold, double relThreshold,
			Function<ClassInstance, T[]> memberGetter, IRanker<T> ranker,
			DoubleConsumer progressReceiver, AtomicInteger totalUnmatched) {
		List<ClassInstance> classes = extractorA.getClasses().values().stream()
				.filter(cls -> cls.getUri() != null && cls.getMatch() != null && memberGetter.apply(cls).length > 0)
				.filter(cls -> {
					boolean found = false;

					for (T member : memberGetter.apply(cls)) {
						if (member.getMatch() == null) {
							found = true;
							break;
						}
					}

					if (!found) return false;

					for (T member : memberGetter.apply(cls)) {
						if (member.getMatch() == null) return true;
					}

					return false;
				})
				.collect(Collectors.toList());
		if (classes.isEmpty()) return Collections.emptyMap();

		Map<T, T> ret = new ConcurrentHashMap<>(512);

		runInParallel(classes, cls -> {
			int unmatched = 0;

			for (T member : memberGetter.apply(cls)) {
				if (member.getMatch() != null) continue;

				List<RankResult<T>> ranking = ranker.rank(member, memberGetter.apply(cls.getMatch()), this);

				if (checkRank(ranking, absThreshold, relThreshold)) {
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

	private static boolean checkRank(List<? extends RankResult<?>> ranking, double absThreshold, double relThreshold) {
		if (ranking.isEmpty()) return false;
		if (ranking.get(0).getScore() < absThreshold) return false;

		if (ranking.size() == 1) {
			return true;
		} else {
			return ranking.get(1).getScore() < ranking.get(0).getScore() * (1 - relThreshold);
		}
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

	public MatchingStatus getStatus() {
		int totalClassCount = extractorA.getClasses().size();
		int matchedClassCount = 0;
		int totalMethodCount = 0;
		int matchedMethodCount = 0;
		int totalFieldCount = 0;
		int matchedFieldCount = 0;

		for (ClassInstance cls : extractorA.getClasses().values()) {
			if (cls.getMatch() != null) matchedClassCount++;

			totalMethodCount += cls.getMethods().length;
			totalFieldCount += cls.getFields().length;

			for (MethodInstance method : cls.getMethods()) {
				if (method.getMatch() != null) matchedMethodCount++;
			}

			for (FieldInstance field : cls.getFields()) {
				if (field.getMatch() != null) matchedFieldCount++;
			}
		}

		return new MatchingStatus(totalClassCount, matchedClassCount, totalMethodCount, matchedMethodCount, totalFieldCount, matchedFieldCount);
	}

	private static List<ClassInstance> getClasses(ClassFeatureExtractor extractor) {
		return extractor.getClasses().values().stream()
				.filter(cls -> cls.getUri() != null)
				.sorted(Comparator.comparing(ClassInstance::toString))
				.collect(Collectors.toList());
	}

	public static class MatchingStatus {
		MatchingStatus(int totalClassCount, int matchedClassCount,
				int totalMethodCount, int matchedMethodCount,
				int totalFieldCount, int matchedFieldCount) {
			this.totalClassCount = totalClassCount;
			this.matchedClassCount = matchedClassCount;
			this.totalMethodCount = totalMethodCount;
			this.matchedMethodCount = matchedMethodCount;
			this.totalFieldCount = totalFieldCount;
			this.matchedFieldCount = matchedFieldCount;
		}

		final int totalClassCount;
		final int matchedClassCount;
		final int totalMethodCount;
		final int matchedMethodCount;
		final int totalFieldCount;
		final int matchedFieldCount;
	}

	private final Map<String, ClassInstance> sharedClasses;
	private final List<FileSystem> openFileSystems = new ArrayList<>();
	private final Map<String, Path> classPathIndex = new HashMap<>();
	private final ClassFeatureExtractor extractorA;
	private final ClassFeatureExtractor extractorB;
}
