package matcher.classifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import matcher.Matcher;
import matcher.Util;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;
import matcher.type.Signature.ClassSignature;

public class ClassClassifier {
	public static void init() {
		addClassifier(classTypeCheck, 20);
		addClassifier(signature, 5);
		addClassifier(hierarchyDepth, 1);
		addClassifier(parentClass, 4);
		addClassifier(childClasses, 3);
		addClassifier(interfaces, 3);
		addClassifier(implementers, 2);
		addClassifier(outerClass, 6);
		addClassifier(innerClasses, 5);
		addClassifier(methodCount, 3);
		addClassifier(fieldCount, 3);
		addClassifier(hierarchySiblings, 2);
		addClassifier(similarMethods, 10);
		addClassifier(outReferences, 6);
		addClassifier(inReferences, 6);
		addClassifier(stringConstants, 8);
		addClassifier(numericConstants, 6);
		addClassifier(methodOutReferences, 5, ClassifierLevel.Intermediate, ClassifierLevel.Full, ClassifierLevel.Extra);
		addClassifier(methodInReferences, 6, ClassifierLevel.Intermediate, ClassifierLevel.Full, ClassifierLevel.Extra);
		addClassifier(fieldReadReferences, 5, ClassifierLevel.Intermediate, ClassifierLevel.Full, ClassifierLevel.Extra);
		addClassifier(fieldWriteReferences, 5, ClassifierLevel.Intermediate, ClassifierLevel.Full, ClassifierLevel.Extra);
		addClassifier(membersFull, 10, ClassifierLevel.Full, ClassifierLevel.Extra);
		addClassifier(inRefsBci, 6, ClassifierLevel.Extra);
	}

	public static void addClassifier(AbstractClassifier classifier, double weight, ClassifierLevel... levels) {
		if (levels.length == 0) levels = ClassifierLevel.ALL;

		classifier.weight = weight;

		for (ClassifierLevel level : levels) {
			classifiers.computeIfAbsent(level, ignore -> new ArrayList<>()).add(classifier);
			maxScore.put(level, getMaxScore(level) + weight);
		}
	}

	public static double getMaxScore(ClassifierLevel level) {
		return maxScore.getOrDefault(level, 0.);
	}

	public static List<RankResult<ClassInstance>> rank(ClassInstance src, ClassInstance[] dsts, ClassifierLevel level, ClassEnvironment env, double maxMismatch) {
		return ClassifierUtil.rank(src, dsts, classifiers.getOrDefault(level, Collections.emptyList()), ClassifierUtil::checkPotentialEquality, env, maxMismatch);
	}

	public static List<RankResult<ClassInstance>> rankParallel(ClassInstance src, ClassInstance[] dsts, ClassifierLevel level, ClassEnvironment env, double maxMismatch) {
		return ClassifierUtil.rankParallel(src, dsts, classifiers.getOrDefault(level, Collections.emptyList()), ClassifierUtil::checkPotentialEquality, env, maxMismatch);
	}

	private static final Map<ClassifierLevel, List<IClassifier<ClassInstance>>> classifiers = new EnumMap<>(ClassifierLevel.class);
	private static final Map<ClassifierLevel, Double> maxScore = new EnumMap<>(ClassifierLevel.class);

	private static AbstractClassifier classTypeCheck = new AbstractClassifier("class type check") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			int mask = Opcodes.ACC_ENUM | Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION | Opcodes.ACC_ABSTRACT;
			int resultA = clsA.getAccess() & mask;
			int resultB = clsB.getAccess() & mask;

			//assert Integer.bitCount(resultA) <= 3 && Integer.bitCount(resultB) <= 3;

