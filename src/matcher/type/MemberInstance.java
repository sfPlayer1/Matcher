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
		this.nameObfuscatedLocal = nameObfuscated;
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

		if (type.mapped && cls.isInput() && (ret = getMappedName()) != null) {
			// MAPPED_*, mapped name available
		} else if (type.mapped && !isNameObfuscated()) {
			// MAPPED_*, local deobf
			ret = origName;
		} else if (type.mapped && matchedInstance != null && !matchedInstance.isNameObfuscated()) {
			// MAPPED_*, remote deobf
			ret = matchedInstance.origName;
		} else if (type.isAux() && cls.isInput() && (ret = getAuxName(type.getAuxIndex())) != null) {
			// *_AUX*, aux available
		} else if (type.tmp && cls.isInput() && (ret = getTmpName()) != null) {
			// MAPPED_TMP_* with obf name or TMP_*, remote name available
		} else if ((type.tmp || locTmp) && hierarchyData != null && hierarchyData.tmpName != null) {
			// MAPPED_TMP_* or MAPPED_LOCTMP_* with obf name or TMP_* or LOCTMP_*, local name available
			ret = hierarchyData.tmpName;
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
		return hierarchyData == null ? nameObfuscatedLocal : hierarchyData.nameObfuscated;
	}

	public int getPosition() {
		return position;
	}

	public abstract int getAccess();

	public boolean isStatic() {
		return isStatic;
	}

	public boolean isPublic() {
		return (getAccess() & Opcodes.ACC_PUBLIC) != 0;
	}

	public boolean isProtected() {
		return (getAccess() & Opcodes.ACC_PROTECTED) != 0;
	}

	public boolean isPrivate() {
		return (getAccess() & Opcodes.ACC_PRIVATE) != 0;
	}

	public boolean isFinal() {
		return (getAccess() & Opcodes.ACC_FINAL) != 0;
	}

	public boolean isSynthetic() {
		return (getAccess() & Opcodes.ACC_SYNTHETIC) != 0;
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

	public T getHierarchyMatch() {
		assert hierarchyData != null; // only available for input classes

		if (hierarchyData.matchedHierarchy == null) return null;

		T ret = getMatch();
		if (ret != null) return ret;

		ClassEnv reqEnv = cls.getEnv();

		for (T m : hierarchyData.getMembers()) {
			ret = m.getMatch();

			if (ret != null) {
				ClassEnv env = m.cls.getEnv();

				if (env.isShared() || env == reqEnv) {
					return ret;
				}
			}
		}

		return null;
	}

	public Set<T> getAllHierarchyMembers() {
		assert hierarchyData != null; // only available for input classes

		return hierarchyData.getMembers();
	}

	@Override
	public boolean hasLocalTmpName() {
		return hierarchyData != null && hierarchyData.tmpName != null;
	}

	private String getTmpName() {
		assert hierarchyData != null; // only available for input classes

		if (hierarchyData.tmpName != null) {
			return hierarchyData.tmpName;
		} else if (hierarchyData.matchedHierarchy != null && hierarchyData.matchedHierarchy.tmpName != null) {
			return hierarchyData.matchedHierarchy.tmpName;
		}

		return null;
	}

	public void setTmpName(String tmpName) {
		hierarchyData.tmpName = tmpName;
	}

	@Override
	public int getUid() {
		assert hierarchyData != null; // only available for input classes

		if (hierarchyData.uid >= 0) {
			if (hierarchyData.matchedHierarchy != null && hierarchyData.matchedHierarchy.uid >= 0) {
				return Math.min(hierarchyData.uid, hierarchyData.matchedHierarchy.uid);
			} else {
				return hierarchyData.uid;
			}
		} else if (hierarchyData.matchedHierarchy != null) {
			return hierarchyData.matchedHierarchy.uid;
		} else {
			return -1;
		}
	}

	public void setUid(int uid) {
		hierarchyData.matchedHierarchy.uid = uid;
	}

	protected abstract String getUidString();

	@Override
	public boolean hasMappedName() {
		return getMappedName() != null;
	}

	private String getMappedName() {
		assert hierarchyData != null; // only available for input classes

		if (hierarchyData.mappedName != null) {
			return hierarchyData.mappedName;
		} else if (hierarchyData.matchedHierarchy != null && hierarchyData.matchedHierarchy.mappedName != null) {
			return hierarchyData.matchedHierarchy.mappedName;
		}

		return null;
	}

	public void setMappedName(String mappedName) {
		hierarchyData.mappedName = mappedName;
	}

	@Override
	public String getMappedComment() {
		if (mappedComment != null) {
			return mappedComment;
		} else if (matchedInstance != null) {
			return matchedInstance.mappedComment;
		} else {
			return null;
		}
	}

	@Override
	public void setMappedComment(String comment) {
		if (comment != null && comment.isEmpty()) comment = null;

		this.mappedComment = comment;
	}

	@Override
	public boolean hasAuxName(int index) {
		return getAuxName(index) != null;
	}

	private String getAuxName(int index) {
		assert hierarchyData != null; // only available for input classes

		if (hierarchyData.auxName != null && hierarchyData.auxName.length > index && hierarchyData.auxName[index] != null) {
			return hierarchyData.auxName[index];
		} else if (hierarchyData.matchedHierarchy != null
				&& hierarchyData.matchedHierarchy.auxName != null
				&& hierarchyData.matchedHierarchy.auxName.length > index
				&& hierarchyData.matchedHierarchy.auxName[index] != null) {
			return hierarchyData.matchedHierarchy.auxName[index];
		}

		return null;
	}

	public void setAuxName(int index, String name) {
		if (hierarchyData.auxName == null) hierarchyData.auxName = new String[NameType.AUX_COUNT];
		hierarchyData.auxName[index] = name;
	}

	@Override
	public boolean isMatchable() {
		return hierarchyData != null && hierarchyData.matchable && cls.isMatchable();
	}

	@Override
	public boolean setMatchable(boolean matchable) {
		if (!matchable && matchedInstance != null) return false;
		if (matchable && !cls.isMatchable()) return false;
		if (hierarchyData == null) return !matchable;
		if (!matchable && hierarchyData.matchedHierarchy != null) return false;

		hierarchyData.matchable = matchable;

		return true;
	}

	@Override
	public T getMatch() {
		return matchedInstance;
	}

	public void setMatch(T match) {
		assert match == null || isMatchable();
		assert match == null || cls == match.cls.getMatch();

		this.matchedInstance = match;
		this.hierarchyData.matchedHierarchy = match != null ? match.hierarchyData : null;
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
	boolean nameObfuscatedLocal;
	final int position;
	final boolean isStatic;

	private Set<T> parents = Collections.emptySet();
	private Set<T> children = Collections.emptySet();
	MemberHierarchyData<T> hierarchyData;

	String mappedComment;

	T matchedInstance;
}
