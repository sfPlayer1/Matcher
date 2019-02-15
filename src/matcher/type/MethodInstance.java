package matcher.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import matcher.NameType;
import matcher.Util;
import matcher.type.Signature.MethodSignature;

public final class MethodInstance extends MemberInstance<MethodInstance> {
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
		this.vars = gatherVars(this);
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
		int lvIdx = method.isStatic ? 0 : 1;

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

					if (n.index == lvIdx && n.start == firstInsn) {
						assert n.desc.equals(type.id);

						asmIndex = j;
						startInsn = il.indexOf(n.start);
						endInsn = il.indexOf(n.end);
						name = n.name;

						break;
					}
				}
			}

			MethodVarInstance arg = new MethodVarInstance(method, true, i, lvIdx, asmIndex, type, startInsn, endInsn, name, name == null || method.nameObfuscated || method.cls.nameObfuscated);
			args[i] = arg;

			method.classRefs.add(type);
			type.methodTypeRefs.add(method);

			lvIdx += type.getSlotSize();
		}

		return args;
	}

	private static MethodVarInstance[] gatherVars(MethodInstance method) {
		MethodNode asmNode = method.asmNode;
		if (asmNode == null) return emptyVars;
		if (asmNode.localVariables == null) return emptyVars; // TODO: generate?
		if (asmNode.localVariables.isEmpty()) return emptyVars;

		InsnList il = asmNode.instructions;
		AbstractInsnNode firstInsn = il.getFirst();
		List<LocalVariableNode> vars = new ArrayList<>();

		lvLoop: for (int i = 0; i < asmNode.localVariables.size(); i++) {
			LocalVariableNode var = asmNode.localVariables.get(i);

			if (var.start == firstInsn) { // check if it's an arg
				if (var.index == 0 && !method.isStatic) continue;

				for (MethodVarInstance arg : method.args) {
					if (arg.asmIndex == i) { // var is an arg
						continue lvLoop;
					}
				}
			}

			vars.add(var);
		}

		if (vars.isEmpty()) return emptyVars;

		// stable sort by start bci
		Collections.sort(vars, Comparator.comparingInt(var -> il.indexOf(var.start))); // Collections.sort is specified as stable, List.sort isn't (only implNote in OpenJDK 8)

		MethodVarInstance[] ret = new MethodVarInstance[vars.size()];

		for (int i = 0; i < vars.size(); i++) {
			LocalVariableNode var = vars.get(i);

			assert var.name != null;
			assert method.args.length == 0 || var.index > method.args[method.args.length - 1].lvIndex;

			ret[i] = new MethodVarInstance(method, false, i, var.index, asmNode.localVariables.indexOf(var),
					method.getEnv().getCreateClassInstance(var.desc), il.indexOf(var.start), il.indexOf(var.end),
					var.name, method.nameObfuscated || method.cls.nameObfuscated);
		}

		return ret;
	}

	@Override
	public String getDisplayName(NameType type, boolean full) {
		StringBuilder ret = new StringBuilder(64);
		ret.append(super.getDisplayName(type, full));
		ret.append('(');
		boolean first = true;

		for (MethodVarInstance arg : args) {
			if (first) {
				first = false;
			} else {
				ret.append(", ");
			}

			ret.append(arg.getType().getDisplayName(type, full));
		}

		ret.append(')');
		ret.append(retType.getDisplayName(type, full));

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

	public MethodVarInstance getVar(int index) {
		if (index < 0 || index >= vars.length) throw new IllegalArgumentException("invalid var index: "+index);

		return vars[index];
	}

	public MethodVarInstance getVar(String id) {
		return getVar(Integer.parseInt(id));
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

	public boolean hasAllArgsMapped() {
		for (MethodVarInstance arg : args) {
			if (!arg.hasMappedName()) return false;
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