			return 1 - Integer.bitCount(resultA ^ resultB) / 4.;
		}
	};

	private static AbstractClassifier signature = new AbstractClassifier("signature") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			ClassSignature sigA = clsA.getSignature();
			ClassSignature sigB = clsB.getSignature();

			if (sigA == null && sigB == null) return 1;
			if (sigA == null || sigB == null) return 0;

			return sigA.isPotentiallyEqual(sigB) ? 1 : 0;
		}
	};

	private static AbstractClassifier hierarchyDepth = new AbstractClassifier("hierarchy depth") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			int countA = 0;
			int countB = 0;

			while (clsA.getSuperClass() != null) {
				clsA = clsA.getSuperClass();
				countA++;
			}

			while (clsB.getSuperClass() != null) {
				clsB = clsB.getSuperClass();
				countB++;
			}

			return ClassifierUtil.compareCounts(countA, countB);
		}
	};


	private static AbstractClassifier hierarchySiblings = new AbstractClassifier("hierarchy siblings") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			return ClassifierUtil.compareCounts(clsA.getSuperClass().getChildClasses().size(), clsB.getSuperClass().getChildClasses().size());
		}
	};

	private static AbstractClassifier parentClass = new AbstractClassifier("parent class") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			if (clsA.getSuperClass() == null && clsB.getSuperClass() == null) return 1;
			if (clsA.getSuperClass() == null || clsB.getSuperClass() == null) return 0;

			return ClassifierUtil.checkPotentialEquality(clsA.getSuperClass(), clsB.getSuperClass()) ? 1 : 0;
		}
	};

	private static AbstractClassifier childClasses = new AbstractClassifier("child classes") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			return ClassifierUtil.compareClassSets(clsA.getChildClasses(), clsB.getChildClasses(), true);
		}
	};

	private static AbstractClassifier interfaces = new AbstractClassifier("interfaces") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			return ClassifierUtil.compareClassSets(clsA.getInterfaces(), clsB.getInterfaces(), true);
		}
	};

	private static AbstractClassifier implementers = new AbstractClassifier("implementers") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			return ClassifierUtil.compareClassSets(clsA.getImplementers(), clsB.getImplementers(), true);
		}
	};

	private static AbstractClassifier outerClass = new AbstractClassifier("outer class") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			ClassInstance outerA = clsA.getOuterClass();
			ClassInstance outerB = clsB.getOuterClass();

			if (outerA == null && outerB == null) return 1;
			if (outerA == null || outerB == null) return 0;

			return ClassifierUtil.checkPotentialEquality(outerA, outerB) ? 1 : 0;
		}
	};

	private static AbstractClassifier innerClasses = new AbstractClassifier("inner classes") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			Set<ClassInstance> innerA = clsA.getInnerClasses();
			Set<ClassInstance> innerB = clsB.getInnerClasses();

			if (innerA.isEmpty() && innerB.isEmpty()) return 1;
			if (innerA.isEmpty() || innerB.isEmpty()) return 0;

			return ClassifierUtil.compareClassSets(innerA, innerB, true);
		}
	};

	private static AbstractClassifier methodCount = new AbstractClassifier("method count") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			return ClassifierUtil.compareCounts(clsA.getMethods().length, clsB.getMethods().length);
		}
	};

	private static AbstractClassifier fieldCount = new AbstractClassifier("field count") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			return ClassifierUtil.compareCounts(clsA.getFields().length, clsB.getFields().length);
		}
	};

	private static AbstractClassifier similarMethods = new AbstractClassifier("similar methods") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			if (clsA.getMethods().length == 0 && clsB.getMethods().length == 0) return 1;
			if (clsA.getMethods().length == 0 || clsB.getMethods().length == 0) return 0;

			Set<MethodInstance> methodsB = Util.newIdentityHashSet(Arrays.asList(clsB.getMethods()));
			double totalScore = 0;
			MethodInstance bestMatch = null;
			double bestScore = 0;

			for (MethodInstance methodA : clsA.getMethods()) {
				{
					mBLoop: for (MethodInstance methodB : methodsB) {
						if (!ClassifierUtil.checkPotentialEquality(methodA, methodB)) continue;
						if (!ClassifierUtil.checkPotentialEquality(methodA.getRetType(), methodB.getRetType())) continue;

						MethodVarInstance[] argsA = methodA.getArgs();
						MethodVarInstance[] argsB = methodB.getArgs();
						if (argsA.length != argsB.length) continue;

						for (int i = 0; i < argsA.length; i++) {
							ClassInstance argA = argsA[i].getType();
							ClassInstance argB = argsB[i].getType();

							if (!ClassifierUtil.checkPotentialEquality(argA, argB)) {
								continue mBLoop;
							}
						}

						MethodNode asmNodeA = methodA.getAsmNode();
						MethodNode asmNodeB = methodA.getAsmNode();
						double score;

						if (asmNodeA == null || asmNodeB == null) {
							score = asmNodeA == null && asmNodeB == null ? 1 : 0;
						} else {
							score = ClassifierUtil.compareCounts(asmNodeA.instructions.size(), asmNodeB.instructions.size());
						}

						if (score > bestScore) {
							bestScore = score;
							bestMatch = methodB;
						}
					}
				}

				if (bestMatch != null) {
					totalScore += bestScore;
					methodsB.remove(bestMatch);
				}
			}

			return totalScore / Math.max(clsA.getMethods().length, clsB.getMethods().length);
		}
	};

	private static AbstractClassifier outReferences = new AbstractClassifier("out references") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			Set<ClassInstance> refsA = getOutRefs(clsA);
			Set<ClassInstance> refsB = getOutRefs(clsB);

			return ClassifierUtil.compareClassSets(refsA, refsB, false);
		}
	};

	private static Set<ClassInstance> getOutRefs(ClassInstance cls) {
		Set<ClassInstance> ret = Util.newIdentityHashSet();

		for (MethodInstance method : cls.getMethods()) {
			ret.addAll(method.getClassRefs());
		}

		for (FieldInstance field : cls.getFields()) {
			ret.add(field.getType());
		}

		return ret;
	}

	private static AbstractClassifier inReferences = new AbstractClassifier("in references") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			Set<ClassInstance> refsA = getInRefs(clsA);
			Set<ClassInstance> refsB = getInRefs(clsB);

			return ClassifierUtil.compareClassSets(refsA, refsB, false);
		}
	};

	private static Set<ClassInstance> getInRefs(ClassInstance cls) {
		Set<ClassInstance> ret = Util.newIdentityHashSet();

		for (MethodInstance method : cls.getMethodTypeRefs()) {
			ret.add(method.getCls());
		}

		for (FieldInstance field : cls.getFieldTypeRefs()) {
			ret.add(field.getCls());
		}

		return ret;
	}

	private static AbstractClassifier methodOutReferences = new AbstractClassifier("method out references") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			Set<MethodInstance> refsA = getMethodOutRefs(clsA);
			Set<MethodInstance> refsB = getMethodOutRefs(clsB);

			return ClassifierUtil.compareMethodSets(refsA, refsB, false);
		}
	};

	private static Set<MethodInstance> getMethodOutRefs(ClassInstance cls) {
		Set<MethodInstance> ret = Util.newIdentityHashSet();

		for (MethodInstance method : cls.getMethods()) {
			ret.addAll(method.getRefsOut());
		}

		return ret;
	}

	private static AbstractClassifier methodInReferences = new AbstractClassifier("method in references") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			Set<MethodInstance> refsA = getMethodInRefs(clsA);
			Set<MethodInstance> refsB = getMethodInRefs(clsB);

			return ClassifierUtil.compareMethodSets(refsA, refsB, false);
		}
	};

	private static Set<MethodInstance> getMethodInRefs(ClassInstance cls) {
		Set<MethodInstance> ret = Util.newIdentityHashSet();

		for (MethodInstance method : cls.getMethods()) {
			ret.addAll(method.getRefsIn());
		}

		return ret;
	}

	private static AbstractClassifier fieldReadReferences = new AbstractClassifier("field read references") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			Set<FieldInstance> refsA = getFieldReadRefs(clsA);
			Set<FieldInstance> refsB = getFieldReadRefs(clsB);

			return ClassifierUtil.compareFieldSets(refsA, refsB, false);
		}
	};

	private static Set<FieldInstance> getFieldReadRefs(ClassInstance cls) {
		Set<FieldInstance> ret = Util.newIdentityHashSet();

		for (MethodInstance method : cls.getMethods()) {
			ret.addAll(method.getFieldReadRefs());
		}

		return ret;
	}

	private static AbstractClassifier fieldWriteReferences = new AbstractClassifier("field write references") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			Set<FieldInstance> refsA = getFieldWriteRefs(clsA);
			Set<FieldInstance> refsB = getFieldWriteRefs(clsB);

			return ClassifierUtil.compareFieldSets(refsA, refsB, false);
		}
	};

	private static Set<FieldInstance> getFieldWriteRefs(ClassInstance cls) {
		Set<FieldInstance> ret = Util.newIdentityHashSet();

		for (MethodInstance method : cls.getMethods()) {
			ret.addAll(method.getFieldWriteRefs());
		}

		return ret;
	}

	private static AbstractClassifier stringConstants = new AbstractClassifier("string constants") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			return ClassifierUtil.compareSets(clsA.getStrings(), clsB.getStrings(), true);
		}
	};

	private static AbstractClassifier numericConstants = new AbstractClassifier("numeric constants") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			Set<Integer> intsA = new HashSet<>();
			Set<Integer> intsB = new HashSet<>();
			Set<Long> longsA = new HashSet<>();
			Set<Long> longsB = new HashSet<>();
			Set<Float> floatsA = new HashSet<>();
			Set<Float> floatsB = new HashSet<>();
			Set<Double> doublesA = new HashSet<>();
			Set<Double> doublesB = new HashSet<>();

			extractNumbers(clsA, intsA, longsA, floatsA, doublesA);
			extractNumbers(clsB, intsB, longsB, floatsB, doublesB);

			return (ClassifierUtil.compareSets(intsA, intsB, false)
					+ ClassifierUtil.compareSets(longsA, longsB, false)
					+ ClassifierUtil.compareSets(floatsA, floatsB, false)
					+ ClassifierUtil.compareSets(doublesA, doublesB, false)) / 4;
		}
	};

	private static AbstractClassifier membersFull = new AbstractClassifier("members full") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			/*if (clsA.getName().equals("agl") && clsB.getName().equals("aht")) {
				System.out.println();
			}*/

			final double absThreshold = 0.8;
			final double relThreshold = 0.08;
			final ClassifierLevel level = ClassifierLevel.Full;
			double match = 0;

			if (clsA.getMethods().length > 0 && clsB.getMethods().length > 0) {
				double maxScore = MethodClassifier.getMaxScore(level);

				for (MethodInstance method : clsA.getMethods()) {
					if (!method.isMatchable()) continue;

					List<RankResult<MethodInstance>> ranking = MethodClassifier.rank(method, clsB.getMethods(), level, env);
					if (Matcher.checkRank(ranking, absThreshold, relThreshold, maxScore)) match += Matcher.getScore(ranking.get(0).getScore(), maxScore);
				}
			}

			if (clsA.getFields().length > 0 && clsB.getFields().length > 0) {
				double maxScore = FieldClassifier.getMaxScore(level);

				for (FieldInstance field : clsA.getFields()) {
					if (!field.isMatchable()) continue;

					List<RankResult<FieldInstance>> ranking = FieldClassifier.rank(field, clsB.getFields(), level, env);
					if (Matcher.checkRank(ranking, absThreshold, relThreshold, maxScore)) match += Matcher.getScore(ranking.get(0).getScore(), maxScore);
				}
			}

			int methods = Math.max(clsA.getMethods().length, clsB.getMethods().length);
			int fields = Math.max(clsA.getFields().length, clsB.getFields().length);

			if (methods == 0 && fields == 0) {
				return 1;
			} else {
				assert match <= methods + fields;

				return match / (methods + fields);
			}
		}
	};

	private static AbstractClassifier inRefsBci = new AbstractClassifier("in refs (bci)") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, ClassEnvironment env) {
			int matched = 0;
			int mismatched = 0;

			for (MethodInstance src : clsA.getMethodTypeRefs()) {
				if (src.getCls() == clsA) continue;

				MethodInstance dst = src.getMatch();

				if (dst == null || !clsB.getMethodTypeRefs().contains(dst)) {
					mismatched++;
					continue;
				}

				int[] map = ClassifierUtil.mapInsns(src, dst);
				if (map == null) continue;

				InsnList ilA = src.getAsmNode().instructions;
				InsnList ilB = dst.getAsmNode().instructions;

				for (int srcIdx = 0; srcIdx < map.length; srcIdx++) {
					if (map[srcIdx] < 0) continue;

					AbstractInsnNode in = ilA.get(srcIdx);
					if (in.getType() != AbstractInsnNode.METHOD_INSN) continue;

					MethodInsnNode min = (MethodInsnNode) in;
					ClassInstance owner = env.getClsByNameA(min.owner);

					if (owner != clsA) continue;

					in = ilB.get(map[srcIdx]);
					min = (MethodInsnNode) in;
					owner = env.getClsByNameB(min.owner);

					if (owner != clsB) {
						mismatched++;
					} else {
						matched++;
					}
				}
			}

			if (matched == 0 && mismatched == 0) {
				return 1;
			} else {
				return (double) matched / (matched + mismatched);
			}
		}
	};

	private static void extractNumbers(ClassInstance cls, Set<Integer> ints, Set<Long> longs, Set<Float> floats, Set<Double> doubles) {
		for (MethodInstance method : cls.getMethods()) {
			MethodNode asmNode = method.getAsmNode();
			if (asmNode == null) continue;

			ClassifierUtil.extractNumbers(asmNode, ints, longs, floats, doubles);
		}

		for (FieldInstance field : cls.getFields()) {
			FieldNode asmNode = field.getAsmNode();
			if (asmNode == null) continue;

			ClassifierUtil.handleNumberValue(asmNode.value, ints, longs, floats, doubles);
		}
	}

	public static abstract class AbstractClassifier implements IClassifier<ClassInstance> {
		public AbstractClassifier(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public double getWeight() {
			return weight;
		}

		private final String name;
		private double weight;
	}
}
