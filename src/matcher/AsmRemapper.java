package matcher;

import org.objectweb.asm.commons.Remapper;

import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MethodInstance;

public class AsmRemapper extends Remapper {
	public AsmRemapper(ClassFeatureExtractor extractor) {
		this.extractor = extractor;
	}

	@Override
	public String map(String typeName) {
		ClassInstance cls = extractor.getClassInstance(typeName);
		String ret;

		return cls != null && (ret = cls.getMappedName()) != null ? ret : typeName;
	}

	@Override
	public String mapFieldName(String owner, String name, String desc) {
		ClassInstance cls = extractor.getClassInstance(owner);
		if (cls == null) return name;

		FieldInstance field = cls.getField(name, desc);
		String ret;

		return field != null && (ret = field.getMappedName()) != null ? ret : name;
	}

	@Override
	public String mapMethodName(String owner, String name, String desc) {
		ClassInstance cls = extractor.getClassInstance(owner);
		if (cls == null) return name;

		MethodInstance method = cls.getMethod(name, desc);
		String ret;

		return method != null && (ret = method.getMappedName()) != null ? ret : name;
	}

	public String mapLambdaInvokeDynamicMethodName(String owner, String name, String desc) {
		return mapMethodName(owner, name, desc);
	}

	public String mapArbitraryInvokeDynamicMethodName(String owner, String name) {
		ClassInstance cls = extractor.getClassInstance(owner);
		if (cls == null) return name;

		MethodInstance method = cls.getMethod(name, null);
		String ret;

		return method != null && (ret = method.getMappedName()) != null ? ret : name;
	}

	private final ClassFeatureExtractor extractor;
}
