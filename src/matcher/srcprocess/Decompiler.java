package matcher.srcprocess;

import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;

public interface Decompiler {
	String decompile(ClassInstance cls, ClassFeatureExtractor extractor, boolean mapped, boolean tmpNamed, boolean unmatchedTmp);
}
