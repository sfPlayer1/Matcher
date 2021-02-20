package matcher.type;

import matcher.NameType;

public interface Matchable<T extends Matchable> {
	String getId();
	String getName();
	String getName(NameType type);

	default String getDisplayName(NameType type, boolean full) {
		return getName(type);
	}

	boolean hasMappedName();
	boolean hasLocalTmpName();
	boolean hasAuxName(int index);

	Matchable<?> getOwner();
	ClassEnv getEnv();

	int getUid();

	boolean isMatchable();
	void setMatchable(boolean matchable);

	default boolean hasMatch() {
		return getMatch() != null;
	}

	T getMatch();
	boolean isFullyMatched(boolean recursive);
	float getSimilarity();
	boolean isNameObfuscated();
}