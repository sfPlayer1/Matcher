package matcher.type;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldNode;

import matcher.NameType;
import matcher.Util;
import matcher.classifier.ClassifierUtil;
import matcher.type.Signature.FieldSignature;

public final class FieldInstance extends MemberInstance<FieldInstance> {
	/**
	 * Create a shared unknown field.
	 */
	FieldInstance(ClassInstance cls, String origName, String desc, boolean isStatic) {
		this(cls, origName, desc, null, false, -1, isStatic);
	}

	/**
	 * Create a known field.
	 */
	FieldInstance(ClassInstance cls, String origName, String desc, FieldNode asmNode, boolean nameObfuscated, int position) {
		this(cls, origName, desc, asmNode, nameObfuscated, position, (asmNode.access & Opcodes.ACC_STATIC) != 0);
	}

	private FieldInstance(ClassInstance cls, String origName, String desc, FieldNode asmNode, boolean nameObfuscated, int position, boolean isStatic) {
		super(cls, getId(origName, desc), origName, nameObfuscated, position, isStatic);

		try {
			this.type = cls.getEnv().getCreateClassInstance(desc);
			this.asmNode = asmNode;
			this.signature = asmNode == null || asmNode.signature == null || !cls.isInput() ? null : FieldSignature.parse(asmNode.signature, cls.getEnv());
		} catch (InvalidSharedEnvQueryException e) {
			throw e.checkOrigin(cls);
		}

		type.fieldTypeRefs.add(this);
	}

	@Override
	public MatchableKind getKind() {
		return MatchableKind.FIELD;
	}

	@Override
	public String getDisplayName(NameType type, boolean full) {
		StringBuilder ret = new StringBuilder(64);

		ret.append(super.getDisplayName(type, full));
		ret.append(' ');
		ret.append(this.type.getDisplayName(type, full));

		return ret.toString();
	}

	@Override
	public String getDesc() {
		return type.id;
	}

	@Override
	public String getDesc(NameType type) {
		if (type == NameType.PLAIN || this.type.isPrimitive()) {
			return this.type.id;
		} else {
			String typeName = this.type.getName(type);

			return typeName != null ? ClassInstance.getId(typeName) : null;
		}
	}

	@Override
	public boolean isReal() {
		return asmNode != null;
	}

	public FieldNode getAsmNode() {
		return asmNode;
	}

	public ClassInstance getType() {
		return type;
	}

	@Override
	public int getAccess() {
		if (asmNode == null) {
			int ret = Opcodes.ACC_PUBLIC;
			if (isStatic) ret |= Opcodes.ACC_STATIC;
			if (isStatic && type == cls && cls.isEnum()) ret |= Opcodes.ACC_ENUM;
			if (isStatic && cls.isInterface()) ret |= Opcodes.ACC_FINAL;

			return ret;
		} else {
			return asmNode.access;
		}
	}

	public FieldSignature getSignature() {
		return signature;
	}

	public List<AbstractInsnNode> getInitializer() {
		return initializer;
	}

	public Set<MethodInstance> getReadRefs() {
		return readRefs;
	}

	public Set<MethodInstance> getWriteRefs() {
		return writeRefs;
	}

	@Override
	public boolean canBeRecordComponent() {
		return cls.isRecord() && !isStatic() && !isProtected() && !isPublic() && isFinal(); // jls requires private, but proguard(?) uses package-private too
	}

	@Override
	public MethodInstance getLinkedRecordComponent(NameType nameType) {
		if (!canBeRecordComponent()) return null;

		String name = nameType != null ? getName(nameType) : null;
		MethodInstance ret = null;

		for (MethodInstance method : cls.getMethods()) {
			if (method.canBeRecordComponent()
					&& method.getRetType().equals(type)
					&& (name == null || name.equals(method.getName(nameType)))
					&& (ret == null || !readRefs.contains(ret) && readRefs.contains(method))) {
				ret = method;
			}
		}

		return ret;
	}

	@Override
	protected String getUidString() {
		int uid = getUid();
		if (uid < 0) return null;

		return cls.env.getGlobal().fieldUidPrefix+uid;
	}

	@Override
	public boolean hasPotentialMatch() {
		if (matchedInstance != null) return true;
		if (!cls.hasMatch() || !isMatchable()) return false;

		for (FieldInstance o : cls.getMatch().getFields()) {
			if (ClassifierUtil.checkPotentialEquality(this, o)) return true;
		}

		return false;
	}

	@Override
	public boolean isFullyMatched(boolean recursive) {
		return matchedInstance != null;
	}

	public static String getId(String name, String desc) {
		return name+";;"+desc;
	}

	final FieldNode asmNode;
	final ClassInstance type;
	ClassInstance exactType;
	private final FieldSignature signature;
	List<AbstractInsnNode> initializer;

	final Set<MethodInstance> readRefs = Util.newIdentityHashSet();
	final Set<MethodInstance> writeRefs = Util.newIdentityHashSet();
}
