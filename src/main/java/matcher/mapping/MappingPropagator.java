package matcher.mapping;

import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleConsumer;

import matcher.Matcher;
import matcher.NameType;
import matcher.Util;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public final class MappingPropagator {
	public static boolean propagateNames(ClassEnvironment env, DoubleConsumer progressReceiver) {
		int total = env.getClassesB().size();
		int current = 0;
		Set<MethodInstance> checked = Util.newIdentityHashSet();
		int propagatedMethodNames = 0;
		int propagatedArgNames = 0;

		for (ClassInstance cls : env.getClassesB()) {
			if (cls.isInput() && cls.getMethods().length > 0) {
				for (MethodInstance method : cls.getMethods()) {
					if (method.getAllHierarchyMembers().size() <= 1) continue;
					if (checked.contains(method)) continue;

					String name = method.hasMappedName() || !method.isNameObfuscated() ? method.getName(NameType.MAPPED_PLAIN) : null;
					if (name != null && method.hasAllArgsMapped()) continue;

					checked.addAll(method.getAllHierarchyMembers());

					// collect names from all hierarchy members

					final int argCount = method.getArgs().length;
					String[] argNames = new String[argCount];
					int missingArgNames = argCount;

					collectLoop: for (MethodInstance m : method.getAllHierarchyMembers()) {
						if (name == null && (method.hasMappedName() || !method.isNameObfuscated())) {
							name = method.getName(NameType.MAPPED_PLAIN);

							if (missingArgNames == 0) break;
						}

						if (missingArgNames > 0) {
							assert m.getArgs().length == argCount;

							for (int i = 0; i < argCount; i++) {
								MethodVarInstance arg;

								if (argNames[i] == null && (arg = m.getArg(i)).hasMappedName()) {
									argNames[i] = arg.getName(NameType.MAPPED_PLAIN);
									missingArgNames--;

									if (name != null && missingArgNames == 0) break collectLoop;
								}
							}
						}
					}

					if (name == null && missingArgNames == argCount) continue; // nothing found

					// apply names to all hierarchy members that don't have any yet

					for (MethodInstance m : method.getAllHierarchyMembers()) {
						if (name != null && !m.hasMappedName()) {
							m.setMappedName(name);
							propagatedMethodNames++;
						}

						for (int i = 0; i < argCount; i++) {
							MethodVarInstance arg;

							if (argNames[i] != null && !(arg = m.getArg(i)).hasMappedName()) {
								arg.setMappedName(argNames[i]);
								propagatedArgNames++;
							}
						}
					}
				}
			}

			if (((++current & (1 << 4) - 1)) == 0) {
				progressReceiver.accept((double) current / total);
			}
		}

		Matcher.LOGGER.info("Propagated {} method names and {} method arg names.", propagatedMethodNames, propagatedArgNames);

		return propagatedMethodNames > 0 || propagatedArgNames > 0;
	}

	/**
	 * Ensure that fields and methods representing the same record component share the same mapped name.
	 */
	public static boolean fixRecordMemberNames(ClassEnvironment env, NameType nameType, NameType linkingNameType) {
		if (!nameType.mapped) throw new IllegalArgumentException("non-mapped nameType: "+nameType);

		int modified = 0;

		for (ClassInstance cls : env.getClassesB()) {
			if (!cls.isRecord()) continue;

			for (FieldInstance field : cls.getFields()) {
				MethodInstance linkedMethod = field.getLinkedRecordComponent(linkingNameType);
				if (linkedMethod == null) continue;
				if (!field.isNameObfuscated() && !linkedMethod.isNameObfuscated()) continue;

				String fieldName = field.getName(nameType);
				String methodName = linkedMethod.getName(nameType);
				if (Objects.equals(fieldName, methodName)) continue;

				if (linkedMethod.isNameObfuscated()
						&& (!field.isNameObfuscated() || !linkedMethod.hasMappedName() || field.hasMappedName())) {
					Matcher.LOGGER.debug("Copying record component name for method {} from field {} -> {}", linkedMethod, field, fieldName);
					linkedMethod.setMappedName(fieldName);
				} else {
					Matcher.LOGGER.debug("Copying record component name for field {} from method {} -> {}", field, linkedMethod, methodName);
					field.setMappedName(methodName);
				}

				modified++;
			}
		}

		Matcher.LOGGER.info("Fixed {} record names.", modified);

		return modified > 0;
	}
}
