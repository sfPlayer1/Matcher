package matcher.type;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import matcher.Util;
import matcher.type.Signature.MethodSignature;

public class MethodInstance extends MemberInstance<MethodInstance> {
	/**
	 * Create a shared unknown method.
	 */
	MethodInstance(ClassInstance cls, String origName, String desc, boolean isStatic) {
		this(cls, origName, desc, null, false, -1, isStatic);
	}

	/**
	 * Create a known method.
	 */
	MethodInstance(ClassInstance cls, String origName, String desc, MethodNode asmNode, boolean nameObfuscated, int position) {
		this(cls, origName, desc, asmNode, nameObfuscated, position, (asmNode.access & Opcodes.ACC_STATIC) != 0);
	}

	private MethodInstance(ClassInstance cls, String origName, String desc, MethodNode asmNode, boolean nameObfuscated, int position, boolean isStatic) {
		super(cls, getId(origName, desc), origName, nameObfuscated, position, isStatic);

		this.asmNode = asmNode;
		this.args = gatherArgs(this, desc);
		//this.vars = gatherVars(this);
		this.retType = cls.getEnv().getCreateClassInstance(Type.getReturnType(desc).getDescriptor());
		classRefs.add(retType);
		retType.methodTypeRefs.add(this);
		this.signature = asmNode == null || asmNode.signature == null || !cls.isInput() ? null : MethodSignature.parse(asmNode.signature, cls.getEnv());
	}

	private static MethodVarInstance[] gatherArgs(MethodInstance method, String desc) {
		Type[] argTypes = Type.getArgumentTypes(desc);
		if (argTypes.length == 0) return emptyVars;

		MethodVarInstance[] args = new MethodVarInstance[argTypes.length];
		List<LocalVariableNode> locals = method.asmNode != null ? method.asmNode.localVariables : null;
		InsnList il = method.asmNode != null ? method.asmNode.instructions : null;
		AbstractInsnNode firstInsn = method.asmNode != null ? il.getFirst() : null;
		int lvtIdx = method.isStatic ? 0 : 1;

		for (int i = 0; i < argTypes.length; i++) {
			Type asmType = argTypes[i];
			ClassInstance type = method.cls.getEnv().getCreateClassInstance(asmType.getDescriptor());
			int asmIndex = -1;
			int startInsn = -1;
			int endInsn = -1;
			String name = null;

			if (locals != null) {
				for (int j = 0; j < locals.size(); j++) {
					LocalVariableNode n = locals.get(j);

					if (n.index == lvtIdx && n.start == firstInsn) {
						assert n.desc.equals(type.id);

						asmIndex = j;
						startInsn = il.indexOf(n.start);
						endInsn = il.indexOf(n.end);
						name = n.name;

						break;
					}
				}
			}

			MethodVarInstance arg = new MethodVarInstance(method, true, i, lvtIdx, asmIndex, type, startInsn, endInsn, name, name == null || method.nameObfuscated || method.cls.nameObfuscated);
			args[i] = arg;

			method.classRefs.add(type);
			type.methodTypeRefs.add(method);

			lvtIdx += type.getSlotSize();
		}

		return args;
	}

	/*private static MethodVarInstance[] gatherVars(MethodInstance method) {
		MethodNode asmNode = method.asmNode;
		if (asmNode == null) return emptyVars;
		if (asmNode.localVariables == null) return null;
		if (asmNode.localVariables.isEmpty()) return emptyVars;

		InsnList il = asmNode.instructions;
		AbstractInsnNode firstInsn = il.getFirst();
		List<MethodVarInstance> ret = new ArrayList<>();

		for (int i = 0; i < asmNode.localVariables.size(); i++) {
			LocalVariableNode n = asmNode.localVariables.get(i);

			if (n.start == firstInsn) { // check if it's an arg
				if (n.index == 0 && !method.isStatic) continue;

				boolean found = false;

				for (MethodArgInstance arg : method.args) {
					if (arg.lvtIndex == n.index) {
						found = true;
						break;
					}
				}

				if (found) continue;
			}

			assert n.name != null;
			ret.add(new MethodVarInstance(method, false, ret.size(), n.index, i,
					method.getEnv().getCreateClassInstance(n.desc), il.indexOf(n.start), il.indexOf(n.end),
					n.name, method.nameObfuscated || method.cls.nameObfuscated));
		}

		return ret.isEmpty() ? emptyVars : ret.toArray(new MethodVarInstance[0]);
	}*/

