package matcher.classifier;

import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import matcher.Matcher;
import matcher.Util;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MethodInstance;

public class ClassClassifier {
	public static void init() {
		addClassifier(classTypeCheck, 20);
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
		addClassifier(methodOutReferences, 5);
		addClassifier(methodInReferences, 6);
		addClassifier(stringConstants, 8);
		addClassifier(numericConstants, 6);
	}

	private static void addClassifier(IClassifier<ClassInstance> classifier, double weight) {
		classifiers.put(classifier, weight);
		maxScore += weight;
	}

	public static double getMaxScore() {
		return maxScore;
	}

	public static List<RankResult<ClassInstance>> rank(ClassInstance srcClass, ClassInstance[] dstClasses, Matcher matcher) {
		return ClassifierUtil.rank(srcClass, dstClasses, classifiers, ClassifierUtil::checkPotentialEquality, matcher);
	}

	private static final Map<IClassifier<ClassInstance>, Double> classifiers = new IdentityHashMap<>();
	private static double maxScore;

	private static AbstractClassifier classTypeCheck = new AbstractClassifier("class type check") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
			int mask = (Opcodes.ACC_ENUM | Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION) | Opcodes.ACC_ABSTRACT;
			int resultA = clsA.getAsmNode().access & mask;
			int resultB = clsB.getAsmNode().access & mask;

			assert Integer.bitCount(resultA) <= 2 && Integer.bitCount(resultB) <= 2;

			return 1 - Integer.bitCount(resultA ^ resultB) / 3.;
		}
	};

	private static AbstractClassifier hierarchyDepth = new AbstractClassifier("hierarchy depth") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
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
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
			return ClassifierUtil.compareCounts(clsA.getSuperClass().getChildClasses().size(), clsB.getSuperClass().getChildClasses().size());
		}
	};

	private static AbstractClassifier parentClass = new AbstractClassifier("parent class") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
			if (clsA.getSuperClass() == null && clsB.getSuperClass() == null) return 1;
			if (clsA.getSuperClass() == null || clsB.getSuperClass() == null) return 0;

			return ClassifierUtil.checkPotentialEquality(clsA.getSuperClass(), clsB.getSuperClass()) ? 1 : 0;
		}
	};

	private static AbstractClassifier childClasses = new AbstractClassifier("child classes") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
			return ClassifierUtil.compareClassSets(clsA.getChildClasses(), clsB.getChildClasses(), true);
		}
	};

	private static AbstractClassifier interfaces = new AbstractClassifier("interfaces") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
			return ClassifierUtil.compareClassSets(clsA.getInterfaces(), clsB.getInterfaces(), true);
		}
	};

	private static AbstractClassifier implementers = new AbstractClassifier("implementers") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
			return ClassifierUtil.compareClassSets(clsA.getImplementers(), clsB.getImplementers(), true);
		}
	};

	private static AbstractClassifier outerClass = new AbstractClassifier("outer class") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
			ClassInstance outerA = clsA.getOuterClass();
			ClassInstance outerB = clsB.getOuterClass();

			if (outerA == null && outerB == null) return 1;
			if (outerA == null || outerB == null) return 0;

			return ClassifierUtil.checkPotentialEquality(outerA, outerB) ? 1 : 0;
		}
	};

	private static AbstractClassifier innerClasses = new AbstractClassifier("inner classes") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
			Set<ClassInstance> innerA = clsA.getInnerClasses();
			Set<ClassInstance> innerB = clsB.getInnerClasses();

			if (innerA.isEmpty() && innerB.isEmpty()) return 1;
			if (innerA.isEmpty() || innerB.isEmpty()) return 0;

			return ClassifierUtil.compareClassSets(innerA, innerB, true);
		}
	};

	private static AbstractClassifier methodCount = new AbstractClassifier("method count") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
			return ClassifierUtil.compareCounts(clsA.getMethods().length, clsB.getMethods().length);
		}
	};

	private static AbstractClassifier fieldCount = new AbstractClassifier("field count") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
			return ClassifierUtil.compareCounts(clsA.getFields().length, clsB.getFields().length);
		}
	};

	private static AbstractClassifier similarMethods = new AbstractClassifier("similar methods") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
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
						if (methodB.getArgs().size() != methodA.getArgs().size()) continue;

						for (int arg = 0; arg < methodA.getArgs().size(); arg++) {
							ClassInstance argA = methodA.getArgs().get(arg);
							ClassInstance argB = methodB.getArgs().get(arg);

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
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
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
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
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
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
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
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
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

	private static AbstractClassifier stringConstants = new AbstractClassifier("string constants") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
			Set<String> stringsA = extractStrings(clsA);
			Set<String> stringsB = extractStrings(clsB);

			return ClassifierUtil.compareSets(stringsA, stringsB, false);
		}
	};

	private static Set<String> extractStrings(ClassInstance cls) {
		Set<String> ret = new HashSet<>();

		for (MethodInstance method : cls.getMethods()) {
			MethodNode asmNode = method.getAsmNode();
			if (asmNode == null) continue;

			ClassifierUtil.extractStrings(asmNode, ret);
		}

		for (FieldInstance field : cls.getFields()) {
			FieldNode asmNode = field.getAsmNode();
			if (asmNode == null) continue;

			if (asmNode.value instanceof String) {
				ret.add((String) asmNode.value);
			}
		}

		return ret;
	}

	private static AbstractClassifier numericConstants = new AbstractClassifier("numeric constants") {
		@Override
		public double getScore(ClassInstance clsA, ClassInstance clsB, Matcher matcher) {
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

	static abstract class AbstractClassifier implements IClassifier<ClassInstance> {
		AbstractClassifier(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public double getWeight() {
			return classifiers.get(this);
		}

		private final String name;
	}
}
