package matcher.type;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import matcher.NameType;
import matcher.SimilarityChecker;
import matcher.Util;
import matcher.bcremap.AsmClassRemapper;
import matcher.bcremap.AsmRemapper;
import matcher.classifier.ClassifierUtil;
import matcher.type.Signature.ClassSignature;

public final class ClassInstance implements Matchable<ClassInstance> {
	/**
	 * Create a shared unknown class.
	 */
	ClassInstance(String id, ClassEnv env) {
		this(id, null, env, null, false, false, null);

		assert id.indexOf('[') == -1 : id;
	}

	/**
	 * Create a known class (class path).
	 */
	public ClassInstance(String id, URI uri, ClassEnv env, ClassNode asmNode) {
		this(id, uri, env, asmNode, false, false, null);

		assert id.indexOf('[') == -1 : id;
	}

	/**
	 * Create an array class.
	 */
	ClassInstance(String id, ClassInstance elementClass) {
		this(id, null, elementClass.env, null, elementClass.nameObfuscated, false, elementClass);

		assert id.startsWith("[") : id;
		assert id.indexOf('[', getArrayDimensions()) == -1 : id;
		assert !elementClass.isArray();

		elementClass.addArray(this);
	}

	/**
	 * Create a non-array class.
	 */
	ClassInstance(String id, URI uri, ClassEnv env, ClassNode asmNode, boolean nameObfuscated) {
		this(id, uri, env, asmNode, nameObfuscated, true, null);

		assert id.startsWith("L") : id;
		assert id.indexOf('[') == -1 : id;
		assert asmNode != null;
	}

