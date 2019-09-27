package matcher.mapping;

public interface MappingAcceptor {
	void acceptClass(String srcName, String dstName, boolean includesOuterNames);
	void acceptClassComment(String srcName, String comment);
	void acceptMethod(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc);
	void acceptMethodComment(String srcClsName, String srcName, String srcDesc, String comment);
	void acceptMethodArg(String srcClsName, String srcMethodName, String srcMethodDesc, int argIndex, int lvIndex, String srcArgName, String dstArgName);
	void acceptMethodArgComment(String srcClsName, String srcMethodName, String srcMethodDesc, int argIndex, int lvIndex, String comment);
	void acceptMethodVar(String srcClsName, String srcMethodName, String srcMethodDesc, int varIndex, int lvIndex, int startOpIdx, int asmIndex, String srcVarName, String dstVarName);
	void acceptMethodVarComment(String srcClsName, String srcMethodName, String srcMethodDesc, int varIndex, int lvIndex, int startOpIdx, int asmIndex, String comment);
	void acceptField(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc);
	void acceptFieldComment(String srcClsName, String srcName, String srcDesc, String comment);
	void acceptMeta(String key, String value);
}