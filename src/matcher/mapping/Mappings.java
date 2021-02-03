package matcher.mapping;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import matcher.NameType;
import matcher.Util;
import matcher.type.ClassEnv;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.LocalClassEnv;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class Mappings {
	public static void load(Path path, MappingFormat format,
			String nsSource, String nsTarget,
			MappingField fieldSource, MappingField fieldTarget,
			LocalClassEnv env, final boolean replace) throws IOException {
		assert fieldTarget != MappingField.PLAIN;
		int[] counts = new int[7];
		Set<String> warnedClasses = new HashSet<>();

		try {
			MappingReader.read(path, format, nsSource, nsTarget, new MappingAcceptor() {
				@Override
				public void acceptClass(String srcName, String dstName, boolean includesOuterNames) {
					ClassInstance cls = findClass(srcName, fieldSource, env);

					if (cls == null) {
						if (warnedClasses.add(srcName)) System.out.println("can't find mapped class "+srcName+" ("+dstName+")");
					} else {
						switch (fieldTarget) {
						case MAPPED:
							if (!cls.hasMappedName() || replace) {
								if (ClassInstance.hasOuterName(dstName)) {
									if (!includesOuterNames) {
										System.out.println("Ignoring extra outer name parts for "+dstName);
									}

									dstName = ClassInstance.getInnerName(dstName);
								}

								cls.setMappedName(dstName);
							}

							break;
						case AUX:
						case AUX2:
							if (!cls.hasAuxName(fieldTarget.type.getAuxIndex()) || replace) {
								if (ClassInstance.hasOuterName(dstName)) {
									if (!includesOuterNames) {
										System.out.println("Ignoring extra outer name parts for "+dstName);
									}

									dstName = ClassInstance.getInnerName(dstName);
								}

								cls.setAuxName(fieldTarget.type.getAuxIndex(), dstName);
							}

							break;
						case UID:
							String prefix = env.getGlobal().classUidPrefix;

							if (!dstName.startsWith(prefix)) {
								System.out.println("Invalid uid class name "+dstName);
								return;
							} else {
								int innerNameStart = dstName.lastIndexOf('$') + 1;
								String uidStr;

								if (innerNameStart > 0) {
									if (!includesOuterNames) {
										System.out.println("Ignoring extra outer name parts for "+dstName);
									}

									int subPrefixStart = prefix.lastIndexOf('/') + 1;

									if (!dstName.startsWith(prefix.substring(subPrefixStart), innerNameStart)) {
										System.out.println("Invalid uid class name "+dstName);
										return;
									} else {
										uidStr = dstName.substring(innerNameStart + prefix.length() - subPrefixStart);
									}
								} else {
									uidStr = dstName.substring(prefix.length());
								}

								int uid = Integer.parseInt(uidStr);

								if (uid < 0) {
									System.out.println("Invalid class uid "+uid);
									return;
								} else if (cls.getUid() < 0 || cls.getUid() > uid || replace) {
									cls.setUid(uid);
								}
							}

							break;
						default:
							throw new IllegalArgumentException();
						}

						counts[0]++;
					}
				}

				@Override
				public void acceptClassComment(String srcName, String comment) {
					ClassInstance cls = findClass(srcName, fieldSource, env);

					if (cls == null) {
						if (warnedClasses.add(srcName)) System.out.println("can't find mapped class "+srcName);
					} else {
						if (cls.getMappedComment() == null || replace) cls.setMappedComment(comment);
						counts[1]++;
					}
				}

				@Override
				public void acceptMethod(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) {
					ClassInstance cls = findClass(srcClsName, fieldSource, env);
					MethodInstance method;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((method = cls.getMethod(srcName, srcDesc, fieldSource.type)) == null || !method.isReal()) {
						System.out.printf("can't find mapped method %s/%s%s (%s%s)%n",
								srcClsName, srcName, srcDesc,
								(cls.hasMappedName() ? cls.getName(NameType.MAPPED)+"/" : ""), dstName);
					} else {
						switch (fieldTarget) {
						case MAPPED:
							if (!method.hasMappedName() || replace) {
								for (MethodInstance m : method.getAllHierarchyMembers()) {
									m.setMappedName(dstName);
								}
							}

							break;
						case AUX:
						case AUX2:
							if (!method.hasAuxName(fieldTarget.type.getAuxIndex()) || replace) {
								for (MethodInstance m : method.getAllHierarchyMembers()) {
									m.setAuxName(fieldTarget.type.getAuxIndex(), dstName);
								}
							}

							break;
						case UID:
							String prefix = env.getGlobal().methodUidPrefix;

							if (!dstName.startsWith(prefix)) {
								System.out.println("Invalid uid method name "+dstName);
								return;
							} else {
								int uid = Integer.parseInt(dstName.substring(prefix.length()));

								if (uid < 0) {
									System.out.println("Invalid method uid "+uid);
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

						counts[2]++;
					}
				}

				@Override
				public void acceptMethodComment(String srcClsName, String srcName, String srcDesc, String comment) {
					ClassInstance cls = findClass(srcClsName, fieldSource, env);
					MethodInstance method;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((method = cls.getMethod(srcName, srcDesc, fieldSource.type)) == null || !method.isReal()) {
						System.out.println("can't find mapped method "+srcClsName+"/"+srcName);
					} else {
						if (method.getMappedComment() == null || replace) method.setMappedComment(comment);
						counts[3]++;
					}
				}

				@Override
				public void acceptMethodArg(String srcClsName, String srcMethodName, String srcMethodDesc,
						int argIndex, int lvIndex, String srcArgName, String dstArgName) {
					MethodVarInstance arg = getMethodVar(srcClsName, srcMethodName, srcMethodDesc, argIndex, lvIndex, -1, -1, true);

					if (arg != null) {
						switch (fieldTarget) {
						case MAPPED:
							if (!arg.hasMappedName() || replace) arg.setMappedName(dstArgName);
							break;
						case AUX:
						case AUX2:
							if (!arg.hasAuxName(fieldTarget.type.getAuxIndex()) || replace) arg.setAuxName(fieldTarget.type.getAuxIndex(), dstArgName);
							break;
						case UID:
							// not applicable
							break;
						default:
							throw new IllegalArgumentException();
						}

						counts[4]++;
					}
				}

				@Override
				public void acceptMethodArgComment(String srcClsName, String srcMethodName, String srcMethodDesc,
						int argIndex, int lvIndex, String comment) {
					MethodVarInstance arg = getMethodVar(srcClsName, srcMethodName, srcMethodDesc, argIndex, lvIndex, -1, -1, true);

					if (arg != null) {
						if (arg.getMappedComment() == null || replace) arg.setMappedComment(comment);

						//counts[4]++;
					}
				}

				@Override
				public void acceptMethodVar(String srcClsName, String srcMethodName, String srcMethodDesc,
						int varIndex, int lvIndex, int startOpIdx, int asmIndex, String srcArgName, String dstVarName) {
					MethodVarInstance var = getMethodVar(srcClsName, srcMethodName, srcMethodDesc, varIndex, lvIndex, startOpIdx, asmIndex, false);

					if (var != null) {
						switch (fieldTarget) {
						case MAPPED:
							if (!var.hasMappedName() || replace) var.setMappedName(dstVarName);
							break;
						case AUX:
						case AUX2:
							if (!var.hasAuxName(fieldTarget.type.getAuxIndex()) || replace) var.setAuxName(fieldTarget.type.getAuxIndex(), dstVarName);
							break;
						case UID:
							// not applicable
							break;
						default:
							throw new IllegalArgumentException();
						}

						counts[4]++;
					}
				}

				@Override
				public void acceptMethodVarComment(String srcClsName, String srcMethodName, String srcMethodDesc,
						int varIndex, int lvIndex, int startOpIdx, int asmIndex, String comment) {
					MethodVarInstance var = getMethodVar(srcClsName, srcMethodName, srcMethodDesc, varIndex, lvIndex, startOpIdx, asmIndex, false);

					if (var != null) {
						if (var.getMappedComment() == null || replace) var.setMappedComment(comment);

						//counts[4]++;
					}
				}

				private MethodVarInstance getMethodVar(String srcClsName, String srcName, String srcDesc,
						int varIndex, int lvIndex, int startOpIdx, int asmIndex, boolean isArg) {
					ClassInstance cls = findClass(srcClsName, fieldSource, env);
					MethodInstance method;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((method = cls.getMethod(srcName, srcDesc, fieldSource.type)) == null || !method.isReal()) {
						System.out.println("can't find mapped method "+srcClsName+"/"+srcName);
					} else if (varIndex < -1 || varIndex >= (isArg ? method.getArgs() : method.getVars()).length) {
						System.out.println("invalid var index "+varIndex+" for method "+method);
					} else if (lvIndex < -1 || lvIndex >= (isArg ? method.getArgs() : method.getVars()).length * 2 + 1) {
						System.out.println("invalid lv index "+lvIndex+" for method "+method);
					} else if (asmIndex < -1) {
						System.out.println("invalid lv asm index "+asmIndex+" for method "+method);
					} else {
						if (varIndex == -1) {
							if (asmIndex >= 0) {
								varIndex = findVarIndexByAsm(isArg ? method.getArgs() : method.getVars(), asmIndex);

								if (varIndex == -1) {
									System.out.println("invalid lv asm index "+asmIndex+" for method "+method);
									return null;
								}
							} else if (lvIndex <= -1) {
								System.out.println("missing arg+lvt index "+lvIndex+" for method "+method);
								return null;
							} else {
								varIndex = findVarIndexByLv(isArg ? method.getArgs() : method.getVars(), lvIndex, startOpIdx);

								if (varIndex == -1) {
									System.out.println("invalid lv index "+lvIndex+" for method "+method);
									return null;
								}
							}
						}

						MethodVarInstance var = isArg ? method.getArg(varIndex) : method.getVar(varIndex);

						if (lvIndex != -1 && var.getLvIndex() != lvIndex) {
							System.out.println("mismatched lv index "+lvIndex+" for method "+method);
							return null;
						}

						if (asmIndex != -1 && var.getAsmIndex() != asmIndex) {
							System.out.println("mismatched lv asm index "+asmIndex+" for method "+method);
							return null;
						}

						return var;
					}

					return null;
				}

				@Override
				public void acceptField(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) {
					ClassInstance cls = findClass(srcClsName, fieldSource, env);
					FieldInstance field;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((field = cls.getField(srcName, srcDesc, fieldSource.type)) == null || !field.isReal()) {
						System.out.println("can't find mapped field "+srcClsName+"/"+srcName+" ("+(cls.hasMappedName() ? cls.getName(NameType.MAPPED)+"/" : "")+dstName+")");
					} else {
						switch (fieldTarget) {
						case MAPPED:
							if (!field.hasMappedName() || replace) {
								for (FieldInstance f : field.getAllHierarchyMembers()) {
									f.setMappedName(dstName);
								}
							}

							break;
						case AUX:
						case AUX2:
							if (!field.hasAuxName(fieldTarget.type.getAuxIndex()) || replace) {
								for (FieldInstance f : field.getAllHierarchyMembers()) {
									f.setAuxName(fieldTarget.type.getAuxIndex(), dstName);
								}
							}

							break;
						case UID:
							String prefix = env.getGlobal().fieldUidPrefix;

							if (!dstName.startsWith(prefix)) {
								System.out.println("Invalid uid field name "+dstName);
								return;
							} else {
								int uid = Integer.parseInt(dstName.substring(prefix.length()));

								if (uid < 0) {
									System.out.println("Invalid field uid "+uid);
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

						counts[5]++;
					}
				}

				@Override
				public void acceptFieldComment(String srcClsName, String srcName, String srcDesc, String comment) {
					ClassInstance cls = findClass(srcClsName, fieldSource, env);
					FieldInstance field;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((field = cls.getField(srcName, srcDesc, fieldSource.type)) == null || !field.isReal()) {
						System.out.println("can't find mapped field "+srcClsName+"/"+srcName);
					} else {
						if (field.getMappedComment() == null || replace) field.setMappedComment(comment);
						counts[6]++;
					}
				}

				@Override
				public void acceptMeta(String key, String value) {
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
			});
		} catch (Throwable t) {
			clear(env);
			throw t;
		}

		System.out.printf("Loaded mappings for %d classes, %d methods (%d args) and %d fields (comments: %d/%d/%d).%n",
				counts[0], counts[2], counts[4], counts[5], counts[1], counts[3], counts[6]);
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

	public static boolean save(Path file, MappingFormat format, LocalClassEnv env, NameType srcType, NameType dstType, MappingsExportVerbosity verbosity) throws IOException {
		List<ClassInstance> classes = env.getClasses().stream()
				.sorted(ClassInstance.nameComparator)
				.collect(Collectors.toList());
		if (classes.isEmpty()) return false;

		List<MethodInstance> methods = new ArrayList<>();
		List<FieldInstance> fields = new ArrayList<>();
		List<MethodVarInstance> vars = new ArrayList<>();
		Set<Set<MethodInstance>> exportedHierarchies = verbosity == MappingsExportVerbosity.MINIMAL ? Util.newIdentityHashSet() : null;

		try (MappingWriter writer = new MappingWriter(file, format, srcType, dstType)) {
			for (ClassInstance cls : classes) {
				String srcClsName = cls.getName(srcType);
				if (srcClsName == null) continue;

				String dstClsName = cls.getName(dstType);

				if (dstClsName != null && (dstClsName.equals(srcClsName) || dstType != dstType.withMapped(false) && cls.hasNoFullyMappedName())) {
					// don't save no-op or partial mappings (partial = only outer class is mapped)
					dstClsName = null;
				}

				if (dstClsName == null
						&& (!format.supportsComments || cls.getMappedComment() == null)
						&& !shouldExportAny(cls.getMethods(), format, srcType, dstType, verbosity, exportedHierarchies)
						&& !shouldExportAny(cls.getFields(), format, srcType, dstType)) {
					continue; // no data for the class, skip
				}

				writer.acceptClass(srcClsName, dstClsName, true);

				// comment

				if (cls.getMappedComment() != null) writer.acceptClassComment(srcClsName, cls.getMappedComment());

				// methods

				for (MethodInstance m : cls.getMethods()) {
					if (shouldExport(m, format, srcType, dstType, verbosity, exportedHierarchies)) methods.add(m);
				}

				methods.sort(MemberInstance.nameComparator);

				for (MethodInstance m : methods) {
					assert m.getCls() == cls;

					String srcName = m.getName(srcType);
					String desc = getDesc(m, srcType);
					String dstName = m.getName(dstType);

					if (dstName != null && dstName.equals(srcName)) {
						dstName = null;
					}

					if (dstName != null && shouldExportName(m, verbosity, exportedHierarchies)) {
						if (verbosity == MappingsExportVerbosity.MINIMAL) exportedHierarchies.add(m.getAllHierarchyMembers());
					} else {
						dstName = null;
					}

					writer.acceptMethod(srcClsName, srcName, desc, dstClsName, dstName, getDesc(m, dstType));

					if (format.supportsComments) {
						String comment = m.getMappedComment();
						if (comment != null) writer.acceptMethodComment(srcClsName, srcName, desc, comment);
					}

					// method args

					if (format.supportsArgs) {
						for (MethodVarInstance arg : m.getArgs()) {
							if (shouldExport(arg, format, srcType, dstType)) vars.add(arg);
						}

						// TODO: sort vars

						for (MethodVarInstance arg : vars) {
							assert arg.getMethod() == m;

							String srcVarName = arg.getName(srcType);
							String dstVarName = arg.getName(dstType);

							if (dstVarName != null && !dstVarName.equals(srcVarName)) {
								writer.acceptMethodArg(srcClsName, srcName, desc, arg.getIndex(), arg.getLvIndex(), srcVarName, dstVarName);
							}

							if (format.supportsComments) {
								String comment = arg.getMappedComment();
								if (comment != null) writer.acceptMethodArgComment(srcClsName, srcName, desc, arg.getIndex(), arg.getLvIndex(), comment);
							}
						}

						vars.clear();
					}

					// method vars

					if (format.supportsLocals) {
						for (MethodVarInstance arg : m.getVars()) {
							if (shouldExport(arg, format, srcType, dstType)) vars.add(arg);
						}

						// TODO: sort vars

						for (MethodVarInstance var : vars) {
							assert var.getMethod() == m;

							String srcVarName = var.getName(srcType);
							String dstVarName = var.getName(dstType);

							if (dstVarName != null && !dstVarName.equals(srcVarName)) {
								writer.acceptMethodVar(srcClsName, srcName, desc,
										var.getIndex(), var.getLvIndex(), var.getStartOpIdx(), var.getAsmIndex(),
										srcVarName, dstVarName);
							}

							if (format.supportsComments) {
								String comment = var.getMappedComment();
								if (comment != null) writer.acceptMethodVarComment(srcClsName, srcName, desc,
										var.getIndex(), var.getLvIndex(), var.getStartOpIdx(), var.getAsmIndex(),
										comment);
							}
						}

						vars.clear();
					}
				}

				methods.clear();

				// fields

				for (FieldInstance f : cls.getFields()) {
					if (shouldExport(f, format, srcType, dstType)) fields.add(f);
				}

				fields.sort(MemberInstance.nameComparator);

				for (FieldInstance f : fields) {
					assert f.getCls() == cls;

					String srcName = f.getName(srcType);
					String desc = getDesc(f, srcType);
					String dstName = f.getName(dstType);

					if (dstName != null && dstName.equals(srcName)) {
						dstName = null;
					}

					writer.acceptField(srcClsName, srcName, desc, dstClsName, dstName, getDesc(f, dstType));

					if (format.supportsComments) {
						String comment = f.getMappedComment();
						if (comment != null) writer.acceptFieldComment(srcClsName, srcName, desc, comment);
					}
				}

				fields.clear();
			}
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}

		return true;
	}

	private static boolean shouldExport(MethodInstance method, MappingFormat format, NameType srcType, NameType dstType, MappingsExportVerbosity verbosity, Set<Set<MethodInstance>> exportedHierarchies) {
		String srcName = method.getName(srcType);
		if (srcName == null) return false;

		String dstName;

		return format.supportsComments && method.getMappedComment() != null
				|| format.supportsArgs && (shouldExportAny(method.getArgs(), format, srcType, dstType) || shouldExportAny(method.getVars(), format, srcType, dstType))
				|| (dstName = method.getName(dstType)) != null && !srcName.equals(dstName) && shouldExportName(method, verbosity, exportedHierarchies);
	}

	private static boolean shouldExport(MethodVarInstance var, MappingFormat format, NameType srcType, NameType dstType) {
		String srcName = var.getName(srcType);
		String dstName = var.getName(dstType);

		return dstName != null && !dstName.equals(srcName)
				|| format.supportsComments && var.getMappedComment() != null;
	}

	private static boolean shouldExport(FieldInstance field, MappingFormat format, NameType srcType, NameType dstType) {
		String srcName = field.getName(srcType);
		if (srcName == null) return false;

		String dstName = field.getName(dstType);

		return dstName != null && !srcName.equals(dstName)
				|| format.supportsComments && field.getMappedComment() != null;
	}

	private static boolean shouldExportAny(MethodInstance[] methods, MappingFormat format, NameType srcType, NameType dstType, MappingsExportVerbosity verbosity, Set<Set<MethodInstance>> exportedHierarchies) {
		for (MethodInstance m : methods) {
			if (shouldExport(m, format, srcType, dstType, verbosity, exportedHierarchies)) return true;
		}

		return false;
	}

	private static boolean shouldExportAny(MethodVarInstance[] vars, MappingFormat format, NameType srcType, NameType dstType) {
		for (MethodVarInstance v : vars) {
			if (shouldExport(v, format, srcType, dstType)) return true;
		}

		return false;
	}

	private static boolean shouldExportAny(FieldInstance[] fields, MappingFormat format, NameType srcType, NameType dstType) {
		for (FieldInstance f : fields) {
			if (shouldExport(f, format, srcType, dstType)) return true;
		}

		return false;
	}

	private static boolean shouldExportName(MethodInstance method, MappingsExportVerbosity verbosity, Set<Set<MethodInstance>> exportedHierarchies) {
		return verbosity == MappingsExportVerbosity.FULL
				|| method.getAllHierarchyMembers().size() == 1
				|| method.getParents().isEmpty() && (verbosity == MappingsExportVerbosity.ROOTS || !exportedHierarchies.contains(method.getAllHierarchyMembers()));
	}

	private static String getDesc(MethodInstance member, NameType type) {
		String ret = "(";

		for (MethodVarInstance arg : member.getArgs()) {
			ret += getClsId(arg.getType(), type);
		}

		ret += ")" + getClsId(member.getRetType(), type);

		return ret;
	}

	private static String getDesc(FieldInstance member, NameType type) {
		return getClsId(member.getType(), type);
	}

	private static String getClsId(ClassInstance cls, NameType type) {
		if (cls.isPrimitive()) {
			return cls.getId();
		} else if (cls.isArray()) {
			return cls.getName(type);
		} else {
			return 'L'+cls.getName(type)+';';
		}
	}

	public static void clear(ClassEnv env) {
		for (ClassInstance cls : env.getClasses()) {
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
