package matcher.mapping;

import java.util.List;
import java.util.Set;

public interface FlatMappingVisitor {
	default Set<MappingFlag> getFlags() {
		return MappingFlag.NONE;
	}

	default void reset() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Determine whether the header (namespaces, metadata if part of the header) should be visited
	 *
	 * @return true if the header is to be visited, false otherwise
	 */
	default boolean visitHeader() {
		return true;
	}

	void visitNamespaces(String srcNamespace, List<String> dstNamespaces);

	default void visitMetadata(String key, String value) { }

	/**
	 * Determine whether the mapping content (classes and anything below, metadata if not part of the header) should be visited
	 *
	 * @return true if content is to be visited, false otherwise
	 */
	default boolean visitContent() {
		return true;
	}

	boolean visitClass(String srcName, String[] dstNames);
	void visitClassComment(String srcName, String[] dstNames, String comment);

	boolean visitField(String srcClsName, String srcName, String srcDesc,
			String[] dstClsNames, String[] dstNames, String[] dstDescs);
	void visitFieldComment(String srcClsName, String srcName, String srcDesc,
			String[] dstClsNames, String[] dstNames, String[] dstDescs,
			String comment);

	boolean visitMethod(String srcClsName, String srcName, String srcDesc,
			String[] dstClsNames, String[] dstNames, String[] dstDescs);
	void visitMethodComment(String srcClsName, String srcName, String srcDesc,
			String[] dstClsNames, String[] dstNames, String[] dstDescs,
			String comment);

	boolean visitMethodArg(String srcClsName, String srcMethodName, String srcMethodDesc,
			int argPosition, int lvIndex, String srcArgName,
			String[] dstClsNames, String[] dstMethodNames, String[] dstMethodDescs, String[] dstArgNames);
	void visitMethodArgComment(String srcClsName, String srcMethodName, String srcMethodDesc,
			int argPosition, int lvIndex, String srcArgName,
			String[] dstClsNames, String[] dstMethodNames, String[] dstMethodDescs, String[] dstArgNames,
			String comment);

	boolean visitMethodVar(String srcClsName, String srcMethodName, String srcMethodDesc,
			int lvtRowIndex, int lvIndex, int startOpIdx, String srcVarName,
			String[] dstClsNames, String[] dstMethodNames, String[] dstMethodDescs, String[] dstVarNames);
	void visitMethodVarComment(String srcClsName, String srcMethodName, String srcMethodDesc,
			int lvtRowIndex, int lvIndex, int startOpIdx, String srcVarName,
			String[] dstClsNames, String[] dstMethodNames, String[] dstMethodDescs, String[] dstVarNames,
			String comment);

	/**
	 * Finish the visitation pass.
	 * @return true if the visitation pass is final, false if it should be started over
	 */
	default boolean visitEnd() {
		return true;
	}

	// regular <-> flat visitor adaptation methods

	default MappingVisitor asMethodVisitor() {
		return new FlatAsRegularMappingVisitor(this);
	}

	static FlatMappingVisitor fromMethodVisitor(MappingVisitor visitor) {
		return new RegularAsFlatMappingVisitor(visitor);
	}

	// convenience visit methods without extra dst context

	default boolean visitField(String srcClsName, String srcName, String srcDesc, String[] dstNames) {
		return visitField(srcClsName, srcName, srcDesc, null, dstNames, null);
	}

	default boolean visitMethod(String srcClsName, String srcName, String srcDesc, String[] dstNames) {
		return visitMethod(srcClsName, srcName, srcDesc, null, dstNames, null);
	}

	default boolean visitMethodArg(String srcClsName, String srcMethodName, String srcMethodDesc,
			int argPosition, int lvIndex, String srcArgName,
			String[] dstArgNames) {
		return visitMethodArg(srcClsName, srcMethodName, srcMethodDesc,
				argPosition, lvIndex, srcArgName,
				null, null, null,
				dstArgNames);
	}

	default boolean visitMethodVar(String srcClsName, String srcMethodName, String srcMethodDesc,
			int lvtRowIndex, int lvIndex, int startOpIdx, String srcVarName,
			String[] dstVarNames) {
		return visitMethodVar(srcClsName, srcMethodName, srcMethodDesc,
				lvtRowIndex, lvIndex, startOpIdx, srcVarName,
				null, null, null,
				dstVarNames);
	}

	// convenience / potentially higher efficiency visit methods for only one dst name

	default boolean visitClass(String srcName, String dstName) {
		return visitClass(srcName, toArray(dstName));
	}

	default void visitClassComment(String srcName, String comment) {
		visitClassComment(srcName, (String) null, comment);
	}

	default void visitClassComment(String srcName, String dstName, String comment) {
		visitClassComment(srcName, toArray(dstName), comment);
	}

	default boolean visitField(String srcClsName, String srcName, String srcDesc,
			String dstName) {
		return visitField(srcClsName, srcName, srcDesc,
				null, dstName, null);
	}

	default boolean visitField(String srcClsName, String srcName, String srcDesc,
			String dstClsName, String dstName, String dstDesc) {
		return visitField(srcClsName, srcName, srcDesc,
				toArray(dstClsName), toArray(dstName), toArray(dstDesc));
	}

