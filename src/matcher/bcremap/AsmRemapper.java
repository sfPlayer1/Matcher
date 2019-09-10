package matcher.bcremap;

import org.objectweb.asm.commons.Remapper;

import matcher.NameType;
import matcher.type.ClassEnv;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class AsmRemapper extends Remapper {
	public AsmRemapper(ClassEnv env, NameType nameType) {
		this.env = env;
		this.nameType = nameType;
	}

	@Override
	public String map(String typeName) {
		ClassInstance cls = env.getClsByName(typeName);
		if (cls == null) return typeName;

		return cls.getName(nameType);
	}

	@Override
	public String mapFieldName(String owner, String name, String desc) {
		ClassInstance cls = env.getClsByName(owner);
		if (cls == null) return name;

		FieldInstance field = cls.resolveField(name, desc);
		if (field == null) return name;

		return field.getName(nameType);
	}

	@Override
	public String mapMethodName(String owner, String name, String desc) {
		ClassInstance cls = env.getClsByName(owner);
		if (cls == null) return name;

		MethodInstance method = cls.getMethod(name, desc);

		if (method == null) {
			assert false : String.format("can't find method %s%s in %s", name, desc, cls);;
			return name;
		}

		return method.getName(nameType);
	}

	public String mapMethodName(String owner, String name, String desc, boolean itf) {
		ClassInstance cls = env.getClsByName(owner);
		if (cls == null) return name;

		MethodInstance method = cls.resolveMethod(name, desc, itf);
		if (method == null) return name;

		return method.getName(nameType);
	}

	public String mapArbitraryInvokeDynamicMethodName(String owner, String name) {
		ClassInstance cls = env.getClsByName(owner);
		if (cls == null) return name;

		MethodInstance method = cls.getMethod(name, null);
		if (method == null) return name;

		return method.getName(nameType);
	}

	public String mapArgName(String className, String methodName, String methodDesc, String name, int asmIndex) {
		ClassInstance cls = env.getClsByName(className);
		if (cls == null) return name;

		MethodInstance method = cls.getMethod(methodName, methodDesc);
		if (method == null) return name;

		return method.getArg(asmIndex).getName(nameType);
	}

	public String mapLocalVariableName(String className, String methodName, String methodDesc, String name, String desc, int lvIndex, int startInsn, int endInsn) {
		ClassInstance cls = env.getClsByName(className);
		if (cls == null) return name;

		MethodInstance method = cls.getMethod(methodName, methodDesc);
		if (method == null) return name;

		MethodVarInstance var = method.getArgOrVar(lvIndex, startInsn, endInsn);

		if (var != null) {
			assert var.getType().getId().equals(desc);

			name = var.getName(nameType);
		}

		return name;
	}

	private final ClassEnv env;
	private final NameType nameType;
}
