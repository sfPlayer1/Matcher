package matcher;

public enum NameType {
	PLAIN(true, false, false, false),
	MAPPED(false, true, false, false),
	AUX(false, false, false, true),

	MAPPED_PLAIN(true, true, false, false),
	MAPPED_AUX_PLAIN(true, true, false, true),
	MAPPED_TMP_PLAIN(true, true, true, false),
	MAPPED_LOCTMP_PLAIN(true, true, false, false),

	UID_PLAIN(true, false, false, false),
	TMP_PLAIN(true, false, true, false),
	LOCTMP_PLAIN(true, false, false, false),
	AUX_PLAIN(true, false, false, true);

	private NameType(boolean plain, boolean mapped, boolean tmp, boolean aux) {
		this.plain = plain;
		this.mapped = mapped;
		this.tmp = tmp;
		this.aux = aux;
	}

	public NameType withMapped(boolean value) {
		if (mapped == value) return this;

		if (value) {
			if (aux) return MAPPED_AUX_PLAIN;
			if (tmp) return MAPPED_TMP_PLAIN;
			if (plain) return MAPPED_PLAIN;

			return MAPPED;
		} else {
			if (aux) return plain ? AUX_PLAIN : AUX;
			if (tmp) return TMP_PLAIN;
			if (this == MAPPED_LOCTMP_PLAIN) return LOCTMP_PLAIN;

			return PLAIN;
		}
	}

	public NameType withAux(boolean value) {
		if (aux == value) return this;

		if (value) {
			if (mapped) return MAPPED_AUX_PLAIN;
			if (plain) return AUX_PLAIN;

			return AUX;
		} else {
			if (mapped) return MAPPED_PLAIN;

			return PLAIN;
		}
	}

	public NameType withTmp(boolean value) {
		if (tmp == value) return this;

		if (value) {
			if (mapped) return MAPPED_TMP_PLAIN;

			return TMP_PLAIN;
		} else {
			if (mapped) return MAPPED_PLAIN;

			return PLAIN;
		}
	}

	// transform between tmp <-> loctmp
	public NameType withUnmatchedTmp(boolean value) {
		boolean locTmp = this == MAPPED_LOCTMP_PLAIN || this == LOCTMP_PLAIN;

		if (value == locTmp || !tmp && !locTmp) return this;

		if (value) {
			if (mapped) return MAPPED_LOCTMP_PLAIN;

			return LOCTMP_PLAIN;
		} else {
			if (mapped) return MAPPED_TMP_PLAIN;

			return TMP_PLAIN;
		}
	}

	public final boolean plain;
	public final boolean mapped;
	public final boolean tmp;
	public final boolean aux;
}
