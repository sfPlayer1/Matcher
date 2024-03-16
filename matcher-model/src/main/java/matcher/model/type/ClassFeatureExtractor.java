package matcher.model.type;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import matcher.model.NameType;
import matcher.model.Util;
import matcher.model.type.Analysis.CommonClasses;

public class ClassFeatureExtractor implements LocalClassEnv {
	public ClassFeatureExtractor(ClassEnvironment env) {
		this.env = env;
	}

	public void processInputs(Collection<Path> inputs, Pattern nonObfuscatedClasses) {
		Set<Path> uniqueInputs = new LinkedHashSet<>(inputs);
		Predicate<ClassNode> obfuscatedCheck = cn -> isNameObfuscated(cn, nonObfuscatedClasses);

		for (Path archive : uniqueInputs) {
			inputFiles.add(new InputFile(archive));
			URI origin = archive.toUri();

			Util.iterateJar(archive, true, file -> {
				ClassInstance cls = readClass(file, origin, obfuscatedCheck);
				String id = cls.getId();
				String name = cls.getName();

				if (env.getSharedClsById(id) != null) return;
				if (env.getSharedClassLocation(name) != null) return;
				if (classPathIndex.containsKey(name)) return;

				ClassInstance prev = classes.get(id);

				if (prev == null) {
					classes.put(id, cls);
				} else if (prev.isInput()) {
					mergeClasses(cls, prev);
				}
			});
		}
	}

	public void processClassPath(Collection<Path> classPath, boolean checkExisting) {
		for (Path archive : classPath) {
			cpFiles.add(new InputFile(archive));

			FileSystem fs = Util.iterateJar(archive, false, file -> {
				String name = file.toAbsolutePath().toString();
				if (!name.startsWith("/") || !name.endsWith(".class") || name.startsWith("//")) throw new RuntimeException("invalid path: "+archive+" ("+name+")");
				name = name.substring(1, name.length() - ".class".length());

				if (!checkExisting || getLocalClsByName(name) == null && env.getSharedClassLocation(name) == null && env.getLocalClsByName(name) == null) {
					classPathIndex.putIfAbsent(name, file);

					/*ClassNode cn = readClass(file);
					addSharedCls(new ClassInstance(ClassInstance.getId(cn.name), file.toUri(), cn));*/
				}
			});

			if (fs != null) env.addOpenFileSystem(fs);
		}
	}

	private static boolean isNameObfuscated(ClassNode cn, Pattern pattern) {
		return pattern == null || !pattern.matcher(cn.name).matches();
	}

	private ClassInstance readClass(Path path, URI origin, Predicate<ClassNode> nameObfuscated) {
		ClassNode cn = ClassEnvironment.readClass(path, false);

		return new ClassInstance(ClassInstance.getId(cn.name), origin, this, cn, nameObfuscated.test(cn));
	}

	private static void mergeClasses(ClassInstance from, ClassInstance to) {
		assert from.getAsmNodes().length == 1;

		to.addAsmNode(from.getAsmNodes()[0], from.getOrigin());
	}

	public void process(Pattern nonObfuscatedMemberPattern) {
		assert initStep == 0;

		ClassInstance clo = getCreateClassInstance("Ljava/lang/Object;");
		assert clo != null && clo.getAsmNodes() != null;

		initStep++;
		List<ClassInstance> initialClasses = new ArrayList<>(classes.values());

		for (ClassInstance cls : initialClasses) {
			if (cls.isReal()) ClassEnvironment.processClassA(cls, nonObfuscatedMemberPattern);
		}

		initStep++;
		initialClasses.clear();
		initialClasses.addAll(classes.values());
		assert initialClasses.size() == new HashSet<>(initialClasses).size();

		for (ClassInstance cls : initialClasses) {
			if (cls.isReal()) processClassB(cls);
		}

		processPending(null);

		initStep++;
		initialClasses.clear();
		initialClasses.addAll(classes.values());

		for (ClassInstance cls : initialClasses) {
			if (cls.isReal()) processClassC(cls);
		}

		processPending(null);

		initStep++;
		initialClasses.clear();
		initialClasses.addAll(classes.values());

		CommonClasses common = new CommonClasses(this);

		for (ClassInstance cls : initialClasses) {
			if (cls.isReal()) processClassD(cls, common);
		}

		processPending(common);

		initStep++;

		int clsIdx = 0;
		AtomicInteger vmIdx = new AtomicInteger();

		for (ClassInstance cls : initialClasses) {
			if (!cls.isReal() || !cls.isInput()) continue;

			int curClsIdx = cls.nameObfuscated ? clsIdx++ : -1;

			processClassE(cls, curClsIdx, vmIdx);
		}

		initStep++;
	}

