package matcher;

public enum NameType {
	MAPPED_PLAIN, PLAIN, UID_PLAIN, TMP_PLAIN, LOCTMP_PLAIN, MAPPED_TMP_PLAIN, MAPPED_LOCTMP_PLAIN;

	public NameType withMapped(boolean mapped) {
		if (mapped) {
			if (this == PLAIN) return MAPPED_PLAIN;
			if (this == TMP_PLAIN) return MAPPED_TMP_PLAIN;
			if (this == LOCTMP_PLAIN) return MAPPED_LOCTMP_PLAIN;
			if (this == UID_PLAIN) throw new IllegalStateException();
		} else {
			if (this == MAPPED_PLAIN) return PLAIN;
			if (this == MAPPED_TMP_PLAIN) return TMP_PLAIN;
			if (this == MAPPED_LOCTMP_PLAIN) return LOCTMP_PLAIN;
		}

		return this;
	}

	public NameType withTmp(boolean tmp) {
		if (tmp) {
			if (this == MAPPED_PLAIN) return MAPPED_TMP_PLAIN;
			if (this == PLAIN) return TMP_PLAIN;
		} else {
			if (this == TMP_PLAIN || this == LOCTMP_PLAIN) return PLAIN;
			if (this == MAPPED_TMP_PLAIN || this == MAPPED_LOCTMP_PLAIN) return MAPPED_PLAIN;
		}

		return this;
	}

	public NameType withUnmatchedTmp(boolean unmatched) {
		if (unmatched) {
			if (this == TMP_PLAIN) return LOCTMP_PLAIN;
			if (this == MAPPED_TMP_PLAIN) return MAPPED_LOCTMP_PLAIN;
		} else {
			if (this == LOCTMP_PLAIN) return TMP_PLAIN;
			if (this == MAPPED_LOCTMP_PLAIN) return MAPPED_TMP_PLAIN;
		}

		return this;
	}
}
