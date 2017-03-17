package matcher.mapping;

public interface IMethodMappingAcceptor {
	void acceptMethod(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc);
}