	@Override
	public String getDisplayName(boolean full, boolean mapped, boolean tmpNamed, boolean unmatchedTmp) {
		StringBuilder ret = new StringBuilder(64);
		ret.append(super.getDisplayName(full, mapped, tmpNamed, unmatchedTmp));
		ret.append('(');
		boolean first = true;

		for (MethodVarInstance arg : args) {
			if (first) {
				first = false;
			} else {
				ret.append(", ");
			}

			ret.append(arg.getType().getDisplayName(true, mapped, tmpNamed, unmatchedTmp));
		}

		ret.append(')');
		ret.append(retType.getDisplayName(true, mapped, tmpNamed, unmatchedTmp));

		return ret.toString();
	}

	@Override
	public String getDesc() {
		String ret = "(";

		for (MethodVarInstance arg : args) {
			ret += arg.getType().id;
		}

		ret += ")" + retType.getId();

		return ret;
	}

	@Override
	public boolean isReal() {
		return asmNode != null;
	}

	public MethodNode getAsmNode() {
		return asmNode;
	}

	public MethodVarInstance getArg(int index) {
		if (index < 0 || index >= args.length) throw new IllegalArgumentException("invalid arg index: "+index);

		return args[index];
	}

	public MethodVarInstance getArg(String id) {
		return getArg(Integer.parseInt(id));
	}

	public MethodVarInstance getVar(String id) {
		throw new UnsupportedOperationException(); // TODO: implement
	}

	public MethodVarInstance getVar(String id, boolean isArg) {
		return isArg ? getArg(id) : getVar(id);
	}

	public MethodVarInstance[] getArgs() {
		return args;
	}

	public MethodVarInstance[] getVars() {
		return vars;
	}

	public boolean hasMappedArg() {
		for (MethodVarInstance arg : args) {
			if (arg.getMappedName() != null) return true;
		}

		return false;
	}

	public boolean hasAllArgsMapped() {
		for (MethodVarInstance arg : args) {
			if (arg.getMappedName() == null) return false;
		}

		return true;
	}

	public ClassInstance getRetType() {
		return retType;
	}

	@Override
	public int getAccess() {
		if (asmNode == null) {
			int ret = Opcodes.ACC_PUBLIC;
			if (isStatic) ret |= Opcodes.ACC_STATIC;

			return ret;
		} else {
			return asmNode.access;
		}
	}

	public MethodSignature getSignature() {
		return signature;
	}

	public Set<MethodInstance> getRefsIn() {
		return refsIn;
	}

	public Set<MethodInstance> getRefsOut() {
		return refsOut;
	}

	public Set<FieldInstance> getFieldReadRefs() {
		return fieldReadRefs;
	}

	public Set<FieldInstance> getFieldWriteRefs() {
		return fieldWriteRefs;
	}

	public Set<ClassInstance> getClassRefs() {
		return classRefs;
	}

	@Override
	public String getUidString() {
		int uid = getUid();
		if (uid < 0) return null;

		return "method_"+uid;
	}

	static String getId(String name, String desc) {
		return name+desc;
	}

	private static final MethodVarInstance[] emptyVars = new MethodVarInstance[0];

	final MethodNode asmNode;
	final MethodVarInstance[] args;
	final ClassInstance retType;
	MethodVarInstance[] vars;
	final MethodSignature signature;

	final Set<MethodInstance> refsIn = Util.newIdentityHashSet();
	final Set<MethodInstance> refsOut = Util.newIdentityHashSet();
	final Set<FieldInstance> fieldReadRefs = Util.newIdentityHashSet();
	final Set<FieldInstance> fieldWriteRefs = Util.newIdentityHashSet();
	final Set<ClassInstance> classRefs = Util.newIdentityHashSet();
}
