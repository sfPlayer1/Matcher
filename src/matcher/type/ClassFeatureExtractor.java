package matcher.type;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import matcher.AsmClassRemapper;
import matcher.AsmRemapper;
import matcher.Matcher;
import matcher.Util;

public class ClassFeatureExtractor {
	public ClassFeatureExtractor(Matcher matcher) {
		this.matcher = matcher;
	}

	public void processInputs(Collection<Path> inputs) {
		Set<Path> uniqueInputs = new LinkedHashSet<>(inputs);

		for (Path archive : uniqueInputs) {
			inputFiles.add(new InputFile(archive));

			Util.iterateJar(archive, true, file -> {
				ClassInstance cls = readClass(file, ClassFeatureExtractor::isNameObfuscated);
				classes.putIfAbsent(cls.getId(), cls);
			});
		}
	}

	private static boolean isNameObfuscated(ClassNode cn) {
		return true;
	}

	private ClassInstance readClass(Path path, Predicate<ClassNode> nameObfuscated) {
		try {
			ClassReader reader = new ClassReader(Files.readAllBytes(path));
			ClassNode cn = new ClassNode();
			reader.accept(cn, ClassReader.EXPAND_FRAMES);

			return new ClassInstance(getClassDesc(cn.name), path.toUri(), cn, nameObfuscated.test(cn));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void process() {
		List<ClassInstance> initialClasses = new ArrayList<>(classes.values());

		for (ClassInstance cls : initialClasses) {
			processClassA(cls);
		}

		for (ClassInstance cls : initialClasses) {
			processClassB(cls);
		}

		for (ClassInstance cls : initialClasses) {
			processClassC(cls);
		}
	}

	public void reset() {
		inputFiles.clear();
		classes.clear();
		arrayClasses.clear();
	}

	public Map<String, ClassInstance> getClasses() {
		return classes;
	}

	public Collection<InputFile> getInputFiles() {
		return inputFiles;
	}

	/**
	 * 1st class processing pass, member and class hierarchy initialization.
	 *
	 * Only the (known) classes are fully available at this point.
	 */
	private void processClassA(ClassInstance cls) {
		ClassNode cn = cls.asmNode;

		for (int i = 0; i < cn.methods.size(); i++) {
			MethodNode mn = cn.methods.get(i);
			cls.addMethod(new MethodInstance(cls, mn.name, mn.desc, mn, i, this));
		}

		for (int i = 0; i < cn.fields.size(); i++) {
			FieldNode fn = cn.fields.get(i);
			cls.addField(new FieldInstance(cls, fn.name, fn.desc, fn, i, this));
		}

		detectOuterClass(cls);

		if (cn.superName != null) {
			addSuperClass(cls, cn.superName);
		}

		for (String iface : cn.interfaces) {
			ClassInstance ifCls = getCreateClassInstance(getClassDesc(iface));
			cls.interfaces.add(ifCls);
			ifCls.implementers.add(cls);
		}
	}

	private void detectOuterClass(ClassInstance cls) {
		ClassNode cn = cls.getAsmNode();

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

			if ((pos = cn.name.lastIndexOf('$')) != -1 && pos < cn.name.length() - 1) {
				addOuterClass(cls, cn.name.substring(0, pos), false);
			}
		}
	}

	private void addOuterClass(ClassInstance cls, String name, boolean createUnknown) {
		ClassInstance outerClass = getClassInstance(name);

		if (outerClass == null) {
			outerClass = getCreateClassInstance(getClassDesc(name), createUnknown);
			if (outerClass == null) throw new IllegalStateException("missing outer cls: "+name+" for "+cls);
		}

		cls.outerClass = outerClass;
		outerClass.innerClasses.add(cls);
	}

	private void addSuperClass(ClassInstance cls, String name) {
		cls.superClass = getCreateClassInstance(getClassDesc(name));
		cls.superClass.childClasses.add(cls);
	}

	/**
	 * 2nd class processing pass, inter-member initialization.
	 *
	 * All (known) classes and members are fully available at this point.
	 */
	private void processClassB(ClassInstance cls) {
		Queue<ClassInstance> toCheck = new ArrayDeque<>();
		Set<ClassInstance> checked = Util.newIdentityHashSet();

		for (MethodInstance method : cls.methods) {
			processMethod(method, toCheck, checked);
			toCheck.clear();
			checked.clear();
		}

		for (FieldInstance field : cls.fields) {
			processField(field, toCheck, checked);
			toCheck.clear();
			checked.clear();
		}
	}

	private void processMethod(MethodInstance method, Queue<ClassInstance> toCheck, Set<ClassInstance> checked) {
		if (method.cls.superClass != null) toCheck.add(method.cls.superClass);
		toCheck.addAll(method.cls.interfaces);
		ClassInstance cls;

		while ((cls = toCheck.poll()) != null) {
			if (!checked.add(cls)) continue;

			MethodInstance m = cls.getMethod(method.id);

			if (m != null) {
				method.parent = m;
				m.children.add(method);
			} else {
				if (cls.superClass != null) toCheck.add(cls.superClass);
				toCheck.addAll(cls.interfaces);
			}
		}

		if (method.asmNode == null) { // artificial method to capture calls to types with incomplete/unknown hierarchy/super type method info
			System.out.println("skipping empty method "+method);
			return;
		}

		for (Iterator<AbstractInsnNode> it = method.asmNode.instructions.iterator(); it.hasNext(); ) {
			AbstractInsnNode ain = it.next();

			switch (ain.getType()) {
			case AbstractInsnNode.METHOD_INSN: {
				MethodInsnNode min = (MethodInsnNode) ain;
				ClassInstance owner = getCreateClassInstance(getClassDesc(min.owner));
				MethodInstance dst = owner.resolveMethod(min.name, min.desc);

				if (dst == null) {
					dst = new MethodInstance(owner, min.name, min.desc, null, -1, this);
					owner.addMethod(dst);
				}

				dst.refsIn.add(method);
				method.refsOut.add(dst);
				dst.cls.methodTypeRefs.add(method);
				method.classRefs.add(dst.cls);

				break;
			}
			case AbstractInsnNode.FIELD_INSN: {
				FieldInsnNode fin = (FieldInsnNode) ain;
				ClassInstance owner = getCreateClassInstance(getClassDesc(fin.owner));
				FieldInstance dst = owner.resolveField(fin.name, fin.desc);

				if (dst == null) {
					dst = new FieldInstance(owner, fin.name, fin.desc, null, -1, this);
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
				ClassInstance dst = getCreateClassInstance(getClassDesc(tin.desc));

				dst.methodTypeRefs.add(method);
				method.classRefs.add(dst);
			}
			}
		}
	}

	private void processField(FieldInstance field, Queue<ClassInstance> toCheck, Set<ClassInstance> checked) {
		if (field.cls.superClass != null) toCheck.add(field.cls.superClass);
		toCheck.addAll(field.cls.interfaces);
		ClassInstance cls;

		while ((cls = toCheck.poll()) != null) {
			if (!checked.add(cls)) continue;

			FieldInstance f = cls.getField(field.id);

			if (f != null) {
				field.parent = f;
				f.children.add(field);
			} else {
				if (cls.superClass != null) toCheck.add(cls.superClass);
				toCheck.addAll(cls.interfaces);
			}
		}
	}

	/**
	 * 3rd processing pass, in depth analysis.
	 */
	void processClassC(ClassInstance cls) {
		/*for (FieldInstance field : cls.getFields()) {
			if (field.writeRefs.size() == 1) {
				Analysis.checkInitializer(field, this);
			}
		}*/
	}

	private static String getClassDesc(String name) {
		if (name.isEmpty()) throw new IllegalArgumentException("empty class name");

		if (name.charAt(0) == '[') {
			int arrayEnd = name.lastIndexOf('[');

			if (name.length() == arrayEnd + 1 || name.length() > arrayEnd + 2 && name.charAt(name.length() - 1) != ';') {
				throw new IllegalArgumentException("invalid class name: "+name);
			}

			return name;
		} else if (name.charAt(name.length() - 1) == ';') {
			throw new IllegalArgumentException("invalid class name: "+name);
		}

		return "L"+name+";";
	}

	public ClassInstance getClassInstance(String name) {
		if (name.isEmpty()) throw new IllegalArgumentException("empty class name");

		if (name.charAt(0) == '[') { // array class
			return arrayClasses.get(name);
		} else {
			if (name.charAt(name.length() - 1) == ';') throw new IllegalArgumentException("invalid class name: "+name);

			return classes.get(getClassDesc(name));
		}
	}

	ClassInstance getCreateClassInstance(String id) {
		return getCreateClassInstance(id, true);
	}

	ClassInstance getCreateClassInstance(String id, boolean createUnknown) {
		if (id.length() == 0) throw new IllegalArgumentException("empty class desc");
		if (id.length() > 1 && id.charAt(0) != '[' && (id.charAt(0) != 'L' || id.charAt(id.length() - 1) != ';')) throw new IllegalArgumentException("invalid class id: "+id);

		ClassInstance ret;

		if (id.charAt(0) == '[') { // array type
			if ((ret = matcher.getSharedCls(id)) != null) return ret;
			if ((ret = arrayClasses.get(id)) != null) return ret;

			String elementId = id.substring(id.lastIndexOf('[') + 1);
			if (elementId.isEmpty()) throw new IllegalArgumentException("invalid class desc: "+id);

			ClassInstance elementClass = getCreateClassInstance(elementId);
			ClassInstance cls = new ClassInstance(id, elementClass);

			if (elementClass.isShared()) {
				ret = matcher.addSharedCls(cls);
			} else {
				ret = arrayClasses.putIfAbsent(id, cls);
				if (ret == null) ret = cls;
			}

			if (ret == cls) {
				addSuperClass(ret, "java/lang/Object");
			}
		} else {
			ret = classes.get(id);
			if (ret != null) return ret;

			ret = matcher.getSharedCls(id);
			if (ret != null) return ret;

			ret = getMissingCls(id, createUnknown);
		}

		return ret;
	}

	private ClassInstance getMissingCls(String id, boolean createUnknown) {
		if (id.length() > 1) {
			String name = id.substring(1, id.length() - 1);
			Path file = matcher.getSharedClassLocation(name);

			if (file == null) {
				URL url = ClassLoader.getSystemResource(name+".class");

				if (url != null) {
					file = getPath(url);
				}
			}

			if (file != null) {
				ClassInstance cls = readClass(file, cn -> false);
				if (!cls.getId().equals(id)) throw new RuntimeException("mismatched cls id "+id+" for "+file+", expected "+name);

				cls.setMatch(cls);
				ClassInstance ret = matcher.addSharedCls(cls);

				if (ret == cls) {
					processClassA(ret);
				}

				return ret;
			}
		}

		if (!createUnknown) return null;

		ClassInstance ret = new ClassInstance(id);
		matcher.addSharedCls(ret);

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
				matcher.addOpenFileSystem(FileSystems.newFileSystem(uri, Collections.emptyMap()));

				return Paths.get(uri);
			} catch (FileSystemNotFoundException e2) {
				throw new RuntimeException("can't find fs for "+url, e2);
			} catch (IOException e2) {
				throw new UncheckedIOException(e2);
			}
		}
	}

	public byte[] serializeClass(ClassInstance cls) {
		if (classes.get(cls.id) != cls) throw new IllegalArgumentException("unknown cls "+cls);
		if (cls.asmNode == null) throw new IllegalArgumentException("cls without asm node: "+cls);

		ClassWriter writer = new ClassWriter(0);
		cls.asmNode.accept(new AsmClassRemapper(writer, remapper));

		return writer.toByteArray();
	}

	final Matcher matcher;
	final AsmRemapper remapper = new AsmRemapper(this);
	final List<InputFile> inputFiles = new ArrayList<>();
	final Map<String, ClassInstance> classes = new HashMap<>();
	final Map<String, ClassInstance> arrayClasses = new HashMap<>();
}
