package matcher.srcprocess;

import matcher.NameType;
import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;

public interface Decompiler {
	String decompile(ClassInstance cls, ClassFeatureExtractor extractor, NameType nameType);
}
