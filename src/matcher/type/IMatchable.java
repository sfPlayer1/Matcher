package matcher.type;

public interface IMatchable<T> {
	String getId();
	String getName();

	default String getDisplayName(boolean full, boolean mapped, boolean tmpNamed, boolean unmatchedTmp) {
		return getName();
	}

	ClassEnv getEnv();

	default String getName(boolean mapped, boolean tmpNamed, boolean unmatchedTmp) {
		if (mapped) {
			String ret = getMappedName();
			if (ret != null) return ret;
		}

		if (tmpNamed) {
			String ret = getTmpName(unmatchedTmp);
			if (ret != null) return ret;
		}

		return getName();
	}

	String getTmpName(boolean unmatched);
	int getUid();
	String getUidString();
	String getMappedName();

	default boolean hasMatch() {
		return getMatch() != null;
	}

	T getMatch();
	boolean isNameObfuscated();
}