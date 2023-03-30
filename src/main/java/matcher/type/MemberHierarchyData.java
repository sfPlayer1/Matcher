package matcher.type;

import java.util.Set;

final class MemberHierarchyData<T> {
	MemberHierarchyData(Set<T> members, boolean nameObfuscated) {
		this.members = members;
		this.nameObfuscated = nameObfuscated;
	}

	boolean hasMultipleMembers() {
		return members.size() > 1;
	}

	Set<T> getMembers() {
		return members;
	}

	void addMember(T member) {
		members.add(member);
	}

	private final Set<T> members;
	boolean nameObfuscated;

	String tmpName;
	int uid = -1;
	String mappedName;
	String[] auxName;

	boolean matchable = true;
	MemberHierarchyData<T> matchedHierarchy;
}
