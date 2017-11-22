package matcher.type;

public interface IMatchable<T> {
	String getId();
	String getName();

	default String getDisplayName(boolean full, boolean mapped) {
		return getName();
	}

	IClassEnv getEnv();
	String getMappedName(boolean defaultToUnmapped);

	default boolean hasMatch() {
		return getMatch() != null;
	}

	T getMatch();
	boolean isNameObfuscated(boolean recursive);
}