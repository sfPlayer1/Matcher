package matcher.mapping;

public interface IFieldMappingAcceptor {
	void acceptField(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc);
}