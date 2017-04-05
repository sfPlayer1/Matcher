package matcher.type;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import matcher.Util;

public class ClassInstance implements IMatchable<ClassInstance> {
	ClassInstance(String id) {
		this(id, null, null, false, null);

		assert !id.startsWith("[") : id;

		matchedClass = this;
	}

	ClassInstance(String id, ClassInstance elementClass) {
		this(id, null, null, false, elementClass);

		assert id.startsWith("[") : id;

		if (elementClass.isShared()) {
			matchedClass = this;
		}
	}

	ClassInstance(String id, URI uri, ClassNode asmNode, boolean nameObfuscated) {
		this(id, uri, asmNode, nameObfuscated, null);

		assert id.startsWith("L") : id;
	}

	private ClassInstance(String id, URI uri, ClassNode asmNode, boolean nameObfuscated, ClassInstance elementClass) {
		if (id.isEmpty()) throw new IllegalArgumentException("empty id");

		this.id = id;
		this.uri = uri;
		this.asmNode = asmNode;
		this.nameObfuscated = nameObfuscated;
		this.elementClass = elementClass;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return id.startsWith("L") ? id.substring(1, id.length() - 1) : id;
	}

	public URI getUri() {
		return uri;
	}

	public ClassNode getAsmNode() {
		return asmNode;
	}

	@Override
	public ClassInstance getMatch() {
		return matchedClass;
	}

	public void setMatch(ClassInstance cls) {
		this.matchedClass = cls;
	}

	@Override
	public boolean isNameObfuscated() {
		return nameObfuscated;
	}

	public ClassInstance getElementClass() {
		return elementClass;
	}

	public boolean isPrimitive() {
		char start = id.charAt(0);

		return start != 'L' && start != '[';
	}

	public boolean isArray() {
		return elementClass != null;
	}

	public boolean isInterface() {
		if (asmNode != null) {
			return (asmNode.access & Opcodes.ACC_INTERFACE) != 0;
		} else {
			return !implementers.isEmpty();
		}
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

	public MethodInstance resolveMethod(String name, String desc) {
		ClassInstance cls = this;

		do {
			MethodInstance ret = cls.getMethod(name, desc);
			if (ret != null) return ret;

			cls = cls.superClass;
		} while (cls != null);

		if (isInterface()) {
			if (interfaces.isEmpty()) return null;

			Queue<ClassInstance> queue = new ArrayDeque<>();
			queue.addAll(interfaces);

			while ((cls = queue.poll()) != null) {
				MethodInstance ret = cls.getMethod(name, desc);
				if (ret != null) return ret;

				queue.addAll(cls.interfaces);
			}
		}

		return null;
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
		if (asmNode == null) throw new UnsupportedOperationException();

		return methods[pos];
	}

	public FieldInstance getField(int pos) {
		if (pos < 0 || pos >= fields.length) throw new IndexOutOfBoundsException();
		if (asmNode == null) throw new UnsupportedOperationException();

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

	public boolean isShared() {
		return matchedClass == this;
	}

	public boolean hasMappedName() {
		return mappedName != null
				|| matchedClass != null && matchedClass.mappedName != null
				|| elementClass != null && elementClass.hasMappedName();
	}

	public String getMappedName() {
		if (mappedName != null) {
			return mappedName;
		} else if (matchedClass != null) {
			return matchedClass.mappedName;
		} else if (elementClass != null) {
			return elementClass.getMappedName();
		} else {
			return null;
		}
	}

	public void setMappedName(String mappedName) {
		this.mappedName = mappedName;
	}

	public String getMappedComment() {
		return mappedComment;
	}

	public void setMappedComment(String comment) {
		if (comment != null && comment.isEmpty()) comment = null;

		this.mappedComment = comment;
	}

	@Override
	public String toString() {
		return getName();
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

	private static final MethodInstance[] noMethods = new MethodInstance[0];
	private static final FieldInstance[] noFields = new FieldInstance[0];

	final String id;
	final URI uri;
	final ClassNode asmNode;
	final boolean nameObfuscated;
	final ClassInstance elementClass; // TODO: improve handling of array classes (references etc.)

	MethodInstance[] methods = noMethods;
	FieldInstance[] fields = noFields;
	final Map<String, MethodInstance> methodIdx = new HashMap<>();
	final Map<String, FieldInstance> fieldIdx = new HashMap<>();

	ClassInstance outerClass;
	final Set<ClassInstance> innerClasses = Util.newIdentityHashSet();

	ClassInstance superClass;
	final Set<ClassInstance> childClasses = Util.newIdentityHashSet();
	final Set<ClassInstance> interfaces = Util.newIdentityHashSet();
	final Set<ClassInstance> implementers = Util.newIdentityHashSet();

	final Set<MethodInstance> methodTypeRefs = Util.newIdentityHashSet();
	final Set<FieldInstance> fieldTypeRefs = Util.newIdentityHashSet();

	String mappedName;
	String mappedComment;
	ClassInstance matchedClass;
}