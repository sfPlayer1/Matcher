package matcher.gui.srcprocess;

import matcher.model.NameType;
import matcher.model.type.ClassEnvironment;
import matcher.model.type.ClassFeatureExtractor;
import matcher.model.type.ClassInstance;

public interface Decompiler {
	static String decompile(ClassEnvironment env, Decompiler decompiler, ClassInstance cls, NameType nameType) {
		ClassFeatureExtractor extractorA = (ClassFeatureExtractor) env.getEnvA();
		ClassFeatureExtractor extractorB = (ClassFeatureExtractor) env.getEnvB();
		ClassFeatureExtractor extractor;

		if (extractorA.getLocalClsById(cls.getId()) == cls) {
			extractor = extractorA;
		} else if (extractorB.getLocalClsById(cls.getId()) == cls) {
			extractor = extractorB;
		} else {
			throw new IllegalArgumentException("unknown class: "+cls);
		}

		return decompiler.decompile(cls, extractor, nameType);
	}

	String decompile(ClassInstance cls, ClassFeatureExtractor extractor, NameType nameType);
}