	default void visitFieldComment(String srcClsName, String srcName, String srcDesc,
			String comment) {
		visitFieldComment(srcClsName, srcName, srcDesc,
				(String) null, null, null,
				comment);
	}

	default void visitFieldComment(String srcClsName, String srcName, String srcDesc,
			String dstClsName, String dstName, String dstDesc,
			String comment) {
		visitFieldComment(srcClsName, srcName, srcDesc,
				toArray(dstClsName), toArray(dstName), toArray(dstDesc),
				comment);
	}

	default boolean visitMethod(String srcClsName, String srcName, String srcDesc,
			String dstName) {
		return visitMethod(srcClsName, srcName, srcDesc,
				null, dstName, null);
	}

	default boolean visitMethod(String srcClsName, String srcName, String srcDesc,
			String dstClsName, String dstName, String dstDesc) {
		return visitMethod(srcClsName, srcName, srcDesc,
				toArray(dstClsName), toArray(dstName), toArray(dstDesc));
	}

	default void visitMethodComment(String srcClsName, String srcName, String srcDesc,
			String comment) {
		visitMethodComment(srcClsName, srcName, srcDesc,
				(String) null, null, null,
				comment);
	}

	default void visitMethodComment(String srcClsName, String srcName, String srcDesc,
			String dstClsName, String dstName, String dstDesc,
			String comment) {
		visitMethodComment(srcClsName, srcName, srcDesc,
				toArray(dstClsName), toArray(dstName), toArray(dstDesc),
				comment);
	}

	default boolean visitMethodArg(String srcClsName, String srcMethodName, String srcMethodDesc,
			int argPosition, int lvIndex, String srcArgName,
			String dstArgName) {
		return visitMethodArg(srcClsName, srcMethodName, srcMethodDesc,
				argPosition, lvIndex, srcArgName,
				null, null, null, dstArgName);
	}

	default boolean visitMethodArg(String srcClsName, String srcMethodName, String srcMethodDesc,
			int argPosition, int lvIndex, String srcArgName,
			String dstClsName, String dstMethodName, String dstMethodDesc, String dstArgName) {
		return visitMethodArg(srcClsName, srcMethodName, srcMethodDesc,
				argPosition, lvIndex, srcArgName,
				toArray(dstClsName), toArray(dstMethodName), toArray(dstMethodDesc), toArray(dstArgName));
	}

	default void visitMethodArgComment(String srcClsName, String srcMethodName, String srcMethodDesc,
			int argPosition, int lvIndex, String srcArgName,
			String comment) {
		visitMethodArgComment(srcClsName, srcMethodName, srcMethodDesc,
				argPosition, lvIndex, srcArgName,
				(String) null, null, null, null,
				comment);
	}

	default void visitMethodArgComment(String srcClsName, String srcMethodName, String srcMethodDesc,
			int argPosition, int lvIndex, String srcArgName,
			String dstClsName, String dstMethodName, String dstMethodDesc, String dstArgName,
			String comment) {
		visitMethodArgComment(srcClsName, srcMethodName, srcMethodDesc, argPosition, lvIndex, srcArgName,
				toArray(dstClsName), toArray(dstMethodName), toArray(dstMethodDesc), toArray(dstArgName),
				comment);
	}

	default boolean visitMethodVar(String srcClsName, String srcMethodName, String srcMethodDesc,
			int lvtRowIndex, int lvIndex, int startOpIdx, String srcVarName,
			String dstVarName) {
		return visitMethodVar(srcClsName, srcMethodName, srcMethodDesc,
				lvtRowIndex, lvIndex, startOpIdx, srcVarName,
				null, null, null, dstVarName);
	}

	default boolean visitMethodVar(String srcClsName, String srcMethodName, String srcMethodDesc,
			int lvtRowIndex, int lvIndex, int startOpIdx, String srcVarName,
			String dstClsName, String dstMethodName, String dstMethodDesc, String dstVarName) {
		return visitMethodVar(srcClsName, srcMethodName, srcMethodDesc,
				lvtRowIndex, lvIndex, startOpIdx, srcVarName,
				toArray(dstClsName), toArray(dstMethodName), toArray(dstMethodDesc), toArray(dstVarName));
	}

	default void visitMethodVarComment(String srcClsName, String srcMethodName, String srcMethodDesc,
			int lvtRowIndex, int lvIndex, int startOpIdx, String srcVarName,
			String comment) {
		visitMethodVarComment(srcClsName, srcMethodName, srcMethodDesc,
				lvtRowIndex, lvIndex, startOpIdx, srcVarName,
				(String) null, null, null, null,
				comment);
	}

	default void visitMethodVarComment(String srcClsName, String srcMethodName, String srcMethodDesc,
			int lvtRowIndex, int lvIndex, int startOpIdx, String srcVarName,
			String dstClsName, String dstMethodName, String dstMethodDesc, String dstVarName,
			String comment) {
		visitMethodVarComment(srcClsName, srcMethodName, srcMethodDesc,
				lvtRowIndex, lvIndex, startOpIdx, srcVarName,
				toArray(dstClsName), toArray(dstMethodName), toArray(dstMethodDesc), toArray(dstVarName),
				comment);
	}

	private static String[] toArray(String s) {
		return s != null ? new String[] { s } : null;
	}
}
