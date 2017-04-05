package matcher.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import matcher.Util;

public class MethodInstance extends MemberInstance<MethodInstance> {
	MethodInstance(ClassInstance cls, String origName, String desc, MethodNode asmNode, int position, ClassFeatureExtractor extractor) {
		super(cls, getId(origName, desc), origName, !origName.equals("<clinit>") && !origName.equals("<init>"), position);

		this.asmNode = asmNode;

		Type[] argTypes = Type.getArgumentTypes(desc);
		List<ClassInstance> args;

		if (argTypes.length == 0) {
			args = Collections.emptyList();
		} else {
			args = new ArrayList<>();

			for (Type type : argTypes) {
				ClassInstance typeCls = extractor.getCreateClassInstance(type.getDescriptor());
				args.add(typeCls);
				classRefs.add(typeCls);
				typeCls.methodTypeRefs.add(this);
			}
		}

		this.args = args;
		this.retType = extractor.getCreateClassInstance(Type.getReturnType(desc).getDescriptor());
		classRefs.add(retType);
		retType.methodTypeRefs.add(this);
	}

	@Override
	public String getName() {
		String ret = origName+"(";
		boolean first = true;

		for (ClassInstance arg : args) {
			if (first) {
				first = false;
			} else {
				ret += ", ";
			}

			ret += arg.getName();
		}

		ret += ")"+retType.getName();

		return ret;
	}

	@Override
	public String getDesc() {
		String ret = "(";

		for (ClassInstance arg : args) {
			ret += arg.id;
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

	public List<ClassInstance> getArgs() {
		return args;
	}

	public ClassInstance getRetType() {
		return retType;
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

	public String getMappedArgName(int idx) {
		if (idx < 0 || idx >= args.size()) throw new IllegalArgumentException("invalid arg idx: "+idx);
		if (mappedArgNames == null) return null;

		return mappedArgNames[idx];
	}

	public void setMappedArgName(int idx, String name) {
		if (idx < 0 || idx >= args.size()) throw new IllegalArgumentException("invalid arg idx: "+idx);

		if (name != null) {
			if (mappedArgNames == null) mappedArgNames = new String[args.size()];

			mappedArgNames[idx] = name;
		} else if (mappedArgNames != null) {
			mappedArgNames[idx] = null;

			for (int i = 0; i < mappedArgNames.length; i++) {
				if (mappedArgNames[i] != null) return;
			}

			mappedArgNames = null;
		}
	}

	public void clearMappedArgNames() {
		mappedArgNames = null;
	}

	static String getId(String name, String desc) {
		return name+desc;
	}

	final MethodNode asmNode;
	final List<ClassInstance> args;
	final ClassInstance retType;

	final Set<MethodInstance> refsIn = Util.newIdentityHashSet();
	final Set<MethodInstance> refsOut = Util.newIdentityHashSet();
	final Set<FieldInstance> fieldReadRefs = Util.newIdentityHashSet();
	final Set<FieldInstance> fieldWriteRefs = Util.newIdentityHashSet();
	final Set<ClassInstance> classRefs = Util.newIdentityHashSet();

	String[] mappedArgNames;
}