	private ClassInstance(String id, URI uri, ClassEnv env, ClassNode asmNode, boolean nameObfuscated, boolean input, ClassInstance elementClass) {
		if (id.isEmpty()) throw new IllegalArgumentException("empty id");
		if (env == null) throw new NullPointerException("null env");

		this.id = id;
		this.uri = uri;
		this.env = env;
		this.asmNodes = asmNode == null ? null : new ClassNode[] { asmNode };
		this.nameObfuscated = nameObfuscated;
		this.input = input;
		this.elementClass = elementClass;

		if (env.isShared()) matchedClass = this;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getName() {
		return getName(id);
	}

	@Override
	public String getName(NameType type) {
		if (type == NameType.PLAIN) {
			return getName();
		} else if (elementClass != null) {
			boolean isPrimitive = elementClass.isPrimitive();
			StringBuilder ret = new StringBuilder();

			ret.append(id, 0, getArrayDimensions());

			if (!isPrimitive) ret.append('L');
			ret.append(elementClass.getName(type));
			if (!isPrimitive) ret.append(';');

			return ret.toString();
		} else if (type == NameType.UID_PLAIN) {
			int uid = getUid();
			if (uid >= 0) return env.getGlobal().classUidPrefix+uid;
		}

		boolean locTmp = type == NameType.MAPPED_LOCTMP_PLAIN || type == NameType.LOCTMP_PLAIN;
		String ret;
		boolean fromMatched; // name retrieved from matched class

		if (type.mapped && mappedName != null) {
			// MAPPED_*, local name available
			ret = mappedName;
			fromMatched = false;
		} else if (type.mapped && matchedClass != null && matchedClass.mappedName != null) {
			// MAPPED_*, remote name available
			ret = matchedClass.mappedName;
			fromMatched = true;
		} else if (type.mapped && !nameObfuscated) {
			// MAPPED_*, local deobf
			ret = getInnerName0(getName());
			fromMatched = false;
		} else if (type.mapped && matchedClass != null && !matchedClass.nameObfuscated) {
			// MAPPED_*, remote deobf
			ret = matchedClass.getInnerName0(matchedClass.getName());
			fromMatched = true;
		} else if (type.isAux() && auxName != null && auxName.length > type.getAuxIndex() && auxName[type.getAuxIndex()] != null) {
			ret = auxName[type.getAuxIndex()];
			fromMatched = false;
		} else if (type.isAux() && matchedClass != null && matchedClass.auxName != null && matchedClass.auxName.length > type.getAuxIndex() && matchedClass.auxName[type.getAuxIndex()] != null) {
			ret = matchedClass.auxName[type.getAuxIndex()];
			fromMatched = true;
		} else if (type.tmp && matchedClass != null && matchedClass.tmpName != null) {
			// MAPPED_TMP_* with obf name or TMP_*, remote name available
			ret = matchedClass.tmpName;
			fromMatched = true;
		} else if ((type.tmp || locTmp) && tmpName != null) {
			// MAPPED_TMP_* or MAPPED_LOCTMP_* with obf name or TMP_* or LOCTMP_*, local name available
			ret = tmpName;
			fromMatched = false;
		} else if (type.plain) {
			ret = getInnerName0(getName());
			fromMatched = false;
		} else {
			return null;
		}

		assert ret == null || !hasOuterName(ret);

		/*
		 * ret-outer: whether ret's source has an outer class
		 * this-outer: whether this has an outer class
		 * has outer class -> assume not normal name with pkg, but plain inner class name
		 *
		 * ret-outer this-outer action                    ret-example this-example result-example
		 *     n         n      ret                        a/b         d/e          a/b
		 *     n         y      this.outer+ret.strip-pkg   a/b         d/e$f        d/e$b
		 *     y         n      ret.outer.pkg+ret          a/b$c       d/e          a/c
		 *     y         y      this.outer+ret             a/b$c       d/e$f        d/e$c
		 */

		if (!fromMatched || (outerClass == null) == (matchedClass.outerClass == null)) { // ret-outer == this-outer
			return outerClass != null ? getNestedName(outerClass.getName(type), ret) : ret;
		} else if (outerClass != null) { // ret is normal name, strip package from ret before concatenating
			return getNestedName(outerClass.getName(type), ret.substring(ret.lastIndexOf('/') + 1));
		} else { // ret is an outer name, restore pkg
			return getNestedName(matchedClass.outerClass.getName(type), ret);
		}
	}

	private String getInnerName0(String name) {
		if (outerClass == null) {
			return name;
		} else {
			return getInnerName(name);
		}
	}

	@Override
	public String getDisplayName(NameType type, boolean full) {
		char lastChar = id.charAt(id.length() - 1);
		String ret;

		if (lastChar != ';') { // primitive or primitive array
			switch (lastChar) {
			case 'B': ret = "byte"; break;
			case 'C': ret = "char"; break;
			case 'D': ret = "double"; break;
			case 'F': ret = "float"; break;
			case 'I': ret = "int"; break;
			case 'J': ret = "long"; break;
			case 'S': ret = "short"; break;
			case 'V': ret = "void"; break;
			case 'Z': ret = "boolean"; break;
			default: throw new IllegalStateException("invalid class desc: "+id);
			}
		} else {
			ret = getName(type).replace('/', '.');
		}

		int dims = getArrayDimensions();

		if (dims > 0) {
			StringBuilder sb;

			if (lastChar != ';') { // primitive array, ret is in plain name form from above
				assert !ret.startsWith("[") && !ret.endsWith(";");

				sb = new StringBuilder(ret.length() + 2 * dims);
				sb.append(ret);
			} else { // reference array, in dot separated id form
				assert ret.startsWith("[") && ret.endsWith(";");

				sb = new StringBuilder(ret.length() + dims - 2);
				sb.append(ret, dims + 1, ret.length() - 1);
			}

			for (int i = 0; i < dims; i++) {
				sb.append("[]");
			}

			ret = sb.toString();
		}

		return full ? ret : ret.substring(ret.lastIndexOf('.') + 1);
	}

	public URI getUri() {
		return uri;
	}

	@Override
	public Matchable<?> getOwner() {
		return null;
	}

	@Override
	public ClassEnv getEnv() {
		return env;
	}

	public ClassNode[] getAsmNodes() {
		return asmNodes;
	}

	public ClassNode getMergedAsmNode() {
		if (asmNodes == null) return null;
		if (asmNodes.length == 1) return asmNodes[0];

		return asmNodes[0]; // TODO: actually merge
	}

	void addAsmNode(ClassNode node) {
		if (!input) throw new IllegalStateException("not mergeable");

		asmNodes = Arrays.copyOf(asmNodes, asmNodes.length + 1);
		asmNodes[asmNodes.length - 1] = node;
	}

	@Override
	public boolean isMatchable() {
		return matchable;
	}

	@Override
	public void setMatchable(boolean matchable) {
		assert matchable || matchedClass == null;

		this.matchable = matchable;
	}

	@Override
	public ClassInstance getMatch() {
		return matchedClass;
	}

	public void setMatch(ClassInstance cls) {
		assert cls == null || isMatchable();
		assert cls == null || cls.getEnv() != env && !cls.getEnv().isShared();

		this.matchedClass = cls;
	}

	@Override
	public boolean isFullyMatched(boolean recursive) {
		if (matchedClass == null) return false;

		boolean anyUnmatched = false;

		for (MethodInstance m : methods) {
			if (m.isMatchable() && (!m.hasMatch() || recursive && !m.isFullyMatched(true))) {
				anyUnmatched = true;
				break;
			}
		}

		if (anyUnmatched) {
			for (MethodInstance a : methods) {
				if (!a.isMatchable() || a.hasMatch() && (!recursive || a.isFullyMatched(true))) continue;

				// check for any potential match to ignore methods that are impossible to match
				for (MethodInstance b : matchedClass.methods) {
					if (b.isMatchable() && !b.hasMatch() && ClassifierUtil.checkPotentialEquality(a, b)) {
						return false;
					}
				}
			}
		}

		anyUnmatched = false;

		for (FieldInstance m : fields) {
			if (m.isMatchable() && (!m.hasMatch() || recursive && !m.isFullyMatched(true))) {
				anyUnmatched = true;
				break;
			}
		}

		if (anyUnmatched) {
			for (FieldInstance a : fields) {
				if (!a.isMatchable() || a.hasMatch() && (!recursive || a.isFullyMatched(true))) continue;

				for (FieldInstance b : matchedClass.fields) {
					if (b.isMatchable() && !b.hasMatch() && ClassifierUtil.checkPotentialEquality(a, b)) {
						return false;
					}
				}
			}
		}

		return true;
	}

	@Override
	public float getSimilarity() {
		if (matchedClass == null) return 0;

		return SimilarityChecker.compare(this, matchedClass);
	}

	@Override
	public boolean isNameObfuscated() {
		return nameObfuscated;
	}

	public boolean isInput() {
		return input;
	}

	public ClassInstance getElementClass() {
		if (!isArray()) throw new IllegalStateException("not applicable to non-array");

		return elementClass;
	}

	public ClassInstance getElementClassShallow(boolean create) {
		if (!isArray()) throw new IllegalStateException("not applicable to non-array");

		int dims = getArrayDimensions();
		if (dims <= 1) return elementClass;

		String retId = id.substring(1);

		return create ? env.getCreateClassInstance(retId) : env.getClsById(retId);
	}

	public ClassSignature getSignature() {
		return signature;
	}

	void setSignature(ClassSignature signature) {
		this.signature = signature;
	}

	public boolean isPrimitive() {
		char start = id.charAt(0);

		return start != 'L' && start != '[';
	}

	public int getSlotSize() {
		char start = id.charAt(0);

		return (start == 'D' || start == 'J') ? 2 : 1;
	}

	public boolean isArray() {
		return elementClass != null;
	}

	public int getArrayDimensions() {
		if (elementClass == null) return 0;

		for (int i = 0; i < id.length(); i++) {
			if (id.charAt(i) != '[') return i;
		}

		throw new IllegalStateException("invalid id: "+id);
	}

	public ClassInstance[] getArrays() {
		return arrays;
	}

	private void addArray(ClassInstance cls) {
		assert !Arrays.asList(arrays).contains(cls);

		arrays = Arrays.copyOf(arrays, arrays.length + 1);
		arrays[arrays.length - 1] = cls;
	}

	public int getAccess() {
		if (asmNodes != null) {
			return asmNodes[0].access;
		} else {
			int ret = Opcodes.ACC_PUBLIC;

			if (!implementers.isEmpty()) {
				ret |= Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
			} else if (superClass != null && superClass.id.equals("Ljava/lang/Enum;")) {
				ret |= Opcodes.ACC_ENUM;
				if (childClasses.isEmpty()) ret |= Opcodes.ACC_FINAL;
			} else if (interfaces.size() == 1 && interfaces.iterator().next().id.equals("Ljava/lang/annotation/Annotation;")) {
				ret |= Opcodes.ACC_ANNOTATION | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
			}

			return ret;
		}
	}

	public boolean isInterface() {
		return (getAccess() & Opcodes.ACC_INTERFACE) != 0;
	}

	public boolean isEnum() {
		return (getAccess() & Opcodes.ACC_ENUM) != 0;
	}

	public boolean isAnnotation() {
		return (getAccess() & Opcodes.ACC_ANNOTATION) != 0;
	}

	public MethodInstance getMethod(String id) {
		return methodIdx.get(id);
	}

	public FieldInstance getField(String id) {
		return fieldIdx.get(id);
	}

	public MethodInstance getMethod(String name, String desc) {
		if (desc != null) {
			return methodIdx.get(MethodInstance.getId(name, desc));
		} else {
			MethodInstance ret = null;

			for (MethodInstance method : methods) {
				if (method.origName.equals(name)) {
					if (ret != null) return null; // non-unique

					ret = method;
				}
			}

			return ret;
		}
	}

	public MethodInstance getMethod(String name, String desc, NameType nameType) {
		if (nameType == NameType.PLAIN) return getMethod(name, desc);

		MethodInstance ret = null;

		methodLoop: for (MethodInstance method : methods) {
			String mappedName = method.getName(nameType);

			if (mappedName == null || !name.equals(mappedName)) {
				continue;
			}

			if (desc != null) {
				assert desc.startsWith("(");
				int idx = 0;
				int pos = 1;
				boolean last = false;

				do {
					char c = desc.charAt(pos);
					ClassInstance match;

					if (c == ')') {
						if (idx != method.args.length) continue methodLoop;
						last = true;
						pos++;
						c = desc.charAt(pos);
						match = method.retType;
					} else {
						if (idx >= method.args.length) continue methodLoop;
						match = method.args[idx].type;
					}

					if (c == '[') { // array cls
						int dims = 1;
						while ((c = desc.charAt(++pos)) == '[') dims++;

						if (match.getArrayDimensions() != dims) continue methodLoop;
						match = match.elementClass;
					} else {
						if (match.isArray()) continue methodLoop;
					}

					int end;

					if (c != 'L') { // primitive cls
						end = pos + 1;
					} else {
						end = desc.indexOf(';', pos + 1) + 1;
						assert end != 0;
					}

					String clsMappedName = match.getName(nameType);
					if (clsMappedName == null) continue methodLoop;

					if (c != 'L') {
						if (clsMappedName.length() != end - pos || !desc.startsWith(clsMappedName, pos)) continue methodLoop;
					} else {
						if (clsMappedName.length() != end - pos - 2 || !desc.startsWith(clsMappedName, pos + 1)) continue methodLoop;
					}

					pos = end;
					idx++;
				} while (!last);
			}

			if (ret != null) return null; // non-unique

			ret = method;
		}

		return ret;
	}

	public FieldInstance getField(String name, String desc) {
		if (desc != null) {
			return fieldIdx.get(FieldInstance.getId(name, desc));
		} else {
			FieldInstance ret = null;

			for (FieldInstance field : fields) {
				if (field.origName.equals(name)) {
					if (ret != null) return null; // non-unique

					ret = field;
				}
			}

			return ret;
		}
	}

	public FieldInstance getField(String name, String desc, NameType nameType) {
		if (nameType == NameType.PLAIN) return getField(name, desc);

		FieldInstance ret = null;

		for (FieldInstance field : fields) {
			String mappedName = field.getName(nameType);

			if (mappedName == null || !name.equals(mappedName)) {
				continue;
			}

			if (desc != null) {
				String clsMappedName = field.type.getName(nameType);
				if (clsMappedName == null) continue;

				if (desc.startsWith("[") || !desc.endsWith(";")) {
					if (!desc.equals(clsMappedName)) continue;
				} else {
					if (desc.length() != clsMappedName.length() + 2 || !desc.startsWith(clsMappedName, 1)) continue;
				}
			}

			if (ret != null) return null; // non-unique

			ret = field;
		}

		return ret;
	}

	public MethodInstance resolveMethod(String name, String desc, boolean toInterface) {
		// toInterface = false: https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.3
		// toInterface = true: https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.4
		// TODO: access check after resolution

		assert asmNodes == null || isInterface() == toInterface;

		if (!toInterface) {
			MethodInstance ret = resolveSignaturePolymorphicMethod(name);
			if (ret != null) return ret;

			ret = getMethod(name, desc);
			if (ret != null) return ret; // <this> is unconditional

			ClassInstance cls = this;

			while ((cls = cls.superClass) != null) {
				ret = cls.resolveSignaturePolymorphicMethod(name);
				if (ret != null) return ret;

				ret = cls.getMethod(name, desc);
				if (ret != null) return ret;
			}

			return resolveInterfaceMethod(name, desc);
		} else {
			MethodInstance ret = getMethod(name, desc);
			if (ret != null) return ret; // <this> is unconditional

			if (superClass != null) {
				assert superClass.id.equals("Ljava/lang/Object;");

				ret = superClass.getMethod(name, desc);
				if (ret != null && (!ret.isReal() || (ret.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_PUBLIC)) return ret;
			}

			return resolveInterfaceMethod(name, desc);
		}
	}

	private MethodInstance resolveSignaturePolymorphicMethod(String name) {
		if (id.equals("Ljava/lang/invoke/MethodHandle;")) { // check for signature polymorphic method - jvms-2.9
			MethodInstance ret = getMethod(name, "([Ljava/lang/Object;)Ljava/lang/Object;");
			final int reqFlags = Opcodes.ACC_VARARGS | Opcodes.ACC_NATIVE;

			if (ret != null && (!ret.isReal() || (ret.access & reqFlags) == reqFlags)) {
				return ret;
			}
		}

		return null;
	}

	private MethodInstance resolveInterfaceMethod(String name, String desc) {
		Queue<ClassInstance> queue = new ArrayDeque<>();
		Set<ClassInstance> queued = Util.newIdentityHashSet();
		ClassInstance cls = this;

		do {
			for (ClassInstance ifCls : cls.interfaces) {
				if (queued.add(ifCls)) queue.add(ifCls);
			}
		} while ((cls = cls.superClass) != null);

		if (queue.isEmpty()) return null;

		Set<MethodInstance> matches = Util.newIdentityHashSet();
		boolean foundNonAbstract = false;

		while ((cls = queue.poll()) != null) {
			MethodInstance ret = cls.getMethod(name, desc);

			if (ret != null
					&& (!ret.isReal() || (ret.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0)) {
				matches.add(ret);

				if (ret.isReal() && (ret.access & Opcodes.ACC_ABSTRACT) == 0) { // jvms prefers the closest non-abstract method
					foundNonAbstract = true;
				}
			}

			for (ClassInstance ifCls : cls.interfaces) {
				if (queued.add(ifCls)) queue.add(ifCls);
			}
		}

		if (matches.isEmpty()) return null;
		if (matches.size() == 1) return matches.iterator().next();

		// non-abstract methods take precedence over non-abstract methods, remove all abstract ones if there's at least 1 non-abstract

		if (foundNonAbstract) {
			for (Iterator<MethodInstance> it = matches.iterator(); it.hasNext(); ) {
				MethodInstance m = it.next();

				if (!m.isReal() || (m.access & Opcodes.ACC_ABSTRACT) != 0) {
					it.remove();
				}
			}

			assert !matches.isEmpty();
			if (matches.size() == 1) return matches.iterator().next();
		}

		// eliminate not maximally specific method declarations, i.e. those that have a child method in matches

		for (Iterator<MethodInstance> it = matches.iterator(); it.hasNext(); ) {
			MethodInstance m = it.next();

			cmpLoop: for (MethodInstance m2 : matches) {
				if (m2 == m) continue;

				if (m2.cls.interfaces.contains(m.cls)) { // m2 is a direct child of m, so m isn't maximally specific
					it.remove();
					break;
				}

				queue.addAll(m2.cls.interfaces);

				while ((cls = queue.poll()) != null) {
					if (cls.interfaces.contains(m.cls)) { // m2 is an indirect child of m, so m isn't maximally specific
						it.remove();
						queue.clear();
						break cmpLoop;
					}

					queue.addAll(cls.interfaces);
				}
			}
		}

		// return an arbitrary choice

		return matches.iterator().next();
	}

	public FieldInstance resolveField(String name, String desc) {
		FieldInstance ret = getField(name, desc);
		if (ret != null) return ret;

		if (!interfaces.isEmpty()) {
			Deque<ClassInstance> queue = new ArrayDeque<>();
			queue.addAll(interfaces);
			ClassInstance cls;

			while ((cls = queue.pollFirst()) != null) {
				ret = cls.getField(name, desc);
				if (ret != null) return ret;

				for (ClassInstance iface : cls.interfaces) {
					queue.addFirst(iface);
				}
			}
		}

		ClassInstance cls = superClass;

		while (cls != null) {
			ret = cls.getField(name, desc);
			if (ret != null) return ret;

			cls = cls.superClass;
		}

		return null;
	}

	public MethodInstance getMethod(int pos) {
		if (pos < 0 || pos >= methods.length) throw new IndexOutOfBoundsException();
		if (asmNodes == null) throw new UnsupportedOperationException();

		return methods[pos];
	}

	public FieldInstance getField(int pos) {
		if (pos < 0 || pos >= fields.length) throw new IndexOutOfBoundsException();
		if (asmNodes == null) throw new UnsupportedOperationException();

		return fields[pos];
	}

	public MethodInstance[] getMethods() {
		return methods;
	}

	public FieldInstance[] getFields() {
		return fields;
	}

	public ClassInstance getOuterClass() {
		return outerClass;
	}

	public Set<ClassInstance> getInnerClasses() {
		return innerClasses;
	}

	public ClassInstance getSuperClass() {
		return superClass;
	}

	public Set<ClassInstance> getChildClasses() {
		return childClasses;
	}

	public Set<ClassInstance> getInterfaces() {
		return interfaces;
	}

	public Set<ClassInstance> getImplementers() {
		return implementers;
	}

	public Set<MethodInstance> getMethodTypeRefs() {
		return methodTypeRefs;
	}

	public Set<FieldInstance> getFieldTypeRefs() {
		return fieldTypeRefs;
	}

	public Set<String> getStrings() {
		return strings;
	}

	public boolean isShared() {
		return matchedClass == this;
	}

	@Override
	public boolean hasLocalTmpName() {
		return tmpName != null;
	}

	public void setTmpName(String tmpName) {
		this.tmpName = tmpName;
	}

	@Override
	public int getUid() {
		if (uid >= 0) {
			if (matchedClass != null && matchedClass.uid >= 0) {
				return Math.min(uid, matchedClass.uid);
			} else {
				return uid;
			}
		} else if (matchedClass != null) {
			return matchedClass.uid;
		} else {
			return -1;
		}
	}

	public void setUid(int uid) {
		this.uid = uid;
	}

	@Override
	public boolean hasMappedName() {
		return mappedName != null
				|| matchedClass != null && matchedClass.mappedName != null
				|| elementClass != null && elementClass.hasMappedName()
				|| outerClass != null && outerClass.hasMappedName();
	}

	public boolean hasNoFullyMappedName() {
		assert elementClass == null || outerClass == null; // array classes can't have an outer class

		return outerClass != null
				&& mappedName == null
				&& (matchedClass == null || matchedClass.mappedName == null);
	}

	public void setMappedName(String mappedName) {
		assert mappedName == null || !hasOuterName(mappedName);

		this.mappedName = mappedName;
	}

	public String getMappedComment() {
		if (mappedComment != null) {
			return mappedComment;
		} else if (matchedClass != null) {
			return matchedClass.mappedComment;
		} else {
			return null;
		}
	}

	public void setMappedComment(String comment) {
		if (comment != null && comment.isEmpty()) comment = null;

		this.mappedComment = comment;
	}

	@Override
	public boolean hasAuxName(int index) {
		return auxName != null && auxName.length > index && auxName[index] != null;
	}

	public void setAuxName(int index, String name) {
		assert name == null || !hasOuterName(name);

		if (this.auxName == null) this.auxName = new String[NameType.AUX_COUNT];
		this.auxName[index] = name;
	}

	public boolean isAssignableFrom(ClassInstance c) {
		if (c == this) return true;
		if (isPrimitive()) return false;

		if (!isInterface()) {
			ClassInstance sc = c;

			while ((sc = sc.superClass) != null) {
				if (sc == this) return true;
			}
		} else {
			if (implementers.isEmpty()) return false;

			// check if c directly implements this
			if (implementers.contains(c)) return true;

			// check if a superclass of c directly implements this
			ClassInstance sc = c;

			while ((sc = sc.superClass) != null) {
				if (implementers.contains(sc)) return true; // cls -> this
			}

			// check if c or a superclass of c implements this with one indirection
			sc = c;
			Queue<ClassInstance> toCheck = null;

			do {
				for (ClassInstance iface : sc.getInterfaces()) {
					assert iface != this; // already checked iface directly
					if (iface.interfaces.isEmpty()) continue;
					if (implementers.contains(iface)) return true; // cls -> if -> this

					if (toCheck == null) toCheck = new ArrayDeque<>();

					toCheck.addAll(iface.interfaces);
				}
			} while ((sc = sc.superClass) != null);

			// check if c or a superclass of c implements this with multiple indirections
			if (toCheck != null) {
				while ((sc = toCheck.poll()) != null) {
					for (ClassInstance iface : sc.getInterfaces()) {
						assert iface != this; // already checked

						if (implementers.contains(iface)) return true;

						toCheck.addAll(iface.interfaces);
					}
				}
			}
		}

		return false;
	}

	public ClassInstance getCommonSuperClass(ClassInstance o) {
		if (o == this) return this;
		if (isPrimitive() || o.isPrimitive()) return null;
		if (isAssignableFrom(o)) return this;
		if (o.isAssignableFrom(this)) return o;

		ClassInstance objCls = env.getCreateClassInstance("Ljava/lang/Object;");

		if (!isInterface() && !o.isInterface()) {
			ClassInstance sc = this;

			while ((sc = sc.superClass) != null && sc != objCls) {
				if (sc.isAssignableFrom(o)) return sc;
			}
		}

		if (!interfaces.isEmpty() || !o.interfaces.isEmpty()) {
			List<ClassInstance> ret = new ArrayList<>();
			Queue<ClassInstance> toCheck = new ArrayDeque<>();
			Set<ClassInstance> checked = Util.newIdentityHashSet();
			toCheck.addAll(interfaces);
			toCheck.addAll(o.interfaces);

			ClassInstance cls;

			while ((cls = toCheck.poll()) != null) {
				if (!checked.add(cls)) continue;

				if (cls.isAssignableFrom(o)) {
					ret.add(cls);
				} else {
					toCheck.addAll(cls.interfaces);
				}
			}

			if (!ret.isEmpty()) {
				if (ret.size() >= 1) {
					for (Iterator<ClassInstance> it = ret.iterator(); it.hasNext(); ) {
						cls = it.next();

						for (ClassInstance cls2 : ret) {
							if (cls != cls2 && cls.isAssignableFrom(cls2)) {
								it.remove();
								break;
							}
						}
					}
					// TODO: multiple options..
				}

				return ret.get(0);
			}
		}

		return objCls;
	}

	public void accept(ClassVisitor visitor, NameType nameType) {
		ClassNode cn = getMergedAsmNode();
		if (cn == null) throw new IllegalArgumentException("cls without asm node: "+this);

		synchronized (Util.asmNodeSync) {
			if (nameType != NameType.PLAIN) {
				AsmClassRemapper.process(cn, new AsmRemapper(env, nameType), visitor);
			} else {
				cn.accept(visitor);
			}
		}
	}

	public byte[] serialize(NameType nameType) {
		ClassWriter writer = new ClassWriter(0);
		accept(writer, nameType);

		return writer.toByteArray();
	}

	@Override
	public String toString() {
		return getDisplayName(NameType.PLAIN, true);
	}

	void addMethod(MethodInstance method) {
		if (method == null) throw new NullPointerException("null method");

		methodIdx.put(method.id, method);
		methods = Arrays.copyOf(methods, methods.length + 1);
		methods[methods.length - 1] = method;
	}

	void addField(FieldInstance field) {
		if (field == null) throw new NullPointerException("null field");

		fieldIdx.put(field.id, field);
		fields = Arrays.copyOf(fields, fields.length + 1);
		fields[fields.length - 1] = field;
	}

	public static String getId(String name) {
		if (name.isEmpty()) throw new IllegalArgumentException("empty class name");
		assert name.charAt(name.length() - 1) != ';' || name.charAt(0) == '[' : name;

		if (name.charAt(0) == '[') {
			assert name.charAt(name.length() - 1) == ';' || name.lastIndexOf('[') == name.length() - 2;

			return name;
		}

		return "L"+name+";";
	}

	public static String getName(String id) {
		return id.startsWith("L") ? id.substring(1, id.length() - 1) : id;
	}

	public static boolean hasOuterName(String name) {
		return name.indexOf('$') > 0; // ignore names starting with $
	}

	public static String getInnerName(String name) {
		return name.substring(name.lastIndexOf('$') + 1);
	}

	public static String getNestedName(String outerName, String innerName) {
		if (outerName == null || innerName == null) {
			return null;
		} else {
			return outerName + '$' + innerName;
		}
	}

	public static final Comparator<ClassInstance> nameComparator = Comparator.comparing(ClassInstance::getId);

	private static final ClassInstance[] noArrays = new ClassInstance[0];
	private static final MethodInstance[] noMethods = new MethodInstance[0];
	private static final FieldInstance[] noFields = new FieldInstance[0];

	final String id;
	final URI uri;
	final ClassEnv env;
	private ClassNode[] asmNodes;
	final boolean nameObfuscated;
	private final boolean input;
	final ClassInstance elementClass; // 0-dim class TODO: improve handling of array classes (references etc.)
	private ClassSignature signature;

	MethodInstance[] methods = noMethods;
	FieldInstance[] fields = noFields;
	final Map<String, MethodInstance> methodIdx = new HashMap<>();
	final Map<String, FieldInstance> fieldIdx = new HashMap<>();

	private ClassInstance[] arrays = noArrays;

	ClassInstance outerClass;
	final Set<ClassInstance> innerClasses = Util.newIdentityHashSet();

	ClassInstance superClass;
	final Set<ClassInstance> childClasses = Util.newIdentityHashSet();
	final Set<ClassInstance> interfaces = Util.newIdentityHashSet();
	final Set<ClassInstance> implementers = Util.newIdentityHashSet();

	final Set<MethodInstance> methodTypeRefs = Util.newIdentityHashSet();
	final Set<FieldInstance> fieldTypeRefs = Util.newIdentityHashSet();

	final Set<String> strings = new HashSet<>();

	private String tmpName;
	private int uid = -1;

	private String mappedName;
	private String mappedComment;

	private String[] auxName;

	private boolean matchable = true;
	private ClassInstance matchedClass;
}
