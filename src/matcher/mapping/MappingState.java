package matcher.mapping;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import matcher.type.FieldInstance;
import matcher.type.MethodInstance;

class MappingState {
	public ClassMappingState getClass(String name) {
		ClassMappingState ret = classMap.get(name);

		if (ret == null) {
			ret = new ClassMappingState(name);
			classMap.put(name, ret);
		}

		int pos = name.lastIndexOf('$');

		if (pos > 0) {
			getClass(name.substring(0, pos)).innerClasses.add(ret);
		}

		return ret;
	}

	public static class ClassMappingState {
		public ClassMappingState(String name) {
			this.name = name;
		}

		public MethodMappingState getMethod(String name, String desc) {
			String id = MethodInstance.getId(name, desc);
			MethodMappingState ret = methodMap.get(id);

			if (ret == null) {
				ret = new MethodMappingState(name, desc);
				methodMap.put(id, ret);
			}

			return ret;
		}

		public FieldMappingState getField(String name, String desc) {
			String id = FieldInstance.getId(name, desc);
			FieldMappingState ret = fieldMap.get(id);

			if (ret == null) {
				ret = new FieldMappingState(name, desc);
				fieldMap.put(id, ret);
			}

			return ret;
		}

		public final String name;
		public String mappedName;
		public String comment;
		public final Set<ClassMappingState> innerClasses = new LinkedHashSet<>();
		public final Map<String, MethodMappingState> methodMap = new LinkedHashMap<>();
		public final Map<String, FieldMappingState> fieldMap = new LinkedHashMap<>();
	}

	public static class MethodMappingState {
		public MethodMappingState(String name, String desc) {
			this.name = name;
			this.desc = desc;
		}

		public ArgMappingState getArg(int index, int lvIndex) {
			ArgMappingState ret = argMap.get(index);

			if (ret == null) {
				ret = new ArgMappingState(index, lvIndex);
				argMap.put(index, ret);
			}

			return ret;
		}

		public VarMappingState getVar(int index, int lvIndex, int startOpIdx, int asmIndex) {
			VarMappingState ret = varMap.get(index);

			if (ret == null) {
				ret = new VarMappingState(index, lvIndex, startOpIdx, asmIndex);
				varMap.put(index, ret);
			}

			return ret;
		}

		public final String name;
		public final String desc;
		public String mappedName;
		public String comment;
		public final Map<Integer, ArgMappingState> argMap = new LinkedHashMap<>();
		public final Map<Integer, VarMappingState> varMap = new LinkedHashMap<>();
	}

	public static class ArgMappingState {
		public ArgMappingState(int index, int lvIndex) {
			this.index = index;
			this.lvIndex = lvIndex;
		}

		public final int index;
		public final int lvIndex;
		public String name;
		public String mappedName;
		public String comment;
	}

	public static class VarMappingState {
		public VarMappingState(int index, int lvIndex, int startOpIdx, int asmIndex) {
			this.index = index;
			this.lvIndex = lvIndex;
			this.startOpIdx = startOpIdx;
			this.asmIndex = asmIndex;
		}

		public final int index;
		public final int lvIndex;
		public final int startOpIdx;
		public final int asmIndex;
		public String name;
		public String mappedName;
		public String comment;
	}

	public static class FieldMappingState {
		public FieldMappingState(String name, String desc) {
			this.name = name;
			this.desc = desc;
		}

		public final String name;
		public final String desc;
		public String mappedName;
		public String comment;
	}

	public final Map<String, ClassMappingState> classMap = new LinkedHashMap<>();
	public final Map<String, String> metaMap = new LinkedHashMap<>();
}
