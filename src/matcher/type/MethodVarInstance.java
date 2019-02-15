package matcher.type;

import matcher.NameType;

public class MethodVarInstance implements IMatchable<MethodVarInstance> {
	MethodVarInstance(MethodInstance method, boolean isArg, int index, int lvIndex, int asmIndex,
			ClassInstance type, int startInsn, int endInsn,
			String origName, boolean nameObfuscated) {
		this.method = method;
		this.isArg = isArg;
		this.index = index;
		this.lvIndex = lvIndex;
		this.asmIndex = asmIndex;
		this.type = type;
		this.startInsn = startInsn;
		this.endInsn = endInsn;
		this.origName = origName;
		this.nameObfuscated = nameObfuscated;
	}

	public MethodInstance getMethod() {
		return method;
	}

	public boolean isArg() {
		return isArg;
	}

	public int getIndex() {
		return index;
	}

	public int getLvIndex() {
		return lvIndex;
	}

	public int getAsmIndex() {
		return asmIndex;
	}

	public ClassInstance getType() {
		return type;
	}

	public int getStartInsn() {
		return startInsn;
	}

	public int getEndInsn() {
		return endInsn;
	}

	@Override
	public String getId() {
		return Integer.toString(index);
	}

	@Override
	public String getName() {
		return origName;
	}

	@Override
	public String getName(NameType type) {
		if (type == NameType.PLAIN) {
			return origName;
		} else if (type == NameType.UID_PLAIN) {
			int uid = getUid();
			if (uid >= 0) return (isArg ? "arg_" : "var_")+index;
		}

		boolean mapped = type == NameType.MAPPED_PLAIN || type == NameType.MAPPED_TMP_PLAIN || type == NameType.MAPPED_LOCTMP_PLAIN;
		boolean tmp = type == NameType.MAPPED_TMP_PLAIN || type == NameType.TMP_PLAIN;
		boolean locTmp = type == NameType.MAPPED_LOCTMP_PLAIN || type == NameType.LOCTMP_PLAIN;
		String ret;

		if (mapped && mappedName != null) {
			// MAPPED_*, local name available
			ret = mappedName;
		} else if (mapped && matchedInstance != null && matchedInstance.mappedName != null) {
			// MAPPED_*, remote name available
			ret = matchedInstance.mappedName;
		} else if (tmp && (nameObfuscated || !mapped) && matchedInstance != null && matchedInstance.tmpName != null) {
			// MAPPED_TMP_* with obf name or TMP_*, remote name available
			ret = matchedInstance.tmpName;
		} else if ((tmp || locTmp) && (nameObfuscated || !mapped) && tmpName != null) {
			// MAPPED_TMP_* or MAPPED_LOCTMP_* with obf name or TMP_* or LOCTMP_*, local name available
			ret = tmpName;
		} else {
			ret = origName;
		}

		return ret;
	}

	@Override
	public ClassEnv getEnv() {
		return method.getEnv();
	}

	@Override
	public boolean isNameObfuscated() {
		return nameObfuscated;
	}

	@Override
	public boolean hasLocalTmpName() {
		return tmpName != null;
	}

	public void setTmpName(String tmpName) {
		this.tmpName = tmpName;
	}

	@Override
	public int getUid() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean hasMappedName() {
		return mappedName != null || matchedInstance != null && matchedInstance.mappedName != null;
	}

	public void setMappedName(String mappedName) {
		this.mappedName = mappedName;
	}

	@Override
	public MethodVarInstance getMatch() {
		return matchedInstance;
	}

	public void setMatch(MethodVarInstance match) {
		assert match == null || method == match.method.getMatch();

		this.matchedInstance = match;
	}

	@Override
	public String toString() {
		return method.toString()+":"+index;
	}

	final MethodInstance method;
	final boolean isArg;
	final int index;
	final int lvIndex;
	final int asmIndex;
	final ClassInstance type;
	final int startInsn; // inclusive
	final int endInsn; // exclusive
	final String origName;
	final boolean nameObfuscated;

	private String tmpName;

	private String mappedName;
	private MethodVarInstance matchedInstance;
}
