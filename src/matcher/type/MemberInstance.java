package matcher.type;

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

import org.objectweb.asm.Opcodes;

import matcher.NameType;
import matcher.SimilarityChecker;
import matcher.Util;

public abstract class MemberInstance<T extends MemberInstance<T>> implements Matchable<T> {
	@SuppressWarnings("unchecked")
	protected MemberInstance(ClassInstance cls, String id, String origName, boolean nameObfuscated, int position, boolean isStatic) {
		this.cls = cls;
		this.id = id;
		this.origName = origName;
		this.nameObfuscated = nameObfuscated;
		this.position = position;
		this.isStatic = isStatic;

		if (cls.isShared()) {
			matchedInstance = (T) this;
		}
	}

	public ClassInstance getCls() {
		return cls;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public final String getName() {
		return origName;
	}

	@Override
	public String getName(NameType type) {
		if (type == NameType.PLAIN) {
			return origName;
		} else if (type == NameType.UID_PLAIN) {
			int uid = getUid();
			if (uid >= 0) return getUidString();
		}

		boolean locTmp = type == NameType.MAPPED_LOCTMP_PLAIN || type == NameType.LOCTMP_PLAIN;
		String ret;

		if (type.mapped && mappedName != null) {
			// MAPPED_*, local name available
			ret = mappedName;
		} else if (type.mapped && cls.isInput() && (ret = getMappedName()) != null) {
			// MAPPED_*, remote name available
		} else if (type.mapped && !nameObfuscated) {
			// MAPPED_*, local deobf
			ret = origName;
		} else if (type.mapped && matchedInstance != null && !matchedInstance.nameObfuscated) {
			// MAPPED_*, remote deobf
			ret = matchedInstance.origName;
		} else if (type.isAux() && auxName != null && auxName.length > type.getAuxIndex() && auxName[type.getAuxIndex()] != null) {
			ret = auxName[type.getAuxIndex()];
		} else if (type.isAux() && matchedInstance != null && matchedInstance.auxName != null && matchedInstance.auxName.length > type.getAuxIndex() && matchedInstance.auxName[type.getAuxIndex()] != null) {
			ret = matchedInstance.auxName[type.getAuxIndex()];
		} else if (type.tmp && cls.isInput() && (ret = getTmpName()) != null) {
			// MAPPED_TMP_* with obf name or TMP_*, remote name available
		} else if ((type.tmp || locTmp) && tmpName != null) {
			// MAPPED_TMP_* or MAPPED_LOCTMP_* with obf name or TMP_* or LOCTMP_*, local name available
			ret = tmpName;
		} else if (type.plain) {
			ret = origName;
		} else {
			ret = null;
		}

		return ret;
	}

	@Override
	public String getDisplayName(NameType type, boolean full) {
		String name = getName(type);

		if (full) {
			return cls.getDisplayName(type, full) + "." + name;
		} else {
			return name;
		}
	}

	public abstract String getDesc();
	public abstract boolean isReal();

	@Override
	public Matchable<?> getOwner() {
		return cls;
	}

	@Override
	public ClassEnv getEnv() {
		return cls.getEnv();
	}

	@Override
	public boolean isNameObfuscated() {
		return nameObfuscated;
	}

	public int getPosition() {
		return position;
	}

	public abstract int getAccess();

	public boolean isStatic() {
		return isStatic;
	}

	public boolean isFinal() {
		return (getAccess() & Opcodes.ACC_FINAL) != 0;
	}

	void addParent(T parent) {
		assert parent.getCls() != getCls();
		assert parent != this;
		assert !children.contains(parent);

		if (parents.isEmpty()) parents = Util.newIdentityHashSet();

		parents.add(parent);
	}

	public Set<T> getParents() {
		return parents;
	}

	void addChild(T child) {
		assert child.getCls() != getCls();
		assert child != this;
		assert !parents.contains(child);

		if (children.isEmpty()) children = Util.newIdentityHashSet();

		children.add(child);
	}

	public Set<T> getChildren() {
		return children;
	}

	@SuppressWarnings("unchecked")
	public T getMatchedHierarchyMember() {
		assert hierarchyMembers != null; // only available for input classes

		if (getMatch() != null) return (T) this;

		ClassEnv reqEnv = cls.getEnv();

		for (T m : hierarchyMembers) {
			if (m.getMatch() != null) {
				ClassEnv env = m.cls.getEnv();

				if (env.isShared() || env == reqEnv) return m;
			}
		}

		return null;
	}

	public Set<T> getAllHierarchyMembers() {
		assert hierarchyMembers != null; // only available for input classes

		return hierarchyMembers;
	}

	@Override
	public boolean hasLocalTmpName() {
		return tmpName != null;
	}

	private String getTmpName() {
		assert hierarchyMembers != null; // only available for input classes

		if (tmpName != null) {
			return tmpName;
		} else if (matchedInstance != null && matchedInstance.tmpName != null) {
			return matchedInstance.tmpName;
		} else if (hierarchyMembers.size() > 1) {
			for (MemberInstance<?> m : hierarchyMembers) {
				if (m.matchedInstance != null && m.matchedInstance.tmpName != null) {
					return m.matchedInstance.tmpName;
				}
			}
		}

		return null;
	}

	public void setTmpName(String tmpName) {
		this.tmpName = tmpName;
	}

	@Override
	public int getUid() {
		if (uid >= 0) {
			if (matchedInstance != null && matchedInstance.uid >= 0) {
				return Math.min(uid, matchedInstance.uid);
			} else {
				return uid;
			}
		} else if (matchedInstance != null) {
			return matchedInstance.uid;
		} else {
			return -1;
		}
	}

	public void setUid(int uid) {
		this.uid = uid;
	}

	protected abstract String getUidString();

	@Override
	public boolean hasMappedName() {
		return getMappedName() != null;
	}

	private String getMappedName() {
		assert hierarchyMembers != null; // only available for input classes

		if (mappedName != null) {
			return mappedName;
		} else if (matchedInstance != null && matchedInstance.mappedName != null) {
			return matchedInstance.mappedName;
		} else if (hierarchyMembers != null && hierarchyMembers.size() > 1) {
			for (MemberInstance<?> m : hierarchyMembers) {
				if (m.matchedInstance != null && m.matchedInstance.mappedName != null) {
					return m.matchedInstance.mappedName;
				}
			}
		}

		return null;
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
	public boolean hasAuxName(int index) {
		return auxName != null && auxName.length > index && auxName[index] != null;
	}

	public void setAuxName(int index, String name) {
		if (this.auxName == null) this.auxName = new String[NameType.AUX_COUNT];
		this.auxName[index] = name;
	}

	@Override
	public boolean isMatchable() {
		return matchable && cls.isMatchable();
	}

	@Override
	public void setMatchable(boolean matchable) {
		assert !matchable || cls.isMatchable();
		assert matchable || matchedInstance == null;

		this.matchable = matchable;
	}

	@Override
	public T getMatch() {
		return matchedInstance;
	}

	public void setMatch(T match) {
		assert match == null || isMatchable();
		assert match == null || cls == match.cls.getMatch();

		this.matchedInstance = match;
	}

	@Override
	public float getSimilarity() {
		if (matchedInstance == null) return 0;

		return SimilarityChecker.compare(this, matchedInstance);
	}

	@Override
	public String toString() {
		return getDisplayName(NameType.PLAIN, true);
	}

	public static final Comparator<MemberInstance<?>> nameComparator = Comparator.<MemberInstance<?>, String>comparing(MemberInstance::getName).thenComparing(MemberInstance::getDesc);

	final ClassInstance cls;
	final String id;
	final String origName;
	boolean nameObfuscated;
	final int position;
	final boolean isStatic;

	private Set<T> parents = Collections.emptySet();
	private Set<T> children = Collections.emptySet();
	Set<T> hierarchyMembers;

	String tmpName;
	int uid = -1;

	String mappedName;
	String mappedComment;

	String[] auxName;

	private boolean matchable = true;
	T matchedInstance;
}
