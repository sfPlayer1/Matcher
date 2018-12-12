package matcher.bcremap;

import org.objectweb.asm.commons.Remapper;

import matcher.type.ClassEnv;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class AsmRemapper extends Remapper {
	public AsmRemapper(ClassEnv env, boolean mapped, boolean tmpNamed, boolean unmatchedTmp) {
		this.env = env;
		this.mapped = mapped;
		this.tmpNamed = tmpNamed;
		this.unmatchedTmp = unmatchedTmp;
	}

	@Override
	public String map(String typeName) {
		ClassInstance cls = env.getClsByName(typeName);
		if (cls == null) return typeName;

		return cls.getName(mapped, tmpNamed, unmatchedTmp);
	}

	@Override
	public String mapFieldName(String owner, String name, String desc) {
		ClassInstance cls = env.getClsByName(owner);
		if (cls == null) return name;

		FieldInstance field = cls.resolveField(name, desc);
		if (field == null) return name;

		return field.getName(mapped, tmpNamed, unmatchedTmp);
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

		return method.getName(mapped, tmpNamed, unmatchedTmp);
	}

	public String mapMethodName(String owner, String name, String desc, boolean itf) {
		ClassInstance cls = env.getClsByName(owner);
		if (cls == null) return name;

		MethodInstance method = cls.resolveMethod(name, desc, itf);
		if (method == null) return name;

		return method.getName(mapped, tmpNamed, unmatchedTmp);
	}

	public String mapArbitraryInvokeDynamicMethodName(String owner, String name) {
		ClassInstance cls = env.getClsByName(owner);
		if (cls == null) return name;

		MethodInstance method = cls.getMethod(name, null);
		if (method == null) return name;

		return method.getName(mapped, tmpNamed, unmatchedTmp);
	}

	public String mapLocalVariableName(String className, String methodName, String methodDesc, String name, String desc, int lvIndex, int startInsn, int endInsn) {
		ClassInstance cls = env.getClsByName(className);
		if (cls == null) return name;

		MethodInstance method = cls.getMethod(methodName, methodDesc);
		if (method == null) return name;

		for (MethodVarInstance var : method.getArgs()) { // TODO: iterate all method vars once available
			if (var.getLvIndex() == lvIndex && var.getEndInsn() > startInsn && var.getStartInsn() < endInsn) {
				assert var.getType().getId().equals(desc);

				return var.getName(mapped, tmpNamed, unmatchedTmp);
			}
		}

		return name;
	}

	private final ClassEnv env;
	private final boolean mapped;
	private final boolean tmpNamed;
	private final boolean unmatchedTmp;
}
