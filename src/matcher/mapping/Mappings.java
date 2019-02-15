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
						System.out.println("can't find mapped method "+srcClsName+"/"+srcName+" ("+(cls.hasMappedName() ? cls.getName(NameType.MAPPED_PLAIN)+"/" : "")+dstName+")");
					} else {
						if (!method.hasMappedName() || replace) {
							for (MethodInstance m : method.getAllHierarchyMembers()) {
								m.setMappedName(dstName);
							}
						}

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

						if (!arg.hasMappedName() || replace) arg.setMappedName(dstArgName);
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

						if (!arg.hasMappedName() || replace) arg.setMappedName(dstVarName);
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
						System.out.println("can't find mapped field "+srcClsName+"/"+srcName+" ("+(cls.hasMappedName() ? cls.getName(NameType.MAPPED_PLAIN)+"/" : "")+dstName+")");
					} else {
						if (!field.hasMappedName() || replace) {
							for (FieldInstance f : field.getAllHierarchyMembers()) {
								f.setMappedName(dstName);
							}
						}

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

				writer.acceptClass(srcClsName, dstClsName);

				// comment

				if (cls.getMappedComment() != null) writer.acceptClassComment(srcClsName, cls.getMappedComment());

				// methods

				for (MethodInstance m : cls.getMethods()) {
					if (shouldExport(m, format, srcType, dstType, verbosity, exportedHierarchies)) methods.add(m);
				}

				methods.sort(MemberInstance.nameComparator);

				for (MethodInstance m : methods) {
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

					if (format.supportsArgs) {
						for (MethodVarInstance arg : m.getArgs()) {
							String dstVarName = arg.getName(dstType);

							if (dstVarName != null && !dstVarName.equals(arg.getName(srcType))) {
								writer.acceptMethodArg(srcClsName, srcName, desc, arg.getIndex(), arg.getLvIndex(), dstVarName);
							}
						}
					}

					if (format.supportsLocals) {
						for (MethodVarInstance var : m.getVars()) {
							String dstVarName = var.getName(dstType);

							if (dstVarName != null && !dstVarName.equals(var.getName(srcType))) {
								writer.acceptMethodVar(srcClsName, srcName, desc, var.getIndex(), var.getLvIndex(), dstVarName);
							}
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
		String dstName;

		return srcName != null
				&& (format.supportsComments && method.getMappedComment() != null
				|| format.supportsArgs && (shouldExportAny(method.getArgs(), format, srcType, dstType) || shouldExportAny(method.getVars(), format, srcType, dstType))
				|| (dstName = method.getName(dstType)) != null && !srcName.equals(dstName) && shouldExportName(method, verbosity, exportedHierarchies));
	}

	private static boolean shouldExport(MethodVarInstance var, MappingFormat format, NameType srcType, NameType dstType) {
		String srcName = var.getName(srcType);
		String dstName = var.getName(dstType);

		return dstName != null && !dstName.equals(srcName);
	}

	private static boolean shouldExport(FieldInstance field, MappingFormat format, NameType srcType, NameType dstType) {
		String srcName = field.getName(srcType);
		String dstName;

		return srcName != null
				&& ((dstName = field.getName(dstType)) != null && !srcName.equals(dstName)
				|| format.supportsComments && field.getMappedComment() != null);
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
				}
			}

			for (FieldInstance field : cls.getFields()) {
				field.setMappedName(null);
				field.setMappedComment(null);
			}
		}
	}
}
