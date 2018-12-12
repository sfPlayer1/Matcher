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
import matcher.type.IMatchable;
import matcher.type.LocalClassEnv;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class Mappings {
	public static void load(Path path, MappingFormat format, LocalClassEnv env, final boolean replace) throws IOException {
		int[] counts = new int[7];
		Set<String> warnedClasses = new HashSet<>();

		try {
			MappingReader.read(path, format, new IMappingAcceptor() {
				@Override
				public void acceptClass(String srcName, String dstName) {
					ClassInstance cls = env.getLocalClsByName(srcName);

					if (cls == null) {
						if (warnedClasses.add(srcName)) System.out.println("can't find mapped class "+srcName+" ("+dstName+")");
					} else {
						if (!cls.hasMappedName() || replace) cls.setMappedName(dstName);
						counts[0]++;
					}
				}

				@Override
				public void acceptClassComment(String srcName, String comment) {
					ClassInstance cls = env.getLocalClsByName(srcName);

					if (cls == null) {
						if (warnedClasses.add(srcName)) System.out.println("can't find mapped class "+srcName);
					} else {
						if (cls.getMappedComment() == null || replace) cls.setMappedComment(comment);
						counts[1]++;
					}
				}

				@Override
				public void acceptMethod(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) {
					ClassInstance cls = env.getLocalClsByName(srcClsName);
					MethodInstance method;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((method = cls.getMethod(srcName, srcDesc)) == null || !method.isReal()) {
						String mappedName = cls.getMappedName();
						System.out.println("can't find mapped method "+srcClsName+"/"+srcName+" ("+(mappedName != null ? mappedName+"/" : "")+dstName+")");
					} else {
						if (!method.hasMappedName() || replace) method.setMappedName(dstName);
						counts[2]++;
					}
				}

				@Override
				public void acceptMethodComment(String srcClsName, String srcName, String srcDesc, String comment) {
					ClassInstance cls = env.getLocalClsByName(srcClsName);
					MethodInstance method;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((method = cls.getMethod(srcName, srcDesc)) == null || !method.isReal()) {
						System.out.println("can't find mapped method "+srcClsName+"/"+srcName);
					} else {
						if (method.getMappedComment() == null || replace) method.setMappedComment(comment);
						counts[3]++;
					}
				}

				@Override
				public void acceptMethodArg(String srcClsName, String srcName, String srcDesc, int argIndex, int lvIndex, String dstArgName) {
					ClassInstance cls = env.getLocalClsByName(srcClsName);
					MethodInstance method;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((method = cls.getMethod(srcName, srcDesc)) == null || !method.isReal()) {
						System.out.println("can't find mapped method "+srcClsName+"/"+srcName);
					} else if (argIndex < -1 || argIndex >= method.getArgs().length) {
						System.out.println("invalid arg index "+argIndex+" for method "+method);
					} else if (lvIndex < -1 || lvIndex >= method.getArgs().length * 2 + 1) {
						System.out.println("invalid lvt index "+lvIndex+" for method "+method);
					} else {
						if (argIndex == -1) {
							if (lvIndex <= -1) {
								System.out.println("missing arg+lvt index "+lvIndex+" for method "+method);
								return;
							} else {
								argIndex = findVarIndex(method.getArgs(), lvIndex);

								if (argIndex == -1) {
									System.out.println("invalid lvt index "+lvIndex+" for method "+method);
									return;
								}
							}
						}

						MethodVarInstance arg = method.getArg(argIndex);

						if (lvIndex != -1 && arg.getLvIndex() != lvIndex) {
							System.out.println("mismatched lvt index "+lvIndex+" for method "+method);
							return;
						}

						if (arg.getMappedName() == null || replace) arg.setMappedName(dstArgName);
						counts[4]++;
					}
				}

				@Override
				public void acceptMethodVar(String srcClsName, String srcName, String srcDesc, int varIndex, int lvIndex, String dstVarName) {
					ClassInstance cls = env.getLocalClsByName(srcClsName);
					MethodInstance method;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((method = cls.getMethod(srcName, srcDesc)) == null || !method.isReal()) {
						System.out.println("can't find mapped method "+srcClsName+"/"+srcName);
					} else if (varIndex < -1 || varIndex >= method.getVars().length) {
						System.out.println("invalid var index "+varIndex+" for method "+method);
					} else if (lvIndex < -1 || lvIndex >= method.getVars().length * 2 + 1) {
						System.out.println("invalid lvt index "+lvIndex+" for method "+method);
					} else {
						if (varIndex == -1) {
							if (lvIndex <= -1) {
								System.out.println("missing arg+lvt index "+lvIndex+" for method "+method);
								return;
							} else {
								varIndex = findVarIndex(method.getVars(), lvIndex);

								if (varIndex == -1) {
									System.out.println("invalid lvt index "+lvIndex+" for method "+method);
									return;
								}
							}
						}

						MethodVarInstance arg = method.getVar(varIndex);

						if (lvIndex != -1 && arg.getLvIndex() != lvIndex) {
							System.out.println("mismatched lvt index "+lvIndex+" for method "+method);
							return;
						}

						if (arg.getMappedName() == null || replace) arg.setMappedName(dstVarName);
						counts[4]++;
					}
				}

				@Override
				public void acceptField(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) {
					ClassInstance cls = env.getLocalClsByName(srcClsName);
					FieldInstance field;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((field = cls.getField(srcName, srcDesc)) == null || !field.isReal()) {
						String mappedName = cls.getMappedName();
						System.out.println("can't find mapped field "+srcClsName+"/"+srcName+" ("+(mappedName != null ? mappedName+"/" : "")+dstName+")");
					} else {
						if (!field.hasMappedName() || replace) field.setMappedName(dstName);
						counts[5]++;
					}
				}

				@Override
				public void acceptFieldComment(String srcClsName, String srcName, String srcDesc, String comment) {
					ClassInstance cls = env.getLocalClsByName(srcClsName);
					FieldInstance field;

					if (cls == null) {
						if (warnedClasses.add(srcClsName)) System.out.println("can't find mapped class "+srcClsName);
					} else if ((field = cls.getField(srcName, srcDesc)) == null || !field.isReal()) {
						System.out.println("can't find mapped field "+srcClsName+"/"+srcName);
					} else {
						if (field.getMappedComment() == null || replace) field.setMappedComment(comment);
						counts[6]++;
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

	private static int findVarIndex(MethodVarInstance[] vars, int lvIndex) {
		for (MethodVarInstance arg : vars) {
			if (arg.getLvIndex() == lvIndex) return arg.getIndex();
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
		Set<Set<MethodInstance>> exportedHierarchies = verbosity == MappingsExportVerbosity.MINIMAL ? Util.newIdentityHashSet() : null;

		try (MappingWriter writer = new MappingWriter(file, format, srcType, dstType)) {
			for (ClassInstance cls : classes) {
				String srcClsName = getName(cls, srcType);
				if (srcClsName == null) continue;

				String dstClsName = getName(cls, dstType);

				if ((dstClsName == null || dstClsName.equals(srcClsName))
						&& (!format.supportsComments || cls.getMappedComment() == null)
						&& !shouldExportAny(cls.getMethods(), format, srcType, dstType, verbosity, exportedHierarchies)
						&& !shouldExportAny(cls.getFields(), format, srcType, dstType)) {
					continue; // no data for the class, skip
				}

				writer.acceptClass(srcClsName, dstClsName);

				// comment

				if (cls.getMappedComment() != null) writer.acceptClassComment(srcClsName, cls.getMappedComment());

				// methods

				for (MethodInstance m : cls.getMethods()) {
					if (shouldExport(m, format, srcType, dstType, verbosity, exportedHierarchies)) methods.add(m);
				}

				methods.sort(MemberInstance.nameComparator);

				for (MethodInstance m : methods) {
					String srcName = getName(m, srcType);
					String desc = getDesc(m, srcType);
					String dstName = getName(m, dstType);

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

					if (format.supportsArgs) {
						for (MethodVarInstance arg : m.getArgs()) {
							String argName = arg.getMappedName();
							if (argName != null) writer.acceptMethodArg(srcClsName, srcName, desc, arg.getIndex(), arg.getLvIndex(), argName);
						}
					}

					if (format.supportsLocals) {
						for (MethodVarInstance var : m.getVars()) {
							String varName = var.getMappedName();
							if (varName != null) writer.acceptMethodVar(srcClsName, srcName, desc, var.getIndex(), var.getLvIndex(), varName);
						}
					}
				}

				methods.clear();

				// fields

				for (FieldInstance f : cls.getFields()) {
					if (shouldExport(f, format, srcType, dstType)) fields.add(f);
				}

				fields.sort(MemberInstance.nameComparator);

				for (FieldInstance f : fields) {
					String srcName = getName(f, srcType);
					String desc = getDesc(f, srcType);
					String dstName = getName(f, dstType);
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

	private static String getName(IMatchable<?> m, NameType type) {
		switch (type) {
		case PLAIN: return m.getName();
		case MAPPED: return m.isNameObfuscated() ? m.getMappedName() : m.getName();
		case TMP: return m.isNameObfuscated() ? m.getTmpName(true) : m.getName();
		case UID: return m.isNameObfuscated() ? m.getUidString() : m.getName();
		default: throw new IllegalArgumentException();
		}
	}

	private static boolean shouldExport(MethodInstance method, MappingFormat format, NameType srcType, NameType dstType, MappingsExportVerbosity verbosity, Set<Set<MethodInstance>> exportedHierarchies) {
		String srcName = getName(method, srcType);
		String dstName;

		return getName(method, srcType) != null
				&& (format.supportsComments && method.getMappedComment() != null
				|| format.supportsArgs && method.hasMappedArg()
				|| (dstName = getName(method, dstType)) != null && !srcName.equals(dstName) && shouldExportName(method, verbosity, exportedHierarchies));
	}

	private static boolean shouldExport(FieldInstance field, MappingFormat format, NameType srcType, NameType dstType) {
		String srcName = getName(field, srcType);
		String dstName;

		return srcName != null
				&& ((dstName = getName(field, dstType)) != null && !srcName.equals(dstName)
				|| format.supportsComments && field.getMappedComment() != null);
	}

	private static boolean shouldExportAny(MethodInstance[] methods, MappingFormat format, NameType srcType, NameType dstType, MappingsExportVerbosity verbosity, Set<Set<MethodInstance>> exportedHierarchies) {
		for (MethodInstance m : methods) {
			if (shouldExport(m, format, srcType, dstType, verbosity, exportedHierarchies)) return true;
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
			return getName(cls, type);
		} else {
			return 'L'+getName(cls, type)+';';
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
				}
			}

			for (FieldInstance field : cls.getFields()) {
				field.setMappedName(null);
				field.setMappedComment(null);
			}
		}
	}
}