	private void processPending(CommonClasses commonClasses) {
		if (pendingInit.isEmpty()) return;

		List<List<ClassInstance>> steps = new ArrayList<>(initStep - 1);

		for (int i = 1; i < initStep; i++) {
			steps.add(new ArrayList<>());
		}

		pendingInitLoop: do {
			assert steps.get(0).isEmpty();
			steps.get(0).addAll(pendingInit);
			pendingInit.clear();

			for (int i = 1; i < initStep; i++) {
				for (ClassInstance cls : steps.get(i - 1)) {
					assert cls.isReal();

					switch (i) {
					case 1: processClassB(cls); break;
					case 2: processClassC(cls); break;
					case 3: processClassD(cls, commonClasses); break;
					default: throw new IllegalStateException();
					}
				}

				if (i + 1 < initStep) {
					steps.get(i).addAll(steps.get(i - 1));
				}

				steps.get(i - 1).clear();

				if (!pendingInit.isEmpty()) continue pendingInitLoop;
			}
		} while (!pendingInit.isEmpty());
	}

	public void reset() {
		inputFiles.clear();
		cpFiles.clear();
		classPathIndex.clear();
		classes.clear();
		arrayClasses.clear();
		pendingInit.clear();
		initStep = 0;
	}

	@Override
	public Collection<ClassInstance> getClasses() {
		return roClasses.values();
	}

	public Collection<InputFile> getInputFiles() {
		return inputFiles;
	}

	public List<InputFile> getClassPathFiles() {
		return cpFiles;
	}

	/**
	 * 2nd class processing pass, inter-member initialization.
	 * All (known) classes and members are fully available at this point.
	 */
	private void processClassB(ClassInstance cls) {
		assert cls.initStep == 1;

		for (MethodInstance method : cls.methods) {
			processMethodInsns(method);
		}

		cls.initStep = 2;
	}

	private void processMethodInsns(MethodInstance method) {
		if (!method.isReal()) { // artificial method to capture calls to types with incomplete/unknown hierarchy/super type method info
			logger.debug("Skipping empty method {}", method);
			return;
		}

		for (Iterator<AbstractInsnNode> it = method.getAsmNode().instructions.iterator(); it.hasNext(); ) {
			AbstractInsnNode ain = it.next();

			switch (ain.getType()) {
			case AbstractInsnNode.METHOD_INSN: {
				MethodInsnNode in = (MethodInsnNode) ain;
				handleMethodInvocation(method,
						in.owner, in.name, in.desc,
						Util.isCallToInterface(in), ain.getOpcode() == Opcodes.INVOKESTATIC);
				break;
			}
			case AbstractInsnNode.FIELD_INSN: {
				FieldInsnNode in = (FieldInsnNode) ain;
				ClassInstance owner = getCreateClassInstance(ClassInstance.getId(in.owner));
				FieldInstance dst = owner.resolveField(in.name, in.desc);

				if (dst == null) { // unknown field, create a synthetic one
					dst = new FieldInstance(owner, in.name, in.desc, ain.getOpcode() == Opcodes.GETSTATIC || ain.getOpcode() == Opcodes.PUTSTATIC);
					owner.addField(dst);
				}

				if (ain.getOpcode() == Opcodes.GETSTATIC || ain.getOpcode() == Opcodes.GETFIELD) {
					dst.readRefs.add(method);
					method.fieldReadRefs.add(dst);
				} else {
					dst.writeRefs.add(method);
					method.fieldWriteRefs.add(dst);
				}

				dst.cls.methodTypeRefs.add(method);
				method.classRefs.add(dst.cls);

				break;
			}
			case AbstractInsnNode.TYPE_INSN: {
				TypeInsnNode tin = (TypeInsnNode) ain;
				ClassInstance dst = getCreateClassInstance(ClassInstance.getId(tin.desc));

				dst.methodTypeRefs.add(method);
				method.classRefs.add(dst);

				break;
			}
			case AbstractInsnNode.INVOKE_DYNAMIC_INSN: {
				InvokeDynamicInsnNode in = (InvokeDynamicInsnNode) ain;
				Handle impl = Util.getTargetHandle(in.bsm, in.bsmArgs);
				if (impl == null) break;

				switch (impl.getTag()) {
				case Opcodes.H_INVOKEVIRTUAL:
				case Opcodes.H_INVOKESTATIC:
				case Opcodes.H_INVOKESPECIAL:
				case Opcodes.H_NEWINVOKESPECIAL:
				case Opcodes.H_INVOKEINTERFACE:
					handleMethodInvocation(method,
							impl.getOwner(), impl.getName(), impl.getDesc(),
							Util.isCallToInterface(impl), impl.getTag() == Opcodes.H_INVOKESTATIC);
					break;
				default:
					logger.warn("Unexpected impl tag: {}", impl.getTag());
				}

				break;
			}
			}
		}
	}

