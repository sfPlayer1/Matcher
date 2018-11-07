package matcher.type;

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

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import matcher.Util;
import matcher.bcremap.AsmClassRemapper;
import matcher.bcremap.AsmRemapper;
import matcher.type.Analysis.CommonClasses;

public class ClassFeatureExtractor implements IClassEnv {
	public ClassFeatureExtractor(ClassEnvironment env) {
		this.env = env;
	}

	public void processInputs(Collection<Path> inputs) {
		Set<Path> uniqueInputs = new LinkedHashSet<>(inputs);

		for (Path archive : uniqueInputs) {
			inputFiles.add(new InputFile(archive));

			Util.iterateJar(archive, true, file -> {
				ClassInstance cls = readClass(file, ClassFeatureExtractor::isNameObfuscated);
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

			env.addOpenFileSystem(Util.iterateJar(archive, false, file -> {
				String name = file.toAbsolutePath().toString();
				if (!name.startsWith("/") || !name.endsWith(".class") || name.startsWith("//")) throw new RuntimeException("invalid path: "+archive+" ("+name+")");
				name = name.substring(1, name.length() - ".class".length());

				if (!checkExisting || getLocalClsByName(name) == null && env.getSharedClassLocation(name) == null && env.getLocalClsByName(name) == null) {
					classPathIndex.putIfAbsent(name, file);

					/*ClassNode cn = readClass(file);
					addSharedCls(new ClassInstance(ClassInstance.getId(cn.name), file.toUri(), cn));*/
				}
			}));
		}
	}

	private static boolean isNameObfuscated(ClassNode cn) {
		return true;
	}

	private ClassInstance readClass(Path path, Predicate<ClassNode> nameObfuscated) {
		ClassNode cn = ClassEnvironment.readClass(path);

		return new ClassInstance(ClassInstance.getId(cn.name), path.toUri(), this, cn, nameObfuscated.test(cn));
	}

	private static void mergeClasses(ClassInstance from, ClassInstance to) {
		assert from.getAsmNodes().length == 1;

		to.addAsmNode(from.getAsmNodes()[0]);
	}

	public void process() {
		ClassInstance clo = getCreateClassInstance("Ljava/lang/Object;");
		assert clo != null && clo.getAsmNodes() != null;

		initStep++;
		List<ClassInstance> initialClasses = new ArrayList<>(classes.values());

		for (ClassInstance cls : initialClasses) {
			ClassEnvironment.processClassA(cls);
		}

		initStep++;
		initialClasses.clear();
		initialClasses.addAll(classes.values());
		assert initialClasses.size() == new HashSet<>(initialClasses).size();

		for (ClassInstance cls : initialClasses) {
			processClassB(cls);
		}

		initStep++;
		initialClasses.clear();
		initialClasses.addAll(classes.values());

		for (ClassInstance cls : initialClasses) {
			processClassC(cls);
		}

		initStep++;
		initialClasses.clear();
		initialClasses.addAll(classes.values());

		CommonClasses common = new CommonClasses(this);

		for (ClassInstance cls : initialClasses) {
			processClassD(cls, common);
		}

		initStep++;

		int clsIdx = 0;
		AtomicInteger vmIdx = new AtomicInteger();

		for (ClassInstance cls : initialClasses) {
			if (cls.getUri() == null || !cls.isInput()) continue;

			int curClsIdx = cls.nameObfuscated ? clsIdx++ : -1;

			processClassE(cls, curClsIdx, vmIdx);
		}

		initStep++;
	}

	public void reset() {
		inputFiles.clear();
		cpFiles.clear();
		classPathIndex.clear();
		classes.clear();
		arrayClasses.clear();
	}

	public Map<String, ClassInstance> getClasses() {
		return roClasses;
	}

	public Collection<InputFile> getInputFiles() {
		return inputFiles;
	}

	public List<InputFile> getClassPathFiles() {
		return cpFiles;
	}

	/**
	 * 2nd class processing pass, inter-member initialization.
	 *
	 * All (known) classes and members are fully available at this point.
	 */
	private void processClassB(ClassInstance cls) {
		for (MethodInstance method : cls.methods) {
			processMethodInsns(method);
		}
	}

	private void processMethodInsns(MethodInstance method) {
		if (method.asmNode == null) { // artificial method to capture calls to types with incomplete/unknown hierarchy/super type method info
			System.out.println("skipping empty method "+method);
			return;
		}

		for (Iterator<AbstractInsnNode> it = method.asmNode.instructions.iterator(); it.hasNext(); ) {
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
					System.out.println("unexpected impl tag: "+impl.getTag());
				}

				break;
			}
			}
		}
	}

	private void handleMethodInvocation(MethodInstance method, String rawOwner, String name, String desc, boolean toInterface, boolean isStatic) {
		ClassInstance owner = getCreateClassInstance(ClassInstance.getId(rawOwner));
		MethodInstance dst = owner.resolveMethod(name, desc, toInterface);

		if (dst == null) { // presumably a method in (super)type missing from the configured class path
			System.out.println("creating synthetic method "+rawOwner+"/"+name+desc);

			dst = new MethodInstance(owner, name, desc, isStatic);
			owner.addMethod(dst);
		}

		dst.refsIn.add(method);
		method.refsOut.add(dst);
		dst.cls.methodTypeRefs.add(method);
		method.classRefs.add(dst.cls);
	}

	/**
	 * 3rd processing pass, determine parent/child methods
	 */
	private static void processClassC(ClassInstance cls) {
		Queue<ClassInstance> toCheck = new ArrayDeque<>();
		Set<ClassInstance> checked = Util.newIdentityHashSet();

		for (MethodInstance method : cls.methods) {
			processMethod(method, toCheck, checked);
			toCheck.clear();
			checked.clear();
		}
	}

	private static void processMethod(MethodInstance method, Queue<ClassInstance> toCheck, Set<ClassInstance> checked) {
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
	}

	private static boolean isHierarchyBarrier(MethodInstance method) {
		return (method.getAccess() & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) != 0;
	}

	/**
	 * 4th processing pass, hierarchy and in depth analysis.
	 */
	private void processClassD(ClassInstance cls, CommonClasses common) {
		Queue<MethodInstance> toCheck = new ArrayDeque<>();
		Set<MethodInstance> checked = Util.newIdentityHashSet();

		for (MethodInstance method : cls.getMethods()) {
			if (isHierarchyBarrier(method)) {
				method.hierarchyMembers = Collections.singleton(method);
			} else {
				processVirtuakMethodHierarchy(method, toCheck, checked);
				toCheck.clear();
				checked.clear();
			}

			//Analysis.analyzeMethod(method, common);
		}

		for (FieldInstance field : cls.getFields()) {
			field.hierarchyMembers = Collections.singleton(field);

			if (field.writeRefs.size() == 1) {
				Analysis.checkInitializer(field, this);
			}
		}
	}

	private void processVirtuakMethodHierarchy(MethodInstance method, Queue<MethodInstance> toCheck, Set<MethodInstance> checked) {
		assert method.getCls().getEnv() == this;

		if (method.hierarchyMembers != null) return; // already processed

		toCheck.add(method);
		checked.add(method);

		boolean nameObf = true;

		while ((method = toCheck.poll()) != null) {
			if (method.hierarchyMembers != null) {
				checked.addAll(method.hierarchyMembers);
			} else {
				for (MethodInstance m : method.getParents()) {
					if (checked.add(m)) toCheck.add(m);
				}

				for (MethodInstance m : method.getChildren()) {
					if (checked.add(m)) toCheck.add(m);
				}
			}

			nameObf &= method.nameObfuscated;
		}

		Set<MethodInstance> hierarchyMembers = Util.newIdentityHashSet(checked);

		for (MethodInstance m : checked) {
			m.hierarchyMembers = hierarchyMembers;
			m.nameObfuscated &= nameObf;
		}
	}

	/**
	 * 5th processing pass, assign temporary names.
	 */
	private void processClassE(ClassInstance cls, int clsIndex, AtomicInteger vmIdx) {
		/* Assign each class+member a contextually unique name in the form <type><env><id>
		 * where <type> is c for class, m for method, vm for virtual method and f for field,
		 * <env> is a for envA and b for envB and <id> is an integer id.
		 *
		 * The purpose is to make it easy to tell if a referenced class/member on side B is the
		 * same as on side A if it is already matched. It also avoids name conflicts from
		 * overwriting identifiers in side B with the matched non-namespaced names of side A.*/

		String envName = this == env.getEnvA() ? "a" : "b";

		if (cls.isNameObfuscated(false)) {
			assert clsIndex >= 0;
			cls.setTmpName("c"+envName+clsIndex);
		}

		int memberIndex = 0;

		for (MethodInstance method : cls.getMethods()) {
			if (!method.isNameObfuscated(false)) continue;

			assert !method.getName().equals("<init>") && !method.getName().equals("<clinit>");

			if (method.getAllHierarchyMembers().size() == 1) {
				method.setTmpName("m"+envName+memberIndex);
				memberIndex++;
			} else if (method.getTmpName(true) == null) {
				String name = "vm"+envName+vmIdx.getAndIncrement();

				for (MethodInstance m : method.getAllHierarchyMembers()) {
					m.setTmpName(name);
				}
			}
		}

		memberIndex = 0;

		for (FieldInstance field : cls.getFields()) {
			if (!field.isNameObfuscated(false)) continue;

			assert field.getAllHierarchyMembers().size() == 1;

			field.setTmpName("f"+envName+memberIndex);
			memberIndex++;
		}
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
	public ClassInstance getClsById(String id, boolean mapped, boolean tmpNamed, boolean unmatchedTmp) {
		if (mapped && tmpNamed && id.charAt(id.length() - 1) == ';') { // no local primitives or primitive arrays
			if (id.charAt(0) == '[') {
				int start = 1;
				while (id.charAt(start) == '[') start++;
				assert id.charAt(start) == 'L';

				int reqNameLen = id.length() - 2 - start;

				for (ClassInstance cls : arrayClasses.values()) {
					String name = cls.getName(mapped, tmpNamed, unmatchedTmp);
					if (name.length() != reqNameLen) continue;

					if (id.startsWith(name, start + 1)) return cls;
				}
			} else {
				assert id.charAt(0) == 'L';
				int reqNameLen = id.length() - 2;

				for (ClassInstance cls : classes.values()) {
					String name = cls.getName(mapped, tmpNamed, unmatchedTmp);
					if (name.length() != reqNameLen) continue;

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
			if ((ret = env.getSharedClsById(id)) != null) return ret;
			if ((ret = createClassPathClass(id)) != null) return ret;

			ret = env.getMissingCls(id, createUnknown);
		}

		return ret;
	}

	private ClassInstance createClassPathClass(String id) {
		if (classPathIndex.isEmpty()) return null;
		if (id.length() <= 1) return null; // primitive

		String name = ClassInstance.getName(id);
		Path file = classPathIndex.get(name);
		if (file == null) return null;

		ClassNode cn = ClassEnvironment.readClass(file);
		ClassInstance cls = new ClassInstance(ClassInstance.getId(cn.name), file.toUri(), this, cn);
		if (!cls.getId().equals(id)) throw new RuntimeException("mismatched cls id "+id+" for "+file+", expected "+name);

		ClassInstance prev = classes.putIfAbsent(cls.getId(), cls);
		assert prev == null;

		if (initStep > 0) ClassEnvironment.processClassA(cls);
		if (initStep > 1) processClassB(cls);
		if (initStep > 2) processClassC(cls);
		if (initStep > 3) processClassD(cls, new CommonClasses(this));

		return cls;
	}

	@Override
	public ClassEnvironment getGlobal() {
		return env;
	}

	public byte[] serializeClass(ClassInstance cls, boolean mapped, boolean tmpNamed, boolean unmatchedTmp) {
		ClassNode cn = cls.getMergedAsmNode();
		if (cn == null) throw new IllegalArgumentException("cls without asm node: "+cls);

		ClassWriter writer = new ClassWriter(0);

		synchronized (Util.asmNodeSync) {
			if (mapped || tmpNamed) {
				AsmClassRemapper.process(cn, new AsmRemapper(this, mapped, tmpNamed, unmatchedTmp), writer);
			} else {
				cn.accept(writer);
			}
		}

		return writer.toByteArray();
	}

	final ClassEnvironment env;
	private final List<InputFile> inputFiles = new ArrayList<>();
	private final List<InputFile> cpFiles = new ArrayList<>();
	private final Map<String, Path> classPathIndex = new HashMap<>();
	private final Map<String, ClassInstance> classes = new HashMap<>();
	private final Map<String, ClassInstance> roClasses = Collections.unmodifiableMap(classes);
	private final Map<String, ClassInstance> arrayClasses = new HashMap<>();

	private int initStep;
}
