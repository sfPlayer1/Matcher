package matcher.mapping;

import java.util.List;
import java.util.Set;

public interface MappingVisitor {
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

	boolean visitClass(String srcName);
	boolean visitField(String srcName, String srcDesc);
	boolean visitMethod(String srcName, String srcDesc);
	boolean visitMethodArg(int argPosition, int lvIndex, String srcName);
	boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, String srcName);

	/**
	 * Finish the visitation pass.
	 * @return true if the visitation pass is final, false if it should be started over
	 */
	default boolean visitEnd() {
		return true;
	}

	/**
	 * Destination name for the current element.
	 *
	 * @param namespace namespace index, index into the dstNamespaces List in {@link #visitNamespaces}
	 * @param name destination name
	 */
	void visitDstName(MappedElementKind targetKind, int namespace, String name);

	default void visitDstDesc(MappedElementKind targetKind, int namespace, String desc) { }

	/**
	 * Determine whether the element content (comment, sub-elements) should be visited.
	 *
	 * <p>This is also a notification about all available dst names having been passed on.
	 *
	 * @return true if the contents are to be visited, false otherwise
	 */
	default boolean visitElementContent(MappedElementKind targetKind) {
		return true;
	}

	/**
	 * Comment for the specified element (last content-visited or any parent).
	 *
	 * @param comment comment as a potentially multi-line string
	 */
	void visitComment(MappedElementKind targetKind, String comment);
}
