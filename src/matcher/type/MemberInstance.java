package matcher.type;

import java.util.Set;

import matcher.Util;

public abstract class MemberInstance<T extends MemberInstance<T>> implements IMatchable<T> {
	@SuppressWarnings("unchecked")
	protected MemberInstance(ClassInstance cls, String id, String origName, boolean nameObfuscated, int position) {
		this.cls = cls;
		this.id = id;
		this.origName = origName;
		this.nameObfuscated = nameObfuscated;
		this.position = position;

		if (cls.isShared()) {
			matchedInstance = (T) this;
		}
	}

	public ClassInstance getCls() {
		return cls;
	}

	public String getId() {
		return id;
	}

	public abstract String getName();
	public abstract String getDesc();

	public String getOrigName() {
		return origName;
	}

	@Override
	public boolean isNameObfuscated() {
		return nameObfuscated;
	}

	public int getPosition() {
		return position;
	}

	public T getParent() {
		return parent;
	}

	public Set<T> getChildren() {
		return children;
	}

	public boolean hasMappedName() {
		return mappedName != null || matchedInstance != null && matchedInstance.mappedName != null;
	}

	public String getMappedName() {
		if (mappedName != null) {
			return mappedName;
		} else if (matchedInstance != null) {
			return matchedInstance.mappedName;
		} else {
			return null;
		}
	}

	public void setMappedName(String mappedName) {
		this.mappedName = mappedName;
	}

	@Override
	public T getMatch() {
		return matchedInstance;
	}

	public void setMatch(T match) {
		this.matchedInstance = match;
	}

	@Override
	public String toString() {
		return cls.getName()+"/"+getName();
	}

	final ClassInstance cls;
	final String id;
	final String origName;
	final boolean nameObfuscated;
	final int position;

	T parent;
	final Set<T> children = Util.newIdentityHashSet();

	String mappedName;
	T matchedInstance;
}