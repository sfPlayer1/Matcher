package matcher.type;

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

import org.objectweb.asm.Opcodes;

import matcher.Util;

public abstract class MemberInstance<T extends MemberInstance<T>> implements IMatchable<T> {
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
	public String getDisplayName(boolean full, boolean mapped, boolean tmpNamed, boolean localOnly) {
		String name = getName(mapped, tmpNamed, localOnly);

		if (full) {
			return cls.getDisplayName(full, mapped, tmpNamed, localOnly) + "." + name;
		} else {
			return name;
		}
	}

	public abstract String getDesc();
	public abstract boolean isReal();

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
		assert hierarchyMembers != null;

		return hierarchyMembers;
	}

	@Override
	public String getTmpName(boolean unmatched) {
		String ret;

		if (!unmatched && matchedInstance != null && (ret = matchedInstance.getTmpName(true)) != null) {
			return ret;
		}

		return tmpName;
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

	public boolean hasMappedName() {
		return mappedName != null || matchedInstance != null && matchedInstance.mappedName != null;
	}

	@Override
	public String getMappedName() {
		if (mappedName != null) {
			return mappedName;
		} else if (matchedInstance != null && matchedInstance.mappedName != null) {
			return matchedInstance.mappedName;
		} else {
			return null;
		}
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
	public T getMatch() {
		return matchedInstance;
	}

	public void setMatch(T match) {
		assert match == null || cls == match.cls.getMatch();

		this.matchedInstance = match;
	}

	@Override
	public String toString() {
		return getDisplayName(true, false, false, true);
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

	private String tmpName;
	int uid = -1;

	String mappedName;
	String mappedComment;
	T matchedInstance;
}
