package matcher.mapping;

import matcher.NameType;

public enum MappingField {
	PLAIN("Plain", NameType.PLAIN),
	MAPPED("Mapped", NameType.MAPPED_PLAIN),
	UID("UID", NameType.UID_PLAIN),
	AUX("AUX", NameType.AUX_PLAIN),
	AUX2("AUX2", NameType.AUX2_PLAIN);

	MappingField(String name, NameType type) {
		this.name = name;
		this.type = type;
	}

	@Override
	public String toString() {
		return name;
	}

	public static final MappingField[] VALUES = values();

	public final String name;
	public final NameType type;
}
