package matcher.type;

import matcher.NameType;
import matcher.SimilarityChecker;
import matcher.Util;

public class MethodVarInstance implements Matchable<MethodVarInstance> {
	MethodVarInstance(MethodInstance method, boolean isArg, int index, int lvIndex, int asmIndex,
			ClassInstance type, int startInsn, int endInsn, int startOpIdx,
			String origName, boolean nameObfuscated) {
		this.method = method;
		this.isArg = isArg;
		this.index = index;
		this.lvIndex = lvIndex;
		this.asmIndex = asmIndex;
		this.type = type;
		this.startInsn = startInsn;
		this.endInsn = endInsn;
		this.startOpIdx = startOpIdx;
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

	public int getStartOpIdx() {
		return startOpIdx;
	}

	@Override
	public String getId() {
		return Integer.toString(index);
	}

	public String getTypedId() {
		return (isArg ? "arg" : "lv").concat(getId());
	}

	@Override
	public String getName() {
		return origName;
	}

	@Override
	public String getName(NameType type) {
		if (type == NameType.PLAIN) {
			return hasValidOrigName() ? origName : getTypedId();
		} else if (type == NameType.UID_PLAIN) {
			ClassEnvironment env = method.cls.env.getGlobal();
			int uid = getUid();
			if (uid >= 0) return (isArg ? env.argUidPrefix : env.varUidPrefix)+uid;
		}

		boolean plain = type != NameType.MAPPED;
		boolean mapped = type == NameType.MAPPED || type == NameType.MAPPED_PLAIN || type == NameType.MAPPED_TMP_PLAIN || type == NameType.MAPPED_LOCTMP_PLAIN;
		boolean tmp = type == NameType.MAPPED_TMP_PLAIN || type == NameType.TMP_PLAIN;
		boolean locTmp = type == NameType.MAPPED_LOCTMP_PLAIN || type == NameType.LOCTMP_PLAIN;
		String ret;

		if (mapped && mappedName != null) {
			// MAPPED_*, local name available
			ret = mappedName;
		} else if (mapped && matchedInstance != null && matchedInstance.mappedName != null) {
			// MAPPED_*, remote name available
			ret = matchedInstance.mappedName;
		} else if (mapped && !nameObfuscated && hasValidOrigName()) {
			// MAPPED_*, local deobf
			ret = origName;
		} else if (mapped && matchedInstance != null && !matchedInstance.nameObfuscated) {
			// MAPPED_*, remote deobf
			ret = matchedInstance.origName;
		} else if (tmp && matchedInstance != null && matchedInstance.tmpName != null) {
			// MAPPED_TMP_* with obf name or TMP_*, remote name available
			ret = matchedInstance.tmpName;
		} else if ((tmp || locTmp) && tmpName != null) {
			// MAPPED_TMP_* or MAPPED_LOCTMP_* with obf name or TMP_* or LOCTMP_*, local name available
			ret = tmpName;
		} else if (plain) {
			ret = hasValidOrigName() ? origName : getTypedId();
		} else {
			ret = null;
		}

		return ret;
	}

	private boolean hasValidOrigName() {
		if (origName == null
				|| !Util.isValidJavaIdentifier(origName)
				|| origName.startsWith("arg") && origName.length() > 3 && origName.charAt(3) >= '0' && origName.charAt(3) <= '9' // conflicts with argX typed id
				|| origName.startsWith("lv") && origName.length() > 2 && origName.charAt(2) >= '0' && origName.charAt(2) <= '9') { // conflicts with lvX typed id
			return false;
		}

		// check if unique

		for (MethodVarInstance var : method.getArgs()) {
			if (var != this && origName.equals(var.origName)) return false;
		}

		for (MethodVarInstance var : method.getVars()) {
			if (var != this && origName.equals(var.origName)) return false;
		}

		return true;
	}

	@Override
	public Matchable<?> getOwner() {
		return method;
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
		return uid;
	}

	public void setUid(int uid) {
		this.uid = uid;
	}

	@Override
	public boolean hasMappedName() {
		return mappedName != null || matchedInstance != null && matchedInstance.mappedName != null;
	}

	public void setMappedName(String mappedName) {
		this.mappedName = mappedName;
	}

	public String getMappedComment() {
		if (mappedComment != null) {
			return mappedComment;
		} else if (matchedInstance != null) {
			return matchedInstance.mappedComment;
		} else {
			return null;
		}
	}

	public void setMappedComment(String comment) {
		if (comment != null && comment.isEmpty()) comment = null;

		this.mappedComment = comment;
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
	public boolean isFullyMatched(boolean recursive) {
		return matchedInstance != null;
	}

	@Override
	public float getSimilarity() {
		if (matchedInstance == null) return 0;

		return SimilarityChecker.compare(this, matchedInstance);
	}

	@Override
	public String toString() {
		return method+":"+getTypedId();
	}

	final MethodInstance method;
	final boolean isArg;
	final int index;
	final int lvIndex;
	final int asmIndex;
	final ClassInstance type;
	private final int startInsn; // inclusive
	private final int endInsn; // exclusive
	private final int startOpIdx;
	final String origName;
	final boolean nameObfuscated;

	private String tmpName;
	private int uid = -1;

	private String mappedName;
	String mappedComment;
	private MethodVarInstance matchedInstance;
}
