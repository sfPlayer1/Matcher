package matcher.mapping;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MemoryMappingTree implements MappingTree, MappingVisitor {
	public MemoryMappingTree() {
		this(false);
	}

	public MemoryMappingTree(boolean indexByDstNames) {
		this.indexByDstNames = indexByDstNames;
	}

	public MemoryMappingTree(MappingTree src) {
		if (src instanceof MemoryMappingTree) {
			indexByDstNames = ((MemoryMappingTree) src).indexByDstNames;
		}

		setSrcNamespace(src.getSrcNamespace());
		setDstNamespaces(src.getDstNamespaces());

		for (Map.Entry<String, String> entry : src.getMetadata()) {
			addMetadata(entry.getKey(), entry.getValue());
		}

		for (ClassMapping cls : src.getClasses()) {
			addClass(cls);
		}
	}

	public void setIndexByDstNames(boolean indexByDstNames) {
		if (indexByDstNames == this.indexByDstNames) return;

		if (indexByDstNames) {
			classesByDstNames = null;
		} else if (dstNamespaces != null) {
			initClassesByDstNames();
		}

		this.indexByDstNames = indexByDstNames;
	}

	@SuppressWarnings("unchecked")
	private void initClassesByDstNames() {
		classesByDstNames = new Map[dstNamespaces.size()];

		for (int i = 0; i < classesByDstNames.length; i++) {
			classesByDstNames[i] = new HashMap<String, ClassEntry>(classesBySrcName.size());
		}

		for (ClassEntry cls : classesBySrcName.values()) {
			for (int i = 0; i < cls.dstNames.length; i++) {
				String dstName = cls.dstNames[i];
				if (dstName != null) classesByDstNames[i].put(dstName, cls);
			}
		}
	}

	@Override
	public String getSrcNamespace() {
		return srcNamespace;
	}

	@Override
	public String setSrcNamespace(String namespace) {
		String ret = srcNamespace;
		srcNamespace = namespace;

		return ret;
	}

	@Override
	public List<String> getDstNamespaces() {
		return dstNamespaces;
	}

	@Override
	public List<String> setDstNamespaces(List<String> namespaces) {
		List<String> ret = dstNamespaces;
		dstNamespaces = namespaces;

		if (indexByDstNames) {
			initClassesByDstNames();
		}

		return ret;
	}

	@Override
	public Collection<Map.Entry<String, String>> getMetadata() {
		return metadata;
	}

	@Override
	public String getMetadata(String key) {
		for (Map.Entry<String, String> entry : metadata) {
			if (entry.getKey().equals(key)) return entry.getValue();
		}

		return null;
	}

	@Override
	public void addMetadata(String key, String value) {
		metadata.add(new AbstractMap.SimpleEntry<>(key, value));
	}

	@Override
	public String removeMetadata(String key) {
		for (Iterator<Map.Entry<String, String>> it = metadata.iterator(); it.hasNext(); ) {
			Map.Entry<String, String> entry = it.next();

			if (entry.getKey().equals(key)) {
				it.remove();

				return entry.getValue();
			}
		}

		return null;
	}

	@Override
	public Collection<ClassEntry> getClasses() {
		return classesBySrcName.values();
	}

	@Override
	public ClassEntry getClass(String srcName) {
		return classesBySrcName.get(srcName);
	}

	@Override
	public ClassMapping getClass(String name, int namespace) {
		if (!indexByDstNames || namespace < 0) {
			return MappingTree.super.getClass(name, namespace);
		} else {
			return classesByDstNames[namespace].get(name);
		}
	}

	@Override
	public ClassEntry addClass(ClassMapping cls) {
		ClassEntry entry = cls instanceof ClassEntry && cls.getTree() == this ? (ClassEntry) cls : new ClassEntry(this, cls);

		ClassEntry ret = classesBySrcName.put(cls.getSrcName(), entry);

		if (indexByDstNames) {
			if (ret != null) {
				for (int i = 0; i < ret.dstNames.length; i++) {
					String dstName = ret.dstNames[i];

					if (dstName != null && !dstName.equals(entry.dstNames[i])) {
						classesByDstNames[i].remove(dstName);
					}
				}
			}

			for (int i = 0; i < entry.dstNames.length; i++) {
				String dstName = entry.dstNames[i];
				if (dstName != null) classesByDstNames[i].put(dstName, entry);
			}
		}

		return ret;
	}

	@Override
	public ClassEntry removeClass(String srcName) {
		ClassEntry ret = classesBySrcName.remove(srcName);

		if (ret != null && indexByDstNames) {
			for (int i = 0; i < ret.dstNames.length; i++) {
				String dstName = ret.dstNames[i];
				if (dstName != null) classesByDstNames[i].remove(dstName);
			}
		}

		return ret;
	}

	@Override
	public void accept(MappingVisitor visitor) {
		do {
			if (visitor.visitHeader()) {
				visitor.visitNamespaces(srcNamespace, dstNamespaces);

				for (Map.Entry<String, String> entry : metadata) {
					visitor.visitMetadata(entry.getKey(), entry.getValue());
				}
			}

			if (visitor.visitContent()) {
				Set<MappingFlag> flags = visitor.getFlags();
				boolean supplyFieldDstDescs = flags.contains(MappingFlag.NEEDS_DST_FIELD_DESC);
				boolean supplyMethodDstDescs = flags.contains(MappingFlag.NEEDS_DST_METHOD_DESC);

				for (ClassEntry cls : classesBySrcName.values()) {
					cls.accept(visitor, supplyFieldDstDescs, supplyMethodDstDescs);
				}
			}
		} while (!visitor.visitEnd());
	}

	@Override
	public void reset() {
		currentEntry = null;
		currentClass = null;
		currentMethod = null;
	}

	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) {
		this.srcNamespace = srcNamespace;
		this.dstNamespaces = dstNamespaces;

		if (indexByDstNames) {
			initClassesByDstNames();
		}
	}

	@Override
	public void visitMetadata(String key, String value) {
		this.metadata.add(new AbstractMap.SimpleEntry<>(key, value));
	}

	@Override
	public boolean visitClass(String srcName) {
		currentMethod = null;

		ClassEntry cls = getClass(srcName);

		if (cls == null) {
			cls = new ClassEntry(this, srcName);
			classesBySrcName.put(srcName, cls);
		}

		currentEntry = currentClass = cls;

		return true;
	}

	@Override
	public boolean visitField(String srcName, String srcDesc) {
		if (currentClass == null) throw new UnsupportedOperationException("Tried to visit field before owning class");

		currentMethod = null;

		FieldEntry field = currentClass.getField(srcName, srcDesc);

		if (field == null) {
			field = new FieldEntry(currentClass, srcName, srcDesc);
			currentClass.addField(field);
		}

		currentEntry = field;

		return true;
	}

	@Override
	public boolean visitMethod(String srcName, String srcDesc) {
		if (currentClass == null) throw new UnsupportedOperationException("Tried to visit method before owning class");

		MethodEntry method = currentClass.getMethod(srcName, srcDesc);

		if (method == null) {
			method = new MethodEntry(currentClass, srcName, srcDesc);
			currentClass.addMethod(method);
		}

		currentEntry = currentMethod = method;

		return true;
	}

	@Override
	public boolean visitMethodArg(int argPosition, int lvIndex, String srcName) {
		if (currentMethod == null) throw new UnsupportedOperationException("Tried to visit method argument before owning method");

		MethodArgEntry arg = currentMethod.getArg(argPosition, lvIndex, srcName);

		if (arg == null) {
			arg = new MethodArgEntry(currentMethod, argPosition, lvIndex, srcName);
			currentMethod.addArg(arg);
		}

		currentEntry = arg;

		return true;
	}

	@Override
	public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, String srcName) {
		if (currentMethod == null) throw new UnsupportedOperationException("Tried to visit method variable before owning method");

		MethodVarEntry var = currentMethod.getVar(lvtRowIndex, lvIndex, startOpIdx, srcName);

		if (var == null) {
			var = new MethodVarEntry(currentMethod, lvtRowIndex, lvIndex, startOpIdx, srcName);
			currentMethod.addVar(var);
		}

		currentEntry = var;

		return true;
	}

	@Override
	public boolean visitEnd() {
		currentEntry = null;
		currentClass = null;
		currentMethod = null;

		return true;
	}

	@Override
	public void visitDstName(MappedElementKind targetKind, int namespace, String name) {
		if (currentEntry == null) throw new UnsupportedOperationException("Tried to visit mapped name before owner");
		currentEntry.setDstName(namespace, name);

		if (indexByDstNames) {
			if (targetKind == MappedElementKind.CLASS) {
				classesByDstNames[namespace].put(name, currentClass);
			}
		}
	}

	@Override
	public void visitComment(MappedElementKind targetKind, String comment) {
		Entry entry;

		switch (targetKind) {
		case CLASS:
			entry = currentClass;
			break;
		case METHOD:
			entry = currentMethod;
			break;
		default:
			entry = currentEntry;
		}

		if (entry == null) throw new UnsupportedOperationException("Tried to visit comment before owning target");
		entry.setComment(comment);
	}

	static abstract class Entry implements ElementMapping {
		protected Entry(MemoryMappingTree tree, String srcName) {
			this.srcName = srcName;
			this.dstNames = new String[tree.dstNamespaces.size()];
		}

		protected Entry(MemoryMappingTree tree, ElementMapping src) {
			this(tree, src.getSrcName());

			for (int i = 0; i < dstNames.length; i++) {
				setDstName(i, src.getDstName(i));
			}

			setComment(src.getComment());
		}

		public abstract MappedElementKind getKind();

		@Override
		public final String getSrcName() {
			return srcName;
		}

		@Override
		public final String getDstName(int namespace) {
			return dstNames[namespace];
		}

		@Override
		public void setDstName(int namespace, String name) {
			dstNames[namespace] = name;
		}

		@Override
		public final String getComment() {
			return comment;
		}

		@Override
		public final void setComment(String comment) {
			this.comment = comment;
		}

		protected final boolean acceptElement(MappingVisitor visitor, String[] dstDescs) {
			MappedElementKind kind = getKind();

			for (int i = 0; i < dstNames.length; i++) {
				String dstName = dstNames[i];

				if (dstName != null) visitor.visitDstName(kind, i, dstName);
			}

			if (dstDescs != null) {
				for (int i = 0; i < dstDescs.length; i++) {
					String dstDesc = dstDescs[i];

					if (dstDesc != null) visitor.visitDstDesc(kind, i, dstDesc);
				}
			}

			if (!visitor.visitElementContent(kind)) {
				return false;
			}

			if (comment != null) visitor.visitComment(kind, comment);

			return true;
		}

		protected final String srcName;
		protected final String[] dstNames;
		protected String comment;
	}

	public static final class ClassEntry extends Entry implements ClassMapping {
		ClassEntry(MemoryMappingTree tree, String srcName) {
			super(tree, srcName);

			this.tree = tree;
		}

		ClassEntry(MemoryMappingTree tree, ClassMapping src) {
			super(tree, src);

			this.tree = tree;

			for (FieldMapping field : src.getFields()) {
				addField(field);
			}

			for (MethodMapping method : src.getMethods()) {
				addMethod(method);
			}
		}

		@Override
		public MappedElementKind getKind() {
			return MappedElementKind.CLASS;
		}

		@Override
		public MemoryMappingTree getTree() {
			return tree;
		}

		@Override
		public void setDstName(int namespace, String name) {
			if (tree.indexByDstNames) {
				String oldName = dstNames[namespace];

				if (!Objects.equals(name, oldName)) {
					Map<String, ClassEntry> map = tree.classesByDstNames[namespace];
					if (oldName != null) map.remove(oldName);

					if (name != null) {
						map.put(name, this);
					} else {
						map.remove(oldName);
					}
				}
			}

			super.setDstName(namespace, name);
		}

		@Override
		public Collection<FieldEntry> getFields() {
			if (fields == null) return Collections.emptyList();

			return fields.values();
		}

		@Override
		public FieldEntry getField(String srcName, String srcDesc) {
			return getMember(srcName, srcDesc, fields, flags);
		}

		@Override
		public FieldEntry addField(FieldMapping field) {
			FieldEntry entry = field instanceof FieldEntry && field.getOwner() == this ? (FieldEntry) field : new FieldEntry(this, field);

			if (fields == null) fields = new LinkedHashMap<>();

			return addMember(entry, fields, FLAG_HAS_ANY_FIELD_DESC, FLAG_MISSES_ANY_FIELD_DESC);
		}

		@Override
		public FieldEntry removeField(String srcName, String srcDesc) {
			FieldEntry ret = getField(srcName, srcDesc);
			if (ret != null) fields.remove(ret.key);

			return ret;
		}

		@Override
		public Collection<MethodEntry> getMethods() {
			if (methods == null) return Collections.emptyList();

			return methods.values();
		}

		@Override
		public MethodEntry getMethod(String srcName, String srcDesc) {
			return getMember(srcName, srcDesc, methods, flags >>> 2);
		}

		@Override
		public MethodEntry addMethod(MethodMapping method) {
			MethodEntry entry = method instanceof MethodEntry && method.getOwner() == this ? (MethodEntry) method : new MethodEntry(this, method);

			if (methods == null) methods = new LinkedHashMap<>();

			return addMember(entry, methods, FLAG_HAS_ANY_METHOD_DESC, FLAG_MISSES_ANY_METHOD_DESC);
		}

		@Override
		public MethodEntry removeMethod(String srcName, String srcDesc) {
			MethodEntry ret = getMethod(srcName, srcDesc);
			if (ret != null) methods.remove(ret.key);

			return ret;
		}

		private static <T extends MemberEntry> T getMember(String srcName, String srcDesc, Map<MemberKey, T> map, int flags) {
			if (map == null) return null;

			boolean hasAnyDesc = (flags & FLAG_HAS_ANY_FIELD_DESC) != 0;
			if (!hasAnyDesc) srcDesc = null;

			T ret = map.get(new MemberKey(srcName, srcDesc));

			if (ret != null
					|| !hasAnyDesc
					|| srcDesc != null && (flags & FLAG_MISSES_ANY_FIELD_DESC) == 0) {
				return ret;
			}

			if (srcDesc != null) {
				return map.get(new MemberKey(srcName, null));
			} else {
				for (T entry : map.values()) {
					if (entry.srcName.equals(srcName)) return entry;
				}

				return null;
			}
		}

		private <T extends MemberEntry> T addMember(T entry, Map<MemberKey, T> map, int flagHasAny, int flagMissesAny) {
			int oldFlags = flags;

			if (entry.srcDesc == null) {
				flags |= flagMissesAny;
			} else {
				flags |= flagHasAny;
			}

			T ret = map.put(entry.key, entry);

			if (ret != null
					|| entry.srcDesc != null && (oldFlags & flagMissesAny) == 0
					|| entry.srcDesc == null && (oldFlags & flagHasAny) == 0) {
				return ret;
			}

			if (entry.srcDesc != null) {
				return map.remove(new MemberKey(srcName, null));
			} else {
				for (Iterator<T> it = map.values().iterator(); it.hasNext(); ) {
					T prevEntry = it.next();

					if (prevEntry != entry && prevEntry.srcName.equals(srcName)) {
						it.remove();

						return prevEntry;
					}
				}

				return null;
			}
		}

		void accept(MappingVisitor visitor, boolean supplyFieldDstDescs, boolean supplyMethodDstDescs) {
			if (visitor.visitClass(srcName) && acceptElement(visitor, null)) {
				if (fields != null) {
					for (FieldEntry field : fields.values()) {
						field.accept(visitor, supplyFieldDstDescs);
					}
				}

				if (methods != null) {
					for (MethodEntry method : methods.values()) {
						method.accept(visitor, supplyMethodDstDescs);
					}
				}
			}
		}

		@Override
		public String toString() {
			return srcName;
		}

		private static final byte FLAG_HAS_ANY_FIELD_DESC = 1;
		private static final byte FLAG_MISSES_ANY_FIELD_DESC = 2;
		private static final byte FLAG_HAS_ANY_METHOD_DESC = 4;
		private static final byte FLAG_MISSES_ANY_METHOD_DESC = 8;

		protected final MemoryMappingTree tree;
		private Map<MemberKey, FieldEntry> fields = null;
		private Map<MemberKey, MethodEntry> methods = null;
		private byte flags;
	}

	static abstract class MemberEntry extends Entry implements MemberMapping {
		protected MemberEntry(ClassEntry owner, String srcName, String srcDesc) {
			super(owner.tree, srcName);

			this.owner = owner;
			this.srcDesc = srcDesc;
			this.key = new MemberKey(srcName, srcDesc);
		}

		protected MemberEntry(ClassEntry owner, MemberMapping src) {
			super(owner.tree, src);

			this.owner = owner;
			this.srcDesc = src.getSrcDesc();
			this.key = new MemberKey(srcName, srcDesc);
		}

		@Override
		public MappingTree getTree() {
			return owner.tree;
		}

		@Override
		public final ClassEntry getOwner() {
			return owner;
		}

		@Override
		public final String getSrcDesc() {
			return srcDesc;
		}

		protected final boolean acceptMember(MappingVisitor visitor, boolean supplyDstDescs) {
			String[] dstDescs;

			if (!supplyDstDescs || srcDesc == null) {
				dstDescs = null;
			} else {
				MappingTree tree = owner.tree;
				dstDescs = new String[tree.getDstNamespaces().size()];

				for (int i = 0; i < dstDescs.length; i++) {
					dstDescs[i] = tree.mapDesc(srcDesc, i);
				}
			}

			return acceptElement(visitor, dstDescs);
		}

		protected final ClassEntry owner;
		protected final String srcDesc;
		final MemberKey key;
	}

	public static final class FieldEntry extends MemberEntry implements FieldMapping {
		FieldEntry(ClassEntry owner, String srcName, String srcDesc) {
			super(owner, srcName, srcDesc);
		}

		FieldEntry(ClassEntry owner, FieldMapping src) {
			super(owner, src);
		}

		@Override
		public MappedElementKind getKind() {
			return MappedElementKind.FIELD;
		}

		void accept(MappingVisitor visitor, boolean supplyDstDescs) {
			if (visitor.visitField(srcName, srcDesc)) {
				acceptMember(visitor, supplyDstDescs);
			}
		}

		@Override
		public String toString() {
			return String.format("%s;;%s", srcName, srcDesc);
		}
	}

	public static final class MethodEntry extends MemberEntry implements MethodMapping {
		MethodEntry(ClassEntry owner, String srcName, String srcDesc) {
			super(owner, srcName, srcDesc);
		}

		MethodEntry(ClassEntry owner, MethodMapping src) {
			super(owner, src);

			for (MethodArgMapping arg : src.getArgs()) {
				addArg(arg);
			}

			for (MethodVarMapping var : src.getVars()) {
				addVar(var);
			}
		}

		@Override
		public MappedElementKind getKind() {
			return MappedElementKind.METHOD;
		}

		@Override
		public Collection<MethodArgEntry> getArgs() {
			if (args == null) return Collections.emptyList();

			return args;
		}

		@Override
		public MethodArgEntry getArg(int argPosition, int lvIndex, String srcName) {
			if (args == null) return null;

			if (argPosition >= 0 || lvIndex >= 0) {
				for (MethodArgEntry entry : args) {
					if (argPosition >= 0 && entry.argPosition == argPosition
							|| lvIndex >= 0 && entry.lvIndex == lvIndex) {
						return entry;
					}
				}
			}

			if (srcName != null) {
				for (MethodArgEntry entry : args) {
					if (entry.srcName == srcName) return entry;
				}
			}

			return null;
		}

		@Override
		public MethodArgEntry addArg(MethodArgMapping arg) {
			MethodArgEntry entry = arg instanceof MethodArgEntry && arg.getMethod() == this ? (MethodArgEntry) arg : new MethodArgEntry(this, arg);

			if (args == null) args = new ArrayList<>();

			args.add(entry);

			return null;
		}

		@Override
		public MethodArgEntry removeArg(int argPosition, int lvIndex, String srcName) {
			MethodArgEntry ret = getArg(argPosition, lvIndex, srcName);
			if (ret != null) args.remove(ret);

			return ret;
		}

		@Override
		public Collection<MethodVarEntry> getVars() {
			if (vars == null) return Collections.emptyList();

			return vars;
		}

		@Override
		public MethodVarEntry getVar(int lvtRowIndex, int lvIndex, int startOpIdx, String srcName) {
			if (vars == null) return null;

			if (lvtRowIndex >= 0) {
				boolean hasMissing = false;

				for (MethodVarEntry entry : vars) {
					if (entry.lvtRowIndex == lvtRowIndex) {
						return entry;
					} else if (entry.lvtRowIndex < 0) {
						hasMissing = true;
					}
				}

				if (!hasMissing) return null;
			}

			if (lvIndex >= 0) {
				boolean hasMissing = false;
				MethodVarEntry bestMatch = null;

				for (MethodVarEntry entry : vars) {
					if (entry.lvIndex != lvIndex) {
						if (entry.lvIndex < 0) hasMissing = true;
						continue;
					}

					if (bestMatch == null) {
						bestMatch = entry;
					} else {
						int startOpDeltaImprovement;

						if (startOpIdx < 0 || bestMatch.startOpIdx < 0 && entry.startOpIdx < 0) {
							startOpDeltaImprovement = 0;
						} else if (bestMatch.startOpIdx < 0) {
							startOpDeltaImprovement = 1;
						} else if (entry.startOpIdx < 0) {
							startOpDeltaImprovement = -1;
						} else {
							startOpDeltaImprovement = Math.abs(bestMatch.startOpIdx - startOpIdx) - Math.abs(entry.startOpIdx - startOpIdx);
						}

						if (startOpDeltaImprovement > 0 || startOpDeltaImprovement == 0 && srcName != null && srcName.equals(entry.srcName) && !srcName.equals(bestMatch.srcName)) {
							bestMatch = entry;
						}
					}
				}

				if (!hasMissing || bestMatch != null) return bestMatch;
			}

			if (srcName != null) {
				for (MethodVarEntry entry : vars) {
					if (entry.srcName == srcName) return entry;
				}
			}

			return null;
		}

		@Override
		public MethodVarEntry addVar(MethodVarMapping var) {
			MethodVarEntry entry = var instanceof MethodVarEntry && var.getMethod() == this ? (MethodVarEntry) var : new MethodVarEntry(this, var);

			if (vars == null) vars = new ArrayList<>();

			vars.add(entry);

			return null;
		}

		@Override
		public MethodVarEntry removeVar(int lvtRowIndex, int lvIndex, int startOpIdx, String srcName) {
			MethodVarEntry ret = getVar(lvtRowIndex, lvIndex, startOpIdx, srcName);
			if (ret != null) vars.remove(ret);

			return ret;
		}

		void accept(MappingVisitor visitor, boolean supplyDstDescs) {
			if (visitor.visitMethod(srcName, srcDesc) && acceptMember(visitor, supplyDstDescs)) {
				if (args != null) {
					for (MethodArgEntry arg : args) {
						arg.accept(visitor);
					}
				}

				if (vars != null) {
					for (MethodVarEntry var : vars) {
						var.accept(visitor);
					}
				}
			}
		}

		@Override
		public String toString() {
			return String.format("%s%s", srcName, srcDesc);
		}

		private List<MethodArgEntry> args = null;
		private List<MethodVarEntry> vars = null;
	}

	public static final class MethodArgEntry extends Entry implements MethodArgMapping {
		MethodArgEntry(MethodEntry method, int argPosition, int lvIndex, String srcName) {
			super(method.owner.tree, srcName);

			this.method = method;
			this.argPosition = argPosition;
			this.lvIndex = lvIndex;
		}

		MethodArgEntry(MethodEntry method, MethodArgMapping src) {
			super(method.owner.tree, src);

			this.method = method;
			this.argPosition = src.getArgPosition();
			this.lvIndex = src.getLvIndex();
		}

		@Override
		public MappingTree getTree() {
			return method.owner.tree;
		}

		@Override
		public MappedElementKind getKind() {
			return MappedElementKind.METHOD_ARG;
		}

		@Override
		public MethodEntry getMethod() {
			return method;
		}

		@Override
		public int getArgPosition() {
			return argPosition;
		}

		@Override
		public int getLvIndex() {
			return lvIndex;
		}

		void accept(MappingVisitor visitor) {
			if (visitor.visitMethodArg(argPosition, lvIndex, srcName)) {
				acceptElement(visitor, null);
			}
		}

		@Override
		public String toString() {
			return String.format("%d/%d:%s", argPosition, lvIndex, srcName);
		}

		private final MethodEntry method;
		private final int argPosition;
		private final int lvIndex;
	}

	public static final class MethodVarEntry extends Entry implements MethodVarMapping {
		MethodVarEntry(MethodEntry method, int lvtRowIndex, int lvIndex, int startOpIdx, String srcName) {
			super(method.owner.tree, srcName);

			this.method = method;
			this.lvtRowIndex = lvtRowIndex;
			this.lvIndex = lvIndex;
			this.startOpIdx = startOpIdx;
		}

		MethodVarEntry(MethodEntry method, MethodVarMapping src) {
			super(method.owner.tree, src);

			this.method = method;
			this.lvtRowIndex = src.getLvtRowIndex();
			this.lvIndex = src.getLvIndex();
			this.startOpIdx = src.getStartOpIdx();
		}

		@Override
		public MappingTree getTree() {
			return method.owner.tree;
		}

		@Override
		public MappedElementKind getKind() {
			return MappedElementKind.METHOD_VAR;
		}

		@Override
		public MethodEntry getMethod() {
			return method;
		}

		@Override
		public int getLvtRowIndex() {
			return lvtRowIndex;
		}

		@Override
		public int getLvIndex() {
			return lvIndex;
		}

		@Override
		public int getStartOpIdx() {
			return startOpIdx;
		}

		void accept(MappingVisitor visitor) {
			if (visitor.visitMethodVar(lvtRowIndex, lvIndex, startOpIdx, srcName)) {
				acceptElement(visitor, null);
			}
		}

		@Override
		public String toString() {
			return String.format("%d/%d@%d:%s", lvtRowIndex, lvIndex, startOpIdx, srcName);
		}

		private final MethodEntry method;
		private final int lvtRowIndex;
		private final int lvIndex;
		private final int startOpIdx;
	}

	static final class MemberKey {
		public MemberKey(String name, String desc) {
			this.name = name;
			this.desc = desc;

			if (desc == null) {
				hash = name.hashCode();
			} else {
				hash = name.hashCode() * 257 + desc.hashCode();
			}
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null || obj.getClass() != MemberKey.class) return false;

			MemberKey o = (MemberKey) obj;

			return name.equals(o.name) && Objects.equals(desc, o.desc);
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public String toString() {
			return String.format("%s.%s", name, desc);
		}

		private final String name;
		private final String desc;
		private final int hash;
	}

	private boolean indexByDstNames;
	private String srcNamespace;
	private List<String> dstNamespaces;
	private final List<Map.Entry<String, String>> metadata = new ArrayList<>();
	private final Map<String, ClassEntry> classesBySrcName = new LinkedHashMap<>();
	private Map<String, ClassEntry>[] classesByDstNames;

	private Entry currentEntry;
	private ClassEntry currentClass;
	private MethodEntry currentMethod;
}
