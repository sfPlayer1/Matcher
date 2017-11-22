package matcher.type;

public interface IClassEnv {
	boolean isShared();

	default ClassInstance getClsByName(String name) {
		return getClsById(ClassInstance.getId(name));
	}

	ClassInstance getClsById(String id);

	default ClassInstance getLocalClsByName(String name) {
		return getLocalClsById(ClassInstance.getId(name));
	}

	ClassInstance getLocalClsById(String id);

	default ClassInstance getCreateClassInstance(String id) {
		return getCreateClassInstance(id, true);
	}

	ClassInstance getCreateClassInstance(String id, boolean createUnknown);

	default ClassInstance getClsByMappedName(String name) {
		return getClsByMappedId(ClassInstance.getId(name));
	}

	ClassInstance getClsByMappedId(String id);

	ClassEnvironment getGlobal();
}
