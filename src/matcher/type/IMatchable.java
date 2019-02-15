package matcher.type;

import matcher.NameType;

public interface IMatchable<T> {
	String getId();
	String getName();
	String getName(NameType type);

	default String getDisplayName(NameType type, boolean full) {
		return getName(type);
	}

	boolean hasMappedName();
	boolean hasLocalTmpName();

	ClassEnv getEnv();

	int getUid();

	default boolean hasMatch() {
		return getMatch() != null;
	}

	T getMatch();
	boolean isNameObfuscated();
}