package matcher.srcprocess;

import matcher.NameType;
import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;

class NoOp implements Decompiler {
	@Override
	public String decompile(ClassInstance cls, ClassFeatureExtractor env, NameType nameType) {
		return "decompiler disabled";
	}
}
