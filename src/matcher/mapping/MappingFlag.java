package matcher.mapping;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum MappingFlag {
	/**
	 * Indication that the visitor may require multiple passes.
	 */
	NEEDS_MULTIPLE_PASSES,
	/**
	 * Requirement that an element has to be visited only once within a pass.
	 *
	 * <p>This means that e.g. all members and properties of a class have to be visited after the same single
	 * visitClass invocation and no other visitClass invocation with the same srcName may occur.
	 */
	NEEDS_UNIQUENESS,
	/**
	 * Requirement that source field descriptors have to be supplied.
	 */
	NEEDS_SRC_FIELD_DESC,
	/**
	 * Requirement that source method descriptors have to be supplied.
	 */
	NEEDS_SRC_METHOD_DESC,
	/**
	 * Requirement that destination field descriptors have to be supplied.
	 */
	NEEDS_DST_FIELD_DESC,
	/**
	 * Requirement that destination method descriptors have to be supplied.
	 */
	NEEDS_DST_METHOD_DESC;

	public static final Set<MappingFlag> NONE = Collections.unmodifiableSet(EnumSet.noneOf(MappingFlag.class));
}
