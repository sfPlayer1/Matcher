package matcher.mapping;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.mappingio.FlatMappingVisitor;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.adapter.RegularAsFlatMappingVisitor;
import net.fabricmc.mappingio.format.MappingFormat;

import matcher.Matcher;
import matcher.NameType;
import matcher.Util;
import matcher.type.ClassEnv;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.LocalClassEnv;
import matcher.type.Matchable;
import matcher.type.MatchableKind;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class Mappings {
	public static void load(Path path, MappingFormat format,
			String nsSource, String nsTarget,
			MappingField fieldSource, MappingField fieldTarget,
			LocalClassEnv env, final boolean replace) throws IOException {
		assert fieldTarget != MappingField.PLAIN;
		int[] dstNameCounts = new int[MatchableKind.VALUES.length];
		int[] commentCounts = new int[MatchableKind.VALUES.length];
		Set<String> warnedClasses = new HashSet<>();

		try {
			MappingReader.read(path, format, new MappingSourceNsSwitch(new MappingVisitor() {
				@Override
				public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) {
					dstNs = dstNamespaces.indexOf(nsTarget);
					if (dstNs < 0) throw new RuntimeException("missing target namespace: "+nsTarget);
				}

				@Override
				public void visitMetadata(String key, String value) {
					if (fieldTarget == MappingField.UID) {
						switch (key) {
						case Mappings.metaUidNextClass: {
							int val = Integer.parseInt(value);
							if (replace || env.getGlobal().nextClassUid < val) env.getGlobal().nextClassUid = val;
							break;
						}
						case Mappings.metaUidNextMethod: {
							int val = Integer.parseInt(value);
							if (replace || env.getGlobal().nextMethodUid < val) env.getGlobal().nextMethodUid = val;
							break;
						}
						case Mappings.metaUidNextField: {
							int val = Integer.parseInt(value);
							if (replace || env.getGlobal().nextFieldUid < val) env.getGlobal().nextFieldUid = val;
							break;
						}
						}
					}
				}

				@Override
				public boolean visitClass(String srcName) {
					method = null;
					field = null;
					arg = null;
					var = null;

					cur = cls = findClass(srcName, fieldSource, env);

					if (cls == null) {
						if (warnedClasses.add(srcName)) Matcher.LOGGER.warn("Can't find mapped class {}", srcName);
						return false;
					}

					return true;
				}

				@Override
				public boolean visitMethod(String srcName, String srcDesc) {
					field = null;
					arg = null;
					var = null;

					cur = method = cls.getMethod(srcName, srcDesc, fieldSource.type);

					if (method == null || !method.isReal()) {
						Matcher.LOGGER.warn("Can't find mapped method {}/{}{}",
								cls.getName(fieldSource.type), srcName, srcDesc);
						return false;
					}

					return true;
				}

				@Override
				public boolean visitMethodArg(int argPosition, int lvIndex, String srcArgName) {
					var = null;

					cur = arg = getMethodVar(argPosition, lvIndex, -1, -1, true);

					if (arg == null) {
						return false;
					}

					return true;
				}

				@Override
				public boolean visitMethodVar(int asmIndex, int lvIndex, int startOpIdx, int endOpIdx, String srcArgName) {
					arg = null;

					cur = var = getMethodVar(-1, lvIndex, startOpIdx, asmIndex, false);

					if (var == null) {
						return false;
					}

					return true;
				}

				private MethodVarInstance getMethodVar(int varIndex, int lvIndex, int startOpIdx, int asmIndex, boolean isArg) {
					if (isArg && varIndex < -1 || varIndex >= method.getArgs().length) {
						Matcher.LOGGER.warn("Invalid var index {} for method {}", varIndex, method);
					} else if (lvIndex < -1 || lvIndex >= (isArg ? method.getArgs() : method.getVars()).length * 2 + 1) {
						Matcher.LOGGER.warn("Invalid lv index {} for method {}", lvIndex, method);
					} else if (asmIndex < -1) {
						Matcher.LOGGER.warn("Invalid lv asm index {} for method {}", asmIndex, method);
					} else {
						if (!isArg || varIndex == -1) {
							if (asmIndex >= 0) {
								varIndex = findVarIndexByAsm(isArg ? method.getArgs() : method.getVars(), asmIndex);

								if (varIndex == -1) {
									Matcher.LOGGER.warn("Invalid lv asm index {} for method {}", asmIndex, method);
									return null;
								}
							} else if (lvIndex <= -1) {
								Matcher.LOGGER.warn("Missing arg+lvt index {} for method {}", lvIndex, method);
								return null;
							} else {
								varIndex = findVarIndexByLv(isArg ? method.getArgs() : method.getVars(), lvIndex, startOpIdx);

								if (varIndex == -1) {
									Matcher.LOGGER.warn("Invalid lv index {} for method {}", lvIndex, method);
									return null;
								}
							}
						}

						MethodVarInstance var = isArg ? method.getArg(varIndex) : method.getVar(varIndex);

						if (lvIndex != -1 && var.getLvIndex() != lvIndex) {
							Matcher.LOGGER.warn("Mismatched lv index {} for method {}", lvIndex, method);
							return null;
						}

						if (asmIndex != -1 && var.getAsmIndex() != asmIndex) {
							Matcher.LOGGER.warn("Mismatched lv asm index {} for method {}", asmIndex, method);
							return null;
						}

						return var;
					}

					return null;
				}

				@Override
				public boolean visitField(String srcName, String srcDesc) {
					method = null;
					arg = null;
					var = null;

					cur = field = cls.getField(srcName, srcDesc, fieldSource.type);

					if (field == null || !field.isReal()) {
						Matcher.LOGGER.warn("Can't find mapped field {}/{}", cls.getName(fieldSource.type), srcName);
						return false;
					}

					return true;
				}

				@Override
				public void visitDstName(MappedElementKind targetKind, int namespace, String name) {
					if (namespace != dstNs) return;

					switch (targetKind) {
					case CLASS:
						switch (fieldTarget) {
						case MAPPED:
							if (!cls.hasMappedName() || replace) {
								if (ClassInstance.hasOuterName(name)) {
									name = ClassInstance.getInnerName(name);
								}

								cls.setMappedName(name);
							}

							break;
						case AUX:
						case AUX2:
							if (!cls.hasAuxName(fieldTarget.type.getAuxIndex()) || replace) {
								if (ClassInstance.hasOuterName(name)) {
									name = ClassInstance.getInnerName(name);
								}

								cls.setAuxName(fieldTarget.type.getAuxIndex(), name);
							}

							break;
						case UID:
							String prefix = env.getGlobal().classUidPrefix;

							if (!name.startsWith(prefix)) {
								Matcher.LOGGER.warn("Invalid uid class name {}", name);
								return;
							} else {
								int innerNameStart = name.lastIndexOf('$') + 1;
								String uidStr;

								if (innerNameStart > 0) {
									int subPrefixStart = prefix.lastIndexOf('/') + 1;

									if (!name.startsWith(prefix.substring(subPrefixStart), innerNameStart)) {
										Matcher.LOGGER.warn("Invalid uid class name {}", name);
										return;
									} else {
										uidStr = name.substring(innerNameStart + prefix.length() - subPrefixStart);
									}
								} else {
									uidStr = name.substring(prefix.length());
								}

								int uid = Integer.parseInt(uidStr);

								if (uid < 0) {
									Matcher.LOGGER.warn("Invalid class uid {}", uid);
									return;
								} else if (cls.getUid() < 0 || cls.getUid() > uid || replace) {
									cls.setUid(uid);
								}
							}

							break;
						default:
							throw new IllegalArgumentException();
						}

						break;
					case FIELD:
						switch (fieldTarget) {
						case MAPPED:
							if (!field.hasMappedName() || replace) {
								for (FieldInstance f : field.getAllHierarchyMembers()) {
									f.setMappedName(name);
								}
							}

							break;
						case AUX:
						case AUX2:
							if (!field.hasAuxName(fieldTarget.type.getAuxIndex()) || replace) {
								for (FieldInstance f : field.getAllHierarchyMembers()) {
									f.setAuxName(fieldTarget.type.getAuxIndex(), name);
								}
							}

							break;
						case UID:
							String prefix = env.getGlobal().fieldUidPrefix;

							if (!name.startsWith(prefix)) {
								Matcher.LOGGER.warn("Invalid uid field name {}", name);
								return;
							} else {
								int uid = Integer.parseInt(name.substring(prefix.length()));

								if (uid < 0) {
									Matcher.LOGGER.warn("Invalid field uid {}", uid);
									return;
								} else if (field.getUid() < 0 || field.getUid() > uid || replace) {
									for (FieldInstance f : field.getAllHierarchyMembers()) {
										f.setUid(uid);
									}
								}
							}

							break;
						default:
							throw new IllegalArgumentException();
						}

						break;
					case METHOD:
						switch (fieldTarget) {
						case MAPPED:
							if (!method.hasMappedName() || replace) {
								for (MethodInstance m : method.getAllHierarchyMembers()) {
									m.setMappedName(name);
								}
							}

							break;
						case AUX:
						case AUX2:
							if (!method.hasAuxName(fieldTarget.type.getAuxIndex()) || replace) {
								for (MethodInstance m : method.getAllHierarchyMembers()) {
									m.setAuxName(fieldTarget.type.getAuxIndex(), name);
								}
							}

							break;
						case UID:
							String prefix = env.getGlobal().methodUidPrefix;

							if (!name.startsWith(prefix)) {
								Matcher.LOGGER.warn("Invalid uid method name {}", name);
								return;
							} else {
								int uid = Integer.parseInt(name.substring(prefix.length()));

								if (uid < 0) {
									Matcher.LOGGER.warn("Invalid method uid {}", uid);
									return;
								} else if (method.getUid() < 0 || method.getUid() > uid || replace) {
									for (MethodInstance m : method.getAllHierarchyMembers()) {
										m.setUid(uid);
									}
								}
							}

							break;
						default:
							throw new IllegalArgumentException();
						}

						break;
					case METHOD_ARG:
						switch (fieldTarget) {
						case MAPPED:
							if (!arg.hasMappedName() || replace) arg.setMappedName(name);
							break;
						case AUX:
						case AUX2:
							if (!arg.hasAuxName(fieldTarget.type.getAuxIndex()) || replace) arg.setAuxName(fieldTarget.type.getAuxIndex(), name);
							break;
						case UID:
							// not applicable
							break;
						default:
							throw new IllegalArgumentException();
						}

						break;
					case METHOD_VAR:
						switch (fieldTarget) {
						case MAPPED:
							if (!var.hasMappedName() || replace) var.setMappedName(name);
							break;
						case AUX:
						case AUX2:
							if (!var.hasAuxName(fieldTarget.type.getAuxIndex()) || replace) var.setAuxName(fieldTarget.type.getAuxIndex(), name);
							break;
						case UID:
							// not applicable
							break;
						default:
							throw new IllegalArgumentException();
						}

						break;
					}

					dstNameCounts[cur.getKind().ordinal()]++;
				}

				@Override
				public void visitComment(MappedElementKind targetKind, String comment) {
					if (cur.getMappedComment() == null || replace) {
						cur.setMappedComment(comment);
						commentCounts[cur.getKind().ordinal()]++;
					}
				}

				private int dstNs;

				private ClassInstance cls;
				private FieldInstance field;
				private MethodInstance method;
				private MethodVarInstance arg;
				private MethodVarInstance var;

				private Matchable<?> cur;
			}, nsSource));
		} catch (Throwable t) {
			clear(env);
			throw t;
		}

		Matcher.LOGGER.info("Loaded mappings for {} classes, {} methods ({} args, {} vars) and {} fields (comments: {}/{}/{}).",
				dstNameCounts[MatchableKind.CLASS.ordinal()],
				dstNameCounts[MatchableKind.METHOD.ordinal()],
				dstNameCounts[MatchableKind.METHOD_ARG.ordinal()],
				dstNameCounts[MatchableKind.METHOD_VAR.ordinal()],
				dstNameCounts[MatchableKind.FIELD.ordinal()],
				commentCounts[MatchableKind.CLASS.ordinal()],
				commentCounts[MatchableKind.METHOD.ordinal()],
				commentCounts[MatchableKind.FIELD.ordinal()]);
	}

	private static ClassInstance findClass(String name, MappingField type, LocalClassEnv env) {
		switch (type) {
		case PLAIN:
			return env.getLocalClsByName(name);
		case MAPPED:
		case AUX:
		case AUX2:
		case UID:
			ClassInstance ret = env.getClsByName(name, type.type);

			return ret != null && !ret.isShared() ? ret : null;
		default:
			throw new IllegalArgumentException();
		}
	}

	private static int findVarIndexByLv(MethodVarInstance[] vars, int lvIndex, int startOpcodeIdx) {
		MethodVarInstance ret = null;

		for (MethodVarInstance arg : vars) {
			if (arg.getLvIndex() == lvIndex
					&& arg.getStartOpIdx() >= startOpcodeIdx // assumes matcher's startInsn is not early, also works with startInsn == -1
					&& (ret == null || arg.getStartOpIdx() < ret.getStartOpIdx())) {
				ret = arg;
			}
		}

		return ret != null ? ret.getIndex() : -1;
	}

	private static int findVarIndexByAsm(MethodVarInstance[] vars, int asmIndex) {
		for (MethodVarInstance arg : vars) {
			if (arg.getAsmIndex() == asmIndex) return arg.getIndex();
		}

		return -1;
	}

	public static boolean save(Path file, MappingFormat format, LocalClassEnv env,
			List<NameType> nsTypes, List<String> nsNames,
			MappingsExportVerbosity verbosity, boolean forAnyInput, boolean fieldsFirst) throws IOException {
		if (nsTypes.size() < 2 || nsTypes.size() > 2 && !format.hasNamespaces) throw new IllegalArgumentException("invalid namespace count");
		if (nsNames != null && nsNames.size() != nsTypes.size()) throw new IllegalArgumentException("namespace types and names don't have the same number of entries");

		if (nsNames == null) {
			nsNames = new ArrayList<>(nsTypes.size());
		} else {
			nsNames = new ArrayList<>(nsNames);
		}

		for (int i = 0; i < nsTypes.size(); i++) {
			if (i >= nsNames.size() || nsNames.get(i) == null || nsNames.get(i).isEmpty()) {
				String name = getNamespaceName(nsTypes.get(i));

				if (i == nsNames.size()) { // > shouldn't be possible
					nsNames.add(name);
				} else {
					nsNames.set(i, name);
				}
			}
		}

		NameType sortNs = nsTypes.get(0);
		Comparator<ClassInstance> clsCmp = MappedElementComparators.byNameShortFirstNestaware(sortNs);
		Comparator<MemberInstance<?>> memberCmp = MappedElementComparators.byNameDescConcat(sortNs);
		Comparator<MethodVarInstance> methodArgCmp = MappedElementComparators.byLvIndex();
		Comparator<MethodVarInstance> methodVarCmp = MappedElementComparators.byLvIndex();

		List<ClassInstance> classes = new ArrayList<>(env.getClasses());
		classes.removeIf(cls -> !cls.isInput() || !cls.isReal());
		if (classes.isEmpty()) return false;

		classes.sort(clsCmp);

		String[] dstClassNames = new String[nsTypes.size() - 1];
		String[] dstMemberNames = new String[dstClassNames.length];
		String[] dstMemberDescs = new String[dstClassNames.length];
		String[] dstVarNames = new String[dstClassNames.length];

		List<MethodInstance> methods = new ArrayList<>();
		List<FieldInstance> fields = new ArrayList<>();
		List<MethodVarInstance> vars = new ArrayList<>();
		Set<Set<MethodInstance>> exportedHierarchies = verbosity == MappingsExportVerbosity.MINIMAL ? Util.newIdentityHashSet() : null;

		FlatMappingVisitor writer = new RegularAsFlatMappingVisitor(MappingWriter.create(file, format));
		writer.visitNamespaces(nsNames.get(0), nsNames.subList(1, nsNames.size()));

		for (ClassInstance cls : classes) {
			String srcClsName = cls.getName(nsTypes.get(0));
			if (srcClsName == null) continue;
			boolean hasAnyDstName = false;

			for (int i = 1; i < nsTypes.size(); i++) {
				NameType dstType = nsTypes.get(i);
				String dstName = cls.getName(dstType); // FIXME: this may actually not be unique, record exported and suffix conflicting?

				if (dstName != null && (dstName.equals(srcClsName) || dstType != dstType.withMapped(false) && cls.hasNoFullyMappedName())) {
					// don't save no-op or partial mappings (partial = only outer class is mapped)
					dstName = null;
				}

				hasAnyDstName |= dstName != null;
				dstClassNames[i - 1] = dstName;
			}

			if (!hasAnyDstName
					&& (!format.supportsComments || cls.getMappedComment() == null)
					&& !shouldExportAny(cls.getMethods(), format, nsTypes, verbosity, forAnyInput, exportedHierarchies)
					&& !shouldExportAny(cls.getFields(), format, nsTypes)) {
				continue; // no data for the class, skip
			}

			if (!writer.visitClass(srcClsName, dstClassNames)) {
				continue;
			}

			// comment

			if (cls.getMappedComment() != null) writer.visitClassComment(srcClsName, dstClassNames, cls.getMappedComment());

			if (fieldsFirst) {
				exportFields(cls, srcClsName, dstClassNames, format, nsTypes,
						dstMemberNames, dstMemberDescs, fields, memberCmp, writer);
			}

			exportMethods(cls, srcClsName, dstClassNames,
					format, nsTypes, verbosity, forAnyInput,
					dstMemberNames, dstMemberDescs, dstVarNames,
					methods, vars, exportedHierarchies,
					memberCmp, methodArgCmp, methodVarCmp,
					writer);

			if (!fieldsFirst) {
				exportFields(cls, srcClsName, dstClassNames, format, nsTypes,
						dstMemberNames, dstMemberDescs, fields, memberCmp, writer);
			}
		}

		writer.visitEnd();
		return true;
	}

	private static void exportMethods(ClassInstance cls, String srcClsName, String[] dstClassNames,
			MappingFormat format, List<NameType> nsTypes, MappingsExportVerbosity verbosity, boolean forAnyInput,
			String[] dstMemberNames, String[] dstMemberDescs, String[] dstVarNames,
			List<MethodInstance> methods, List<MethodVarInstance> vars, Set<Set<MethodInstance>> exportedHierarchies,
			Comparator<? super MethodInstance> methodCmp, Comparator<MethodVarInstance> methodArgCmp, Comparator<MethodVarInstance> methodVarCmp,
			FlatMappingVisitor writer) throws IOException {
		for (MethodInstance m : cls.getMethods()) {
			if (shouldExport(m, format, nsTypes, verbosity, forAnyInput, exportedHierarchies)) methods.add(m);
		}

		methods.sort(methodCmp);

		for (MethodInstance m : methods) {
			assert m.getCls() == cls;

			String srcName = m.getName(nsTypes.get(0));
			assert srcName != null; // shouldExport already verified this
			boolean hasAnyDstName = false;

			for (int i = 1; i < nsTypes.size(); i++) {
				NameType dstType = nsTypes.get(i);
				String dstName = m.getName(dstType);

				if (dstName != null && dstName.equals(srcName)) { // no-op mapping
					dstName = null;
				}

				hasAnyDstName |= dstName != null;
				dstMemberNames[i - 1] = dstName;
				dstMemberDescs[i - 1] = m.getDesc(dstType);
			}

			String[] dstMethodNames;

			if (hasAnyDstName && shouldExportName(m, verbosity, forAnyInput, exportedHierarchies)) {
				if (verbosity == MappingsExportVerbosity.MINIMAL) exportedHierarchies.add(m.getAllHierarchyMembers());
				dstMethodNames = dstMemberNames;
			} else {
				dstMethodNames = null;
			}

			String desc = m.getDesc(nsTypes.get(0));

			if (!writer.visitMethod(srcClsName, srcName, desc, dstClassNames, dstMethodNames, dstMemberDescs)) {
				continue;
			}

			if (format.supportsComments) {
				String comment = m.getMappedComment();
				if (comment != null) writer.visitMethodComment(srcClsName, srcName, desc, dstClassNames, dstMethodNames, dstMemberDescs, comment);
			}

			// method args, vars

			if (format.supportsArgs || format.supportsLocals) {
				for (int k = 0; k < 2; k++) {
					boolean isArg = k == 0;
					MethodVarInstance[] instances;

					if (isArg) { // arg
						if (!format.supportsArgs) continue;
						instances = m.getArgs();
					} else { // var
						if (!format.supportsLocals) continue;
						instances = m.getVars();
					}

					for (MethodVarInstance arg : instances) {
						if (shouldExport(arg, format, nsTypes)) vars.add(arg);
					}

					vars.sort(isArg ? methodArgCmp : methodVarCmp);

					for (MethodVarInstance var : vars) {
						assert var.getMethod() == m;

						String srcVarName = var.getName(nsTypes.get(0));
						hasAnyDstName = false;

						for (int i = 1; i < nsTypes.size(); i++) {
							NameType dstType = nsTypes.get(i);
							String dstName = var.getName(dstType);

							if (dstName != null && dstName.equals(srcVarName)) { // no-op mapping
								dstName = null;
							}

							hasAnyDstName |= dstName != null;
							dstVarNames[i - 1] = dstName;
						}

						if (hasAnyDstName) {
							if (isArg) {
								writer.visitMethodArg(srcClsName, srcName, desc, var.getIndex(), var.getLvIndex(), srcVarName,
										dstClassNames, dstMethodNames, dstMemberDescs, dstVarNames);
							} else {
								writer.visitMethodVar(srcClsName, srcName, desc,
										var.getAsmIndex(), var.getLvIndex(), var.getStartOpIdx(), -1, srcVarName,
										dstClassNames, dstMethodNames, dstMemberDescs, dstVarNames);
							}
						}

						if (format.supportsComments) {
							String comment = var.getMappedComment();

							if (comment != null) {
								if (isArg) {
									writer.visitMethodArgComment(srcClsName, srcName, desc, var.getIndex(), var.getLvIndex(), srcVarName,
											dstClassNames, dstMethodNames, dstMemberDescs, dstVarNames,
											comment);
								} else {
									writer.visitMethodVarComment(srcClsName, srcName, desc,
											var.getAsmIndex(), var.getLvIndex(), var.getStartOpIdx(), -1, srcVarName,
											dstClassNames, dstMethodNames, dstMemberDescs, dstVarNames,
											comment);
								}
							}
						}
					}

					vars.clear();
				}
			}
		}

		methods.clear();
	}

	private static void exportFields(ClassInstance cls, String srcClsName, String[] dstClassNames,
			MappingFormat format, List<NameType> nsTypes,
			String[] dstMemberNames, String[] dstMemberDescs,
			List<FieldInstance> fields,
			Comparator<? super FieldInstance> fieldCmp,
			FlatMappingVisitor writer) throws IOException {
		for (FieldInstance f : cls.getFields()) {
			if (shouldExport(f, format, nsTypes)) fields.add(f);
		}

		fields.sort(fieldCmp);

		for (FieldInstance f : fields) {
			assert f.getCls() == cls;

			String srcName = f.getName(nsTypes.get(0));
			assert srcName != null; // shouldExport already verified this

			for (int i = 1; i < nsTypes.size(); i++) {
				NameType dstType = nsTypes.get(i);
				String dstName = f.getName(dstType);

				if (dstName != null && dstName.equals(srcName)) { // no-op mapping
					dstName = null;
				}

				dstMemberNames[i - 1] = dstName;
				dstMemberDescs[i - 1] = f.getDesc(dstType);
			}

			String desc = f.getDesc(nsTypes.get(0));

			if (!writer.visitField(srcClsName, srcName, desc, dstClassNames, dstMemberNames, dstMemberDescs)) {
				continue;
			}

			if (format.supportsComments) {
				String comment = f.getMappedComment();
				if (comment != null) writer.visitFieldComment(srcClsName, srcName, desc, dstClassNames, dstMemberNames, dstMemberDescs, comment);
			}
		}

		fields.clear();
	}

	private static String getNamespaceName(NameType type) {
		switch (type) {
		case MAPPED:
		case MAPPED_PLAIN:
			return "named";
		case PLAIN:
			return "official";
		case LOCTMP_PLAIN:
		case TMP_PLAIN:
			return "tmp";
		case UID_PLAIN:
			return "intermediary";
		case MAPPED_LOCTMP_PLAIN:
		case MAPPED_TMP_PLAIN:
			return "named-tmp";
		case AUX:
		case AUX2:
		case AUX_PLAIN:
		case AUX2_PLAIN:
			return type.getAuxIndex() > 0 ? String.format("aux%d", type.getAuxIndex()) : "aux";
		case MAPPED_AUX_PLAIN:
			return "named-aux";
		default: throw new IllegalArgumentException();
		}
	}

	private static boolean shouldExport(MethodInstance method, MappingFormat format, List<NameType> nsTypes,
			MappingsExportVerbosity verbosity, boolean forAnyInput, Set<Set<MethodInstance>> exportedHierarchies) {
		String srcName = method.getName(nsTypes.get(0));
		if (srcName == null) return false;

		return format.supportsComments && method.getMappedComment() != null
				|| format.supportsArgs && shouldExportAny(method.getArgs(), format, nsTypes)
				|| format.supportsLocals && shouldExportAny(method.getVars(), format, nsTypes)
				|| hasAnyNames(method, srcName, nsTypes) && shouldExportName(method, verbosity, forAnyInput, exportedHierarchies);
	}

	private static boolean shouldExport(MethodVarInstance var, MappingFormat format, List<NameType> nsTypes) {
		String srcName = var.getName(nsTypes.get(0));

		return format.supportsComments && var.getMappedComment() != null || hasAnyNames(var, srcName, nsTypes);
	}

	private static boolean shouldExport(FieldInstance field, MappingFormat format, List<NameType> nsTypes) {
		String srcName = field.getName(nsTypes.get(0));

		return srcName != null
				&& (format.supportsComments && field.getMappedComment() != null || hasAnyNames(field, srcName, nsTypes));
	}

	private static boolean hasAnyNames(Matchable<?> m, String srcName, List<NameType> nsTypes) {
		for (int i = 1; i < nsTypes.size(); i++) {
			String dstName = m.getName(nsTypes.get(i));

			if (dstName != null && !dstName.equals(srcName)) return true;
		}

		return false;
	}

	private static boolean shouldExportAny(MethodInstance[] methods, MappingFormat format, List<NameType> nsTypes, MappingsExportVerbosity verbosity, boolean forAnyInput, Set<Set<MethodInstance>> exportedHierarchies) {
		for (MethodInstance m : methods) {
			if (shouldExport(m, format, nsTypes, verbosity, forAnyInput, exportedHierarchies)) return true;
		}

		return false;
	}

	private static boolean shouldExportAny(MethodVarInstance[] vars, MappingFormat format, List<NameType> nsTypes) {
		for (MethodVarInstance v : vars) {
			if (shouldExport(v, format, nsTypes)) return true;
		}

		return false;
	}

	private static boolean shouldExportAny(FieldInstance[] fields, MappingFormat format, List<NameType> nsTypes) {
		for (FieldInstance f : fields) {
			if (shouldExport(f, format, nsTypes)) return true;
		}

		return false;
	}

	private static boolean shouldExportName(MethodInstance method, MappingsExportVerbosity verbosity, boolean forAnyInput, Set<Set<MethodInstance>> exportedHierarchies) {
		return verbosity == MappingsExportVerbosity.FULL
				|| method.getAllHierarchyMembers().size() == 1
				|| (method.getParents().isEmpty() || forAnyInput && isAnyInputRoot(method))
				&& (verbosity == MappingsExportVerbosity.ROOTS || !exportedHierarchies.contains(method.getAllHierarchyMembers())); // FIXME: forAnyInput + minimal needs to use an exportedHierarchies set per origin
	}

	private static boolean isAnyInputRoot(MethodInstance method) {
		ClassInstance cls = method.getCls();
		String name = method.getName();
		String desc = method.getDesc();

		// check if each origin that supplies this method has a parent within the same origin

		for (int i = 0; i < cls.getAsmNodes().length; i++) {
			for (MethodNode m : cls.getAsmNodes()[i].methods) {
				if (m.name.equals(method.getName())
						&& m.desc.equals(method.getDesc())) {
					if (!hasParentMethod(name, desc, method.getParents(), cls.getAsmNodeOrigin(i))) {
						return true;
					} else {
						break;
					}
				}
			}
		}

		return false;
	}

	private static boolean hasParentMethod(String name, String desc, Collection<MethodInstance> parents, URI reqOrigin) {
		// check direct parents (must supply the method from the required origin)

		for (MethodInstance parent : parents) {
			ClassInstance parentCls = parent.getCls();

			for (int i = 0; i < parentCls.getAsmNodes().length; i++) {
				if (parentCls.getAsmNodeOrigin(i).equals(reqOrigin)) {
					for (MethodNode m : parentCls.getAsmNodes()[i].methods) {
						if (m.name.equals(name)
								&& m.desc.equals(desc)) {
							return true;
						}
					}
				}
			}
		}

		// check indirect parents recursively

		for (MethodInstance parent : parents) {
			if (!parent.getParents().isEmpty() && hasParentMethod(name, desc, parent.getParents(), reqOrigin)) {
				return true;
			}
		}

		return false;
	}

	public static void clear(ClassEnv env) {
		for (ClassInstance cls : env.getClasses()) {
			if (!cls.isReal()) continue;

			cls.setMappedName(null);
			cls.setMappedComment(null);

			for (MethodInstance method : cls.getMethods()) {
				method.setMappedName(null);
				method.setMappedComment(null);

				for (MethodVarInstance arg : method.getArgs()) {
					arg.setMappedName(null);
					arg.setMappedComment(null);
				}

				for (MethodVarInstance var : method.getVars()) {
					var.setMappedName(null);
					var.setMappedComment(null);
				}
			}

			for (FieldInstance field : cls.getFields()) {
				field.setMappedName(null);
				field.setMappedComment(null);
			}
		}
	}

	public static final String metaUidNextClass = "uid-next-class";
	public static final String metaUidNextMethod = "uid-next-method";
	public static final String metaUidNextField = "uid-next-field";
}
