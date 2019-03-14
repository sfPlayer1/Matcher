package matcher.mapping;

public interface IMappingAcceptor {
	void acceptClass(String srcName, String dstName, boolean includesOuterNames);
	void acceptClassComment(String srcName, String comment);
	void acceptMethod(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc);
	void acceptMethodComment(String srcClsName, String srcName, String srcDesc, String comment);
	void acceptMethodArg(String srcClsName, String srcMethodName, String srcMethodDesc, int argIndex, int lvIndex, String dstArgName);
	void acceptMethodVar(String srcClsName, String srcMethodName, String srcMethodDesc, int varIndex, int lvIndex, String dstVarName);
	void acceptField(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc);
	void acceptFieldComment(String srcClsName, String srcName, String srcDesc, String comment);
	void acceptMeta(String key, String value);
}