package matcher;

import java.util.NoSuchElementException;
import java.util.Objects;

public enum NameType {
	PLAIN(true, false, false, 0),
	MAPPED(false, true, false, 0),
	AUX(false, false, false, 1),
	AUX2(false, false, false, 2),

	MAPPED_PLAIN(true, true, false, 0),
	MAPPED_AUX_PLAIN(true, true, false, 1),
	MAPPED_AUX2_PLAIN(true, true, false, 2),
	MAPPED_TMP_PLAIN(true, true, true, 0),
	MAPPED_LOCTMP_PLAIN(true, true, false, 0),

	UID_PLAIN(true, false, false, 0),
	TMP_PLAIN(true, false, true, 0),
	LOCTMP_PLAIN(true, false, false, 0),
	AUX_PLAIN(true, false, false, 1),
	AUX2_PLAIN(true, false, false, 2);

	NameType(boolean plain, boolean mapped, boolean tmp, int aux) {
		this.plain = plain;
		this.mapped = mapped;
		this.tmp = tmp;
		this.aux = aux;
	}

	public NameType withMapped(boolean value) {
		if (mapped == value) return this;

		if (value) {
			if (aux > 0) return VALUES[MAPPED_AUX_PLAIN.ordinal() + aux - 1];
			if (tmp) return MAPPED_TMP_PLAIN;
			if (plain) return MAPPED_PLAIN;

			return MAPPED;
		} else {
			if (aux > 0) return VALUES[(plain ? AUX_PLAIN : AUX).ordinal() + aux - 1];
			if (tmp) return TMP_PLAIN;
			if (this == MAPPED_LOCTMP_PLAIN) return LOCTMP_PLAIN;

			return PLAIN;
		}
	}

	public NameType withAux(int index, boolean value) {
		if ((aux - 1 == index) == value) return this;

		if (value) {
			if (mapped) return VALUES[MAPPED_AUX_PLAIN.ordinal() + index];
			if (plain) return VALUES[AUX_PLAIN.ordinal() + index];

			return VALUES[AUX.ordinal() + index];
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

	public boolean isAux() {
		return aux > 0;
	}

	public int getAuxIndex() {
		if (aux == 0) throw new NoSuchElementException();

		return aux - 1;
	}

	public static NameType getAux(int index) {
		Objects.checkIndex(index, AUX_COUNT);

		return VALUES[NameType.AUX.ordinal() + index];
	}

	public static final int AUX_COUNT = 2;

	private static final NameType[] VALUES = values();

	public final boolean plain;
	public final boolean mapped;
	public final boolean tmp;
	private final int aux;
}
