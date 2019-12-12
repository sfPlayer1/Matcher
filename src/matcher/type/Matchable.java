package matcher.type;

import matcher.NameType;

public interface Matchable<T> {
	String getId();
	String getName();
	String getName(NameType type);

	default String getDisplayName(NameType type, boolean full) {
		return getName(type);
	}

	boolean hasMappedName();
	boolean hasLocalTmpName();

	Matchable<?> getOwner();
	ClassEnv getEnv();

	int getUid();

	default boolean hasMatch() {
		return getMatch() != null;
	}

	T getMatch();
	boolean isFullyMatched(boolean recursive);
	float getSimilarity();
	boolean isNameObfuscated();
}