	private void handleMethodInvocation(MethodInstance method, String rawOwner, String name, String desc, boolean toInterface, boolean isStatic) {
		MethodInstance dst = resolveMethod(rawOwner, name, desc, toInterface, isStatic, true);

		dst.refsIn.add(method);
		method.refsOut.add(dst);
		dst.cls.methodTypeRefs.add(method);
		method.classRefs.add(dst.cls);
	}

	private MethodInstance resolveMethod(String owner, String name, String desc, boolean toInterface, boolean isStatic, boolean create) {
		ClassInstance cls = getCreateClassInstance(ClassInstance.getId(owner), create);
		if (cls == null) return null;

		MethodInstance ret = cls.resolveMethod(name, desc, toInterface);

		if (ret == null && create) {
			logger.trace("Creating synthetic method {}/{}{}", owner, name, desc);

			ret = new MethodInstance(cls, name, desc, isStatic);
			cls.addMethod(ret);
		}

		return ret;
	}

	private MethodInstance resolveMethod(MethodInsnNode in) {
		return resolveMethod(in.owner, in.name, in.desc,
				Util.isCallToInterface(in), in.getOpcode() == Opcodes.INVOKESTATIC, false);
	}

	private MethodInstance resolveMethod(Handle handle) {
		return resolveMethod(handle.getOwner(), handle.getName(), handle.getDesc(),
				Util.isCallToInterface(handle), handle.getTag() == Opcodes.H_INVOKESTATIC, false);
	}

