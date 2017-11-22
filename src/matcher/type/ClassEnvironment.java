package matcher.type;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.DoubleConsumer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

import matcher.CfrIf;
import matcher.ProjectConfig;
import matcher.Util;
import matcher.classifier.ClassifierUtil;
import matcher.classifier.MatchingCache;

public class ClassEnvironment implements IClassEnv {
	public void init(ProjectConfig config, DoubleConsumer progressReceiver) {
		final double cpInitCost = 0.05;
		final double classReadCost = 0.2;
		double progress = 0;

		boolean inputsFirst = config.hasInputsBeforeClassPath();

		try {
			for (int i = 0; i < 2; i++) {
				if ((i == 0) != inputsFirst) {
					// class path indexing
					initClassPath(config.getSharedClassPath(), inputsFirst);
					CompletableFuture.allOf(
							CompletableFuture.runAsync(() -> extractorA.processClassPath(config.getClassPathA(), inputsFirst)),
							CompletableFuture.runAsync(() -> extractorB.processClassPath(config.getClassPathB(), inputsFirst))).get();
					progress += cpInitCost;
				} else {
					// async class reading
					CompletableFuture.allOf(
							CompletableFuture.runAsync(() -> extractorA.processInputs(config.getPathsA())),
							CompletableFuture.runAsync(() -> extractorB.processInputs(config.getPathsB()))).get();
					progress += classReadCost;
				}

				progressReceiver.accept(progress);
			}

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

		progressReceiver.accept(1);
	}

	private void initClassPath(Collection<Path> sharedClassPath, boolean checkExisting) throws IOException {
		for (Path archive : sharedClassPath) {
			cpFiles.add(new InputFile(archive));

			openFileSystems.add(Util.iterateJar(archive, false, file -> {
				String name = file.toAbsolutePath().toString();
				if (!name.startsWith("/") || !name.endsWith(".class") || name.startsWith("//")) throw new RuntimeException("invalid path: "+archive+" ("+name+")");
				name = name.substring(1, name.length() - ".class".length());

				if (!checkExisting || extractorA.getLocalClsByName(name) == null || extractorB.getLocalClsByName(name) == null) {
					classPathIndex.putIfAbsent(name, file);

					/*ClassNode cn = readClass(file);
					addSharedCls(new ClassInstance(ClassInstance.getId(cn.name), file.toUri(), cn));*/
				}
			}));
		}
	}

	public void reset() {
		cpFiles.clear();
		sharedClasses.clear();
		classPathIndex.clear();
		extractorA.reset();
		extractorB.reset();
		cache.clear();
	}

	public void addOpenFileSystem(FileSystem fs) {
		openFileSystems.add(fs);
	}

	@Override
	public boolean isShared() {
		return true;
	}

	@Override
	public ClassInstance getClsById(String id) {
		return getSharedClsById(id);
	}

	@Override
	public ClassInstance getLocalClsById(String id) {
		return getSharedClsById(id);
	}

	@Override
	public ClassInstance getClsByMappedId(String id) {
		return getSharedClsById(id);
	}

	public IClassEnv getEnvA() {
		return extractorA;
	}

	public IClassEnv getEnvB() {
		return extractorB;
	}

	public ClassInstance getClsByNameA(String name) {
		return extractorA.getClsByName(name);
	}

	public ClassInstance getClsByNameB(String name) {
		return extractorB.getClsByName(name);
	}

	public ClassInstance getClsByIdA(String id) {
		return extractorA.getClsById(id);
	}

	public ClassInstance getClsByIdB(String id) {
		return extractorB.getClsById(id);
	}

	public ClassInstance getLocalClsByNameA(String name) {
		return extractorA.getLocalClsByName(name);
	}

	public ClassInstance getLocalClsByNameB(String name) {
		return extractorB.getLocalClsByName(name);
	}

	public ClassInstance getLocalClsByIdA(String id) {
		return extractorA.getLocalClsById(id);
	}

	public ClassInstance getLocalClsByIdB(String id) {
		return extractorB.getLocalClsById(id);
	}

	public ClassInstance getSharedClsById(String id) {
		assert !id.isEmpty();
		assert id.length() == 1 || id.charAt(id.length() - 1) == ';' || id.charAt(0) == '[' && id.lastIndexOf('[') == id.length() - 2 : id;

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

	public Collection<ClassInstance> getClassesA() {
		return extractorA.getClasses().values();
	}

	public Collection<ClassInstance> getClassesB() {
		return extractorB.getClasses().values();
	}

	public List<ClassInstance> getDisplayClassesA(boolean inputsOnly) {
		return getDisplayClasses(extractorA, inputsOnly);
	}

	public List<ClassInstance> getDisplayClassesB(boolean inputsOnly) {
		return getDisplayClasses(extractorB, inputsOnly);
	}

	private static List<ClassInstance> getDisplayClasses(ClassFeatureExtractor extractor, boolean inputsOnly) {
		List<ClassInstance> ret = new ArrayList<>();

		for (ClassInstance cls : extractor.getClasses().values()) {
			if (cls.getUri() == null || inputsOnly && !cls.isInput()) continue;

			ret.add(cls);
		}

		ret.sort(Comparator.comparing(ClassInstance::toString));

		return ret;
	}

	public Collection<InputFile> getClassPathFiles() {
		return cpFiles;
	}

	public Collection<InputFile> getClassPathFilesA() {
		return extractorA.getClassPathFiles();
	}

	public Collection<InputFile> getClassPathFilesB() {
		return extractorB.getClassPathFiles();
	}

	public Collection<InputFile> getInputFilesA() {
		return extractorA.getInputFiles();
	}

	public Collection<InputFile> getInputFilesB() {
		return extractorB.getInputFiles();
	}

	public byte[] serializeClass(ClassInstance cls, boolean isA, boolean mapped) {
		ClassFeatureExtractor extractor = isA ? extractorA : extractorB;

		return extractor.serializeClass(cls, mapped);
	}

	public String decompile(ClassInstance cls, boolean mapped) {
		ClassFeatureExtractor extractor;

		if (extractorA.getClasses().get(cls.getId()) == cls) {
			extractor = extractorA;
		} else if (extractorB.getClasses().get(cls.getId()) == cls) {
			extractor = extractorB;
		} else {
			throw new IllegalArgumentException("unknown class: "+cls);
		}

		return CfrIf.decompile(cls, extractor, mapped);
	}

	@Override
	public ClassInstance getCreateClassInstance(String id, boolean createUnknown) {
		ClassInstance ret = getSharedClsById(id);
		if (ret != null) return ret;

		if (id.charAt(0) == '[') { // array type
			ClassInstance elementClass = getArrayCls(this, id);
			ClassInstance cls = new ClassInstance(id, elementClass);

			assert elementClass.isShared();
			ret = addSharedCls(cls);

			if (ret == cls) { // cls was added
				addSuperClass(ret, "java/lang/Object");
			}
		} else {
			ret = getMissingCls(id, createUnknown);
		}

		return ret;
	}

	static ClassInstance getArrayCls(IClassEnv env, String id) {
		assert id.startsWith("[");

		String elementId = id.substring(id.lastIndexOf('[') + 1);
		if (elementId.isEmpty()) throw new IllegalArgumentException("invalid class desc: "+id);
		assert elementId.length() == 1 || elementId.charAt(elementId.length() - 1) == ';' : elementId;

		return env.getCreateClassInstance(elementId);
	}

	ClassInstance getMissingCls(String id, boolean createUnknown) {
		if (id.length() > 1) {
			String name = ClassInstance.getName(id);
			Path file = getSharedClassLocation(name);

			if (file == null) {
				URL url = ClassLoader.getSystemResource(name+".class");

				if (url != null) {
					file = getPath(url);
				}
			}

			if (file != null) {
				ClassNode cn = readClass(file);
				ClassInstance cls = new ClassInstance(ClassInstance.getId(cn.name), file.toUri(), this, cn);
				if (!cls.getId().equals(id)) throw new RuntimeException("mismatched cls id "+id+" for "+file+", expected "+name);

				ClassInstance ret = addSharedCls(cls);

				if (ret == cls) { // cls was added
					processClassA(ret);
				}

				return ret;
			}
		}

		if (!createUnknown) return null;

		ClassInstance ret = new ClassInstance(id, this);
		addSharedCls(ret);

		return ret;
	}

	private Path getPath(URL url) {
		URI uri = null;

		try {
			uri = url.toURI();

			return Paths.get(uri);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		} catch (FileSystemNotFoundException e) {
			try {
				addOpenFileSystem(FileSystems.newFileSystem(uri, Collections.emptyMap()));

				return Paths.get(uri);
			} catch (FileSystemNotFoundException e2) {
				throw new RuntimeException("can't find fs for "+url, e2);
			} catch (IOException e2) {
				throw new UncheckedIOException(e2);
			}
		}
	}

	static ClassNode readClass(Path path) {
		try {
			ClassReader reader = new ClassReader(Files.readAllBytes(path));
			ClassNode cn = new ClassNode();
			reader.accept(cn, ClassReader.EXPAND_FRAMES);

			return cn;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * 1st class processing pass, member and class hierarchy initialization.
	 *
	 * Only the (known) classes are fully available at this point.
	 */
	static void processClassA(ClassInstance cls) {
		Set<String> strings = cls.strings;

		for (ClassNode cn : cls.getAsmNodes()) {
			for (int i = 0; i < cn.methods.size(); i++) {
				MethodNode mn = cn.methods.get(i);

				if (cls.getMethod(mn.name, mn.desc) == null) {
					boolean nameObfuscated = !cls.isShared() && !mn.name.equals("<clinit>") && !mn.name.equals("<init>");
					cls.addMethod(new MethodInstance(cls, mn.name, mn.desc, mn, nameObfuscated, i));

					ClassifierUtil.extractStrings(mn.instructions, strings);
				}
			}

			for (int i = 0; i < cn.fields.size(); i++) {
				FieldNode fn = cn.fields.get(i);

				if (cls.getField(fn.name, fn.desc) == null) {
					boolean nameObfuscated = !cls.isShared();
					cls.addField(new FieldInstance(cls, fn.name, fn.desc, fn, nameObfuscated, i));

					if (fn.value instanceof String) {
						strings.add((String) fn.value);
					}
				}
			}

			if (cls.getOuterClass() == null) detectOuterClass(cls, cn);

			if (cn.superName != null && cls.getSuperClass() == null) {
				addSuperClass(cls, cn.superName);
			}

			for (String iface : cn.interfaces) {
				ClassInstance ifCls = cls.getEnv().getCreateClassInstance(ClassInstance.getId(iface));

				if (cls.interfaces.add(ifCls)) ifCls.implementers.add(cls);
			}
		}
	}

	private static void detectOuterClass(ClassInstance cls, ClassNode cn) {
		if (cn.outerClass != null) {
			addOuterClass(cls, cn.outerClass, true);
		} else if (cn.outerMethod != null) {
			throw new UnsupportedOperationException();
		} else { // determine outer class by outer$inner name pattern
			for (InnerClassNode icn : cn.innerClasses) {
				if (icn.name.equals(cn.name)) {
					addOuterClass(cls, icn.outerName, true);
					return;
				}
			}

			int pos;

			if ((pos = cn.name.lastIndexOf('$')) > 0 && pos < cn.name.length() - 1) {
				addOuterClass(cls, cn.name.substring(0, pos), false);
			}
		}
	}

	private static void addOuterClass(ClassInstance cls, String name, boolean createUnknown) {
		ClassInstance outerClass = cls.getEnv().getLocalClsByName(name);

		if (outerClass == null) {
			outerClass = cls.getEnv().getCreateClassInstance(ClassInstance.getId(name), createUnknown);

			if (outerClass == null) {
				System.err.println("missing outer cls: "+name+" for "+cls);
				return;
			}
		}

		cls.outerClass = outerClass;
		outerClass.innerClasses.add(cls);
	}

	static void addSuperClass(ClassInstance cls, String name) {
		cls.superClass = cls.getEnv().getCreateClassInstance(ClassInstance.getId(name));
		cls.superClass.childClasses.add(cls);
	}

	@Override
	public ClassEnvironment getGlobal() {
		return this;
	}

	public MatchingCache getCache() {
		return cache;
	}

	private final List<InputFile> cpFiles = new ArrayList<>();
	private final Map<String, ClassInstance> sharedClasses = new HashMap<>();
	private final List<FileSystem> openFileSystems = new ArrayList<>();
	private final Map<String, Path> classPathIndex = new HashMap<>();
	private final ClassFeatureExtractor extractorA = new ClassFeatureExtractor(this);
	private final ClassFeatureExtractor extractorB = new ClassFeatureExtractor(this);
	private final MatchingCache cache = new MatchingCache();
}
