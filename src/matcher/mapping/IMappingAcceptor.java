package matcher.mapping;

public interface IMappingAcceptor {
	void acceptClass(String srcName, String dstName);
	void acceptClassComment(String srcName, String comment);
	void acceptMethod(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc);
	void acceptMethodComment(String srcClsName, String srcName, String srcDesc, String comment);
	void acceptMethodArg(String srcClsName, String srcName, String srcDesc, int argIndex, int lvtIndex, String dstArgName);
	void acceptField(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc);
	void acceptFieldComment(String srcClsName, String srcName, String srcDesc, String comment);
}