	/**
	 * 3rd processing pass, determine same hierarchy methods.
	 */
	private static void processClassC(ClassInstance cls) {
		assert cls.initStep == 2;
		cls.initStep = 3;

		/* Determine which methods share the same hierarchy by grouping all methods within a
		 * bottom-up class hierarchy by id.
		 *
		 * Methods are part of the same hierarchy if:
		 * - their id matches
		 * - neither is private or static
		 * - every methods's owner is part of a set of 2+ classes/interfaces where a class or
		 *   interface exists that is assignable to them
		 * - all of these owner sets are linked by sharing a class/interface (potentially indirectly) */
		if (!cls.childClasses.isEmpty() || !cls.implementers.isEmpty()) return; // visiting only classes that aren't being extended is sufficient to visit every method

		Map<String, MethodInstance> methods = new HashMap<>();
		Queue<ClassInstance> toCheck = new ArrayDeque<>();
		toCheck.add(cls);

		while ((cls = toCheck.poll()) != null) {
			for (MethodInstance method : cls.methods) {
				MethodInstance prev;

				if (isHierarchyBarrier(method)) {
					if (method.hierarchyData == null) {
						method.hierarchyData = new MemberHierarchyData<>(Collections.singleton(method), method.nameObfuscatedLocal);
					}
				} else if ((prev = methods.get(method.id)) != null) {
					if (method.hierarchyData == null) {
						method.hierarchyData = prev.hierarchyData;
						method.hierarchyData.addMember(method);
					} else if (method.hierarchyData != prev.hierarchyData) {
						for (MethodInstance m : prev.hierarchyData.getMembers()) {
							method.hierarchyData.addMember(m);
							m.hierarchyData = method.hierarchyData;
						}
					}
				} else {
					methods.put(method.id, method);

					if (method.hierarchyData == null) {
						method.hierarchyData = new MemberHierarchyData<>(Util.newIdentityHashSet(), method.nameObfuscatedLocal);
						method.hierarchyData.addMember(method);
					}
				}

				assert method.hierarchyData != null;
			}

			if (cls.superClass != null) toCheck.add(cls.superClass);
			toCheck.addAll(cls.interfaces);
		}
	}

	private static boolean isHierarchyBarrier(MethodInstance method) {
		return (method.getAccess() & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) != 0;
	}

	/**
	 * 4th processing pass, child<->parent relation and in depth analysis.
	 */
	private void processClassD(ClassInstance cls, CommonClasses common) {
		assert cls.initStep == 3;

		Queue<ClassInstance> toCheck = new ArrayDeque<>();
		Set<ClassInstance> checked = Util.newIdentityHashSet();
		Set<MemberHierarchyData<MethodInstance>> nameObfChecked = Util.newIdentityHashSet();

		for (MethodInstance method : cls.getMethods()) {
			assert method.hierarchyData != null;

			if (method.hierarchyData.hasMultipleMembers()) { // may have parent/child methods
				determineMethodRelations(method, toCheck, checked);

				// update name obfuscated state if not done yet, the name is only obfuscated if it is for all hierarchy members
				if (nameObfChecked.add(method.hierarchyData) && method.hierarchyData.nameObfuscated) {
					for (MethodInstance m : method.hierarchyData.getMembers()) {
						if (!m.nameObfuscatedLocal) {
							method.hierarchyData.nameObfuscated = false;
							break;
						}
					}
				}
			}

			determineMethodType(method);
			//Analysis.analyzeMethod(method, common);
		}

		for (FieldInstance field : cls.getFields()) {
			field.hierarchyData = new MemberHierarchyData<>(Collections.singleton(field), field.nameObfuscatedLocal);

			if (field.writeRefs.size() == 1) {
				Analysis.checkInitializer(field, this);
			}
		}

		cls.initStep = 4;
	}

	private static void determineMethodRelations(MethodInstance method, Queue<ClassInstance> toCheck, Set<ClassInstance> checked) {
		if (method.origName.equals("<init>") || method.origName.equals("<clinit>")) return;
		if (isHierarchyBarrier(method)) return;

		if (method.cls.superClass != null) toCheck.add(method.cls.superClass);
		toCheck.addAll(method.cls.interfaces);
		ClassInstance cls;

		while ((cls = toCheck.poll()) != null) {
			if (!checked.add(cls)) continue;

			MethodInstance m = cls.getMethod(method.id);

			if (m != null && !isHierarchyBarrier(m)) { // skips over private or static methods
				method.addParent(m);
				m.addChild(method);
			} else {
				if (cls.superClass != null) toCheck.add(cls.superClass);
				toCheck.addAll(cls.interfaces);
			}
		}

		checked.clear();
	}

	private void determineMethodType(MethodInstance method) {
		MethodType type;

		if (method.getId().startsWith("<clinit>")) {
			type = MethodType.CLASS_INIT;
		} else if (method.getId().startsWith("<init>")) {
			type = MethodType.CONSTRUCTOR;
		} else if (isLambdaMethod(method)) {
			type = MethodType.LAMBDA_IMPL;
		} else {
			type = MethodType.OTHER;
		}

		method.type = type;
	}

	private boolean isLambdaMethod(MethodInstance method) {
		if (!method.isSynthetic() || !method.isPrivate() || method.refsIn.isEmpty()) return false;

		for (MethodInstance m : method.refsIn) {
			boolean found = false;

			for (Iterator<AbstractInsnNode> it = m.getAsmNode().instructions.iterator(); it.hasNext(); ) {
				AbstractInsnNode ain = it.next();

				switch (ain.getType()) {
				case AbstractInsnNode.METHOD_INSN:
					if (resolveMethod((MethodInsnNode) ain) == method) return false;
					break;
				case AbstractInsnNode.INVOKE_DYNAMIC_INSN: {
					InvokeDynamicInsnNode in = (InvokeDynamicInsnNode) ain;
					Handle impl = Util.getTargetHandle(in.bsm, in.bsmArgs);
					if (impl == null) break;

					if (resolveMethod(impl) == method) {
						if (Util.isJavaLambdaMetafactory(in.bsm)) {
							found = true;
						} else {
							return false;
						}
					}

					break;
				}
				}
			}

			if (!found) return false;
		}

		return true;
	}

	/**
	 * 5th processing pass, assign temporary names.
	 */
	private void processClassE(ClassInstance cls, int clsIndex, AtomicInteger vmIdx) {
		assert cls.initStep == 4;

		/* Assign each class+member a contextually unique name in the form <type><env><id>
		 * where <type> is c for class, m for method, vm for virtual method and f for field,
		 * <env> is a for envA and b for envB and <id> is an integer id.
		 *
		 * The purpose is to make it easy to tell if a referenced class/member on side B is the
		 * same as on side A if it is already matched. It also avoids name conflicts from
		 * overwriting identifiers in side B with the matched non-namespaced names of side A.*/

		String envName = this == env.getEnvA() ? "a" : "b";

		if (cls.isNameObfuscated()) {
			assert clsIndex >= 0;
			cls.setTmpName("c"+envName+clsIndex);
		}

		int memberIndex = 0;

		for (MethodInstance method : cls.getMethods()) {
			if (!method.isNameObfuscated()) continue;

			assert !method.getName().equals("<init>") && !method.getName().equals("<clinit>");

			if (method.getAllHierarchyMembers().size() == 1) {
				method.setTmpName("m"+envName+memberIndex);
				memberIndex++;
			} else if (!method.hasLocalTmpName()) {
				String name = "vm"+envName+vmIdx.getAndIncrement();
				method.setTmpName(name);
			}
		}

		memberIndex = 0;

		for (FieldInstance field : cls.getFields()) {
			if (!field.isNameObfuscated()) continue;

			assert field.getAllHierarchyMembers().size() == 1;

			field.setTmpName("f"+envName+memberIndex);
			memberIndex++;
		}

		cls.initStep = 5;
	}

	@Override
	public boolean isShared() {
		return false;
	}

	@Override
	public ClassInstance getClsById(String id) {
		assert !id.isEmpty();

		if (id.charAt(id.length() - 1) == ';') { // no local primitives or primitive arrays
			ClassInstance ret = getLocalClsById(id);
			if (ret != null) return ret;
		}

		return env.getSharedClsById(id);
	}

	@Override
	public ClassInstance getLocalClsById(String id) {
		if (id.isEmpty()) throw new IllegalArgumentException("empty class name");
		assert id.charAt(id.length() - 1) == ';' : id;

		if (id.charAt(0) == '[') { // array class
			return arrayClasses.get(id);
		} else {
			return classes.get(id);
		}
	}

