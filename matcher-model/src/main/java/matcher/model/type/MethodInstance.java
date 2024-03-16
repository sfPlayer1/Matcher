package matcher.model.type;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
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

import matcher.model.classifier.ClassifierUtil;
import matcher.model.NameType;
import matcher.model.Util;
import matcher.model.type.Signature.MethodSignature;

public final class MethodInstance extends MemberInstance<MethodInstance> implements ParentInstance {
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

		try {
			this.real = asmNode != null;
			this.access = asmNode != null ? asmNode.access : approximateAccess(isStatic);
			this.args = gatherArgs(this, desc, asmNode);
			this.vars = cls.isInput() ? gatherVars(this, asmNode) : emptyVars;
			this.retType = cls.getEnv().getCreateClassInstance(Type.getReturnType(desc).getDescriptor());
			this.signature = asmNode == null || asmNode.signature == null || !cls.isInput() ? null : MethodSignature.parse(asmNode.signature, cls.getEnv());
			this.asmNode = !cls.getEnv().isShared() ? asmNode : null;
		} catch (InvalidSharedEnvQueryException e) {
			throw e.checkOrigin(cls);
		}

		classRefs.add(retType);
		retType.methodTypeRefs.add(this);
	}

	private static int approximateAccess(boolean isStatic) {
		int ret = Opcodes.ACC_PUBLIC;
		if (isStatic) ret |= Opcodes.ACC_STATIC;

		return ret;
	}

	private static MethodVarInstance[] gatherArgs(MethodInstance method, String desc, MethodNode asmNode) {
		Type[] argTypes = Type.getArgumentTypes(desc);
		if (argTypes.length == 0) return emptyVars;

		MethodVarInstance[] args = new MethodVarInstance[argTypes.length];
		List<LocalVariableNode> locals;
		InsnList il;
		AbstractInsnNode firstInsn;

		if (asmNode != null) {
			locals = asmNode.localVariables;
			il = asmNode.instructions;
			firstInsn = il.getFirst();
		} else {
			locals = null;
			il = null;
			firstInsn = null;
		}

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

			MethodVarInstance arg = new MethodVarInstance(method, true, i, lvIdx, asmIndex,
					type, startInsn, endInsn, 0,
					name,
					name == null || method.nameObfuscatedLocal || method.cls.nameObfuscated || !Util.isValidJavaIdentifier(name));
			args[i] = arg;

			method.classRefs.add(type);
			type.methodTypeRefs.add(method);

			lvIdx += type.getSlotSize();
		}

		return args;
	}

	private static MethodVarInstance[] gatherVars(MethodInstance method, MethodNode asmNode) {
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

			int startInsn = il.indexOf(var.start);
			int endInsn = il.indexOf(var.end);
			assert startInsn >= 0 && endInsn >= 0;

			AbstractInsnNode start = var.start;
			int startOpIdx = 0;

			while ((start = start.getPrevious()) != null) {
				if (start.getOpcode() >= 0) startOpIdx++;
			}

			ret[i] = new MethodVarInstance(method, false, i, var.index, asmNode.localVariables.indexOf(var),
					method.getEnv().getCreateClassInstance(var.desc), startInsn, endInsn, startOpIdx,
					var.name,
					var.name == null || method.nameObfuscatedLocal || method.cls.nameObfuscated || !Util.isValidJavaIdentifier(var.name));
		}

		return ret;
	}

	@Override
	public MatchableKind getKind() {
		return MatchableKind.METHOD;
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
		return getDesc(NameType.PLAIN);
	}

	@Override
	public String getDesc(NameType type) {
		int descStart = id.indexOf('(');
		if (type == NameType.PLAIN || id.indexOf('L', descStart + 1) < 0) return id.substring(descStart);

		StringBuilder ret = new StringBuilder(id.length());
		ret.append('(');

		for (MethodVarInstance arg : args) {
			if (!appendId(arg.getType(), type, ret)) return null;
		}

		ret.append(')');
		if (!appendId(retType, type, ret)) return null;

		return ret.toString();
	}

	private static boolean appendId(ClassInstance cls, NameType type, StringBuilder out) {
		if (type == NameType.PLAIN || cls.isPrimitive()) {
			out.append(cls.id);
		} else {
			String name = cls.getName(type);
			if (name == null) return false;

			if (cls.isArray()) {
				out.append(name);
			} else {
				out.append('L');
				out.append(name);
				out.append(';');
			}
		}

		return true;
	}

	@Override
	public boolean isReal() {
		return real;
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

	public MethodVarInstance getArgOrVar(int lvIndex, int pos) {
		return getArgOrVar(lvIndex, pos, pos + 1);
	}

	public MethodVarInstance getArgOrVar(int lvIndex, int start, int end) {
		if (args.length > 0 && lvIndex <= args[args.length - 1].getLvIndex()) {
			for (int i = 0; i < args.length; i++) {
				MethodVarInstance arg = args[i];

				if (arg.getLvIndex() == lvIndex) {
					assert arg.getStartInsn() < 0
					|| start < arg.getEndInsn() && end > arg.getStartInsn()
					|| arg.getStartInsn() < end && arg.getEndInsn() > start;

					return arg;
				}
			}
		} else {
			MethodVarInstance candidate = null;
			boolean conflict = false;

			for (int i = 0; i < vars.length; i++) {
				MethodVarInstance var = vars[i];
				if (var.getLvIndex() != lvIndex) continue;

				if (start < var.getEndInsn() && end > var.getStartInsn()) { // requested interval within var interval (assumes matcher's interval never too loose)
					return var;
				} else if (var.getStartInsn() < end && var.getEndInsn() > start) { // var interval within requested interval, only allow unique match
					if (candidate != null) {
						conflict = true;
					} else {
						candidate = var;
					}
				}
			}

			if (candidate != null && !conflict) {
				return candidate;
			}
		}

		return null;
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

	@Override
	public boolean hasMappedChildren() {
		for (MethodVarInstance arg : args) {
			if (arg.hasMappedName()) return true;
		}

		for (MethodVarInstance var : vars) {
			if (var.hasMappedName()) return true;
		}

		return false;
	}

	public boolean hasNonInheritedMappedName() {
		return !hasParentMethod() && hasMappedName();
	}

	public boolean hasParentMethod() {
		if (hasParentMethod != null) return hasParentMethod;

		return hasParentMethod = checkAncestry(AncestryCheck.HAS_PARENT_METHOD);
	}

	public boolean isAnyInputRoot() {
		if (anyInputRoot != null) return anyInputRoot;

		return anyInputRoot = checkAncestry(AncestryCheck.IS_ANY_INPUT_ROOT);
	}

	enum AncestryCheck {
		HAS_PARENT_METHOD,
		IS_ANY_INPUT_ROOT
	}

	private boolean checkAncestry(AncestryCheck checkType) {
		// check if each origin that supplies this method has a parent within the same origin

		for (int i = 0; i < cls.getAsmNodes().length; i++) {
			for (MethodNode m : cls.getAsmNodes()[i].methods) {
				if (m.name.equals(getName()) && m.desc.equals(getDesc())) {
					boolean parentFound = hasParentMethod(getParents(), cls.getAsmNodeOrigin(i));

					if (parentFound && checkType == AncestryCheck.HAS_PARENT_METHOD) {
						return true;
					} else if (parentFound && checkType == AncestryCheck.IS_ANY_INPUT_ROOT) {
						break;
					} else if (!parentFound && checkType == AncestryCheck.IS_ANY_INPUT_ROOT) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private boolean hasParentMethod(Collection<MethodInstance> parents, URI reqOrigin) {
		// check direct parents (must supply the method from the required origin)

		for (MethodInstance parent : parents) {
			ClassInstance parentCls = parent.getCls();

			if (parentCls.getAsmNodes() == null) {
				continue;
			}

			for (int i = 0; i < parentCls.getAsmNodes().length; i++) {
				if (parentCls.getAsmNodeOrigin(i).equals(reqOrigin)) {
					for (MethodNode m : parentCls.getAsmNodes()[i].methods) {
						if (m.name.equals(getName()) && m.desc.equals(getDesc())) {
							return true;
						}
					}
				}
			}
		}

		// check indirect parents recursively

		for (MethodInstance parent : parents) {
			if (!parent.getParents().isEmpty() && hasParentMethod(parent.getParents(), reqOrigin)) {
				return true;
			}
		}

		return false;
	}

	public ClassInstance getRetType() {
		return retType;
	}

	@Override
	public int getAccess() {
		return access;
	}

	public MethodSignature getSignature() {
		return signature;
	}

	@Override
	public boolean canBeRecordComponent() {
		return cls.isRecord() && !isStatic() && isPublic() && args.length == 0 && !retType.id.equals("V");
	}

	@Override
	public FieldInstance getLinkedRecordComponent(NameType nameType) {
		if (!canBeRecordComponent()) return null;

		String name = nameType != null ? getName(nameType) : null;
		FieldInstance ret = null;

		for (FieldInstance field : cls.getFields()) {
			if (field.canBeRecordComponent()
					&& field.getType().equals(retType)
					&& (name == null || name.equals(field.getName(nameType)))
					&& (ret == null || !fieldReadRefs.contains(ret) && fieldReadRefs.contains(field))) {
				ret = field;
			}
		}

		return ret;
	}

	public boolean isBridge() {
		return (getAccess() & Opcodes.ACC_BRIDGE) != 0;
	}

	public MethodType getType() {
		return type;
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

		return cls.env.getGlobal().methodUidPrefix+uid;
	}

	@Override
	public boolean hasPotentialMatch() {
		if (matchedInstance != null) return true;
		if (!cls.hasMatch() || !isMatchable()) return false;

		for (MethodInstance o : cls.getMatch().getMethods()) {
			if (ClassifierUtil.checkPotentialEquality(this, o)) return true;
		}

		return false;
	}

	@Override
	public boolean isFullyMatched(boolean recursive) {
		if (matchedInstance == null) return false;

		boolean anyUnmatched = false;

		for (MethodVarInstance v : args) {
			if (!v.hasMatch()) {
				anyUnmatched = true;
				break;
			}
		}

		if (anyUnmatched) {
			for (MethodVarInstance a : args) {
				if (a.hasMatch()) continue;

				// check for any potential match to ignore methods that are impossible to match
				for (MethodVarInstance b : matchedInstance.args) {
					if (!b.hasMatch() && ClassifierUtil.checkPotentialEquality(a, b)) {
						return false;
					}
				}
			}
		}

		anyUnmatched = false;

		for (MethodVarInstance v : vars) {
			if (!v.hasMatch()) {
				anyUnmatched = true;
				break;
			}
		}

		if (anyUnmatched) {
			for (MethodVarInstance a : vars) {
				if (a.hasMatch()) continue;

				for (MethodVarInstance b : matchedInstance.vars) {
					if (!b.hasMatch() && ClassifierUtil.checkPotentialEquality(a, b)) {
						return false;
					}
				}
			}
		}

		return true;
	}

	public static String getId(String name, String desc) {
		return name+desc;
	}

	private static final MethodVarInstance[] emptyVars = new MethodVarInstance[0];

	final boolean real;
	final int access;
	final MethodVarInstance[] args;
	final ClassInstance retType;
	MethodVarInstance[] vars;
	final MethodSignature signature;
	private final MethodNode asmNode;

	Boolean hasParentMethod;
	Boolean anyInputRoot;
	MethodType type = MethodType.UNKNOWN;

	final Set<MethodInstance> refsIn = Util.newIdentityHashSet();
	final Set<MethodInstance> refsOut = Util.newIdentityHashSet();
	final Set<FieldInstance> fieldReadRefs = Util.newIdentityHashSet();
	final Set<FieldInstance> fieldWriteRefs = Util.newIdentityHashSet();
	final Set<ClassInstance> classRefs = Util.newIdentityHashSet();
}
