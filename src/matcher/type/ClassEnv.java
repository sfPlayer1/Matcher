package matcher.type;

import java.util.Collection;

import matcher.NameType;

public interface ClassEnv {
	boolean isShared();

	Collection<ClassInstance> getClasses();

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

	default ClassInstance getClsByName(String name, NameType nameType) {
		return getClsById(ClassInstance.getId(name), nameType);
	}

	ClassInstance getClsById(String id, NameType nameType);

	ClassEnvironment getGlobal();
}