	@Override
	public ClassInstance getClsById(String id, NameType nameType) {
		if (nameType != NameType.PLAIN && id.charAt(id.length() - 1) == ';') { // no local primitives or primitive arrays
			if (id.charAt(0) == '[') {
				int start = 1;
				while (id.charAt(start) == '[') start++;
				assert id.charAt(start) == 'L';

				int reqNameLen = id.length() - 2 - start;

				for (ClassInstance cls : arrayClasses.values()) {
					String name = cls.getName(nameType);
					if (name == null || name.length() != reqNameLen) continue;

					if (id.startsWith(name, start + 1)) return cls;
				}
			} else {
				assert id.charAt(0) == 'L';
				int reqNameLen = id.length() - 2;

				for (ClassInstance cls : classes.values()) {
					String name = cls.getName(nameType);
					if (name == null || name.length() != reqNameLen) continue;

					if (id.startsWith(name, 1)) return cls;
				}
			}
		}

		return getClsById(id);
	}

	@Override
	public ClassInstance getCreateClassInstance(String id, boolean createUnknown) {
		if (id.length() == 0) throw new IllegalArgumentException("empty class desc");
		assert id.length() == 1 || id.charAt(id.length() - 1) == ';' || id.charAt(0) == '[' && id.lastIndexOf('[') == id.length() - 2 : id;

		ClassInstance ret;

		if (id.charAt(0) == '[') { // array type
			if ((ret = arrayClasses.get(id)) != null) return ret;
			if ((ret = env.getSharedClsById(id)) != null) return ret;

			ClassInstance elementClass = ClassEnvironment.getArrayCls(this, id);
			ClassInstance cls = new ClassInstance(id, elementClass);

			if (elementClass.isShared()) {
				ret = env.addSharedCls(cls);
			} else {
				ret = arrayClasses.putIfAbsent(id, cls);
				if (ret == null) ret = cls;
			}

			if (ret == cls) { // cls was added
				ClassEnvironment.addSuperClass(ret, "java/lang/Object");
			}
		} else {
			if ((ret = classes.get(id)) != null) return ret;

			// try shared non-artificial class
			ClassInstance sharedRet = env.getSharedClsById(id);
			if (sharedRet != null && sharedRet.isReal()) return sharedRet;

			// try reading class from class path
			if ((ret = createClassPathClass(id)) != null) return ret;

			// try shared artificial class
			if (sharedRet != null) return sharedRet;

			// create shared missing class
			//ret = env.getMissingCls(id, createUnknown);

			// try shared jvm-cp class
			if ((ret = env.getMissingCls(id, false)) != null || !createUnknown) return ret;

			// create local artificial class
			ret = new ClassInstance(id, this);
			classes.put(id, ret);
		}

		return ret;
	}

	private ClassInstance createClassPathClass(String id) {
		if (classPathIndex.isEmpty()) return null;
		if (id.length() <= 1) return null; // primitive

		String name = ClassInstance.getName(id);
		Path file = classPathIndex.get(name);
		if (file == null) return null;

		ClassNode cn = ClassEnvironment.readClass(file, false);
		ClassInstance cls = new ClassInstance(ClassInstance.getId(cn.name), ClassEnvironment.getContainingUri(file.toUri(), cn.name), this, cn);
		if (!cls.getId().equals(id)) throw new RuntimeException("mismatched cls id "+id+" for "+file+", expected "+name);

		ClassInstance prev = classes.putIfAbsent(cls.getId(), cls);
		assert prev == null;

		if (initStep > 0) ClassEnvironment.processClassA(cls, null);
		if (initStep > 1) pendingInit.add(cls);

		return cls;
	}

	@Override
	public ClassEnvironment getGlobal() {
		return env;
	}

	@Override
	public ClassEnv getOther() {
		return this == env.getEnvA() ? env.getEnvB() : env.getEnvA();
	}

	private static final Logger logger = LoggerFactory.getLogger(ClassFeatureExtractor.class);
	final ClassEnvironment env;
	private final List<InputFile> inputFiles = new ArrayList<>();
	private final List<InputFile> cpFiles = new ArrayList<>();
	private final Map<String, Path> classPathIndex = new HashMap<>();
	private final Map<String, ClassInstance> classes = new HashMap<>();
	private final Map<String, ClassInstance> roClasses = Collections.unmodifiableMap(classes);
	private final Map<String, ClassInstance> arrayClasses = new HashMap<>();

	private int initStep;
	private final List<ClassInstance> pendingInit = new ArrayList<>();
}
