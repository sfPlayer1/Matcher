package matcher.classifier;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import matcher.Matcher;
import matcher.type.ClassInstance;
import matcher.type.MethodInstance;

import org.objectweb.asm.Opcodes;

public class MethodClassifier {
	public static void init() {
		addClassifier(methodTypeCheck, 10);
		addClassifier(accessFlags, 4);
		addClassifier(argTypes, 10);
		addClassifier(retType, 5);
		addClassifier(classRefs, 3);
		addClassifier(stringConstants, 5);
		addClassifier(numericConstants, 5);
		addClassifier(parentMethod, 4);
		addClassifier(childMethods, 3);
		addClassifier(inReferences, 6);
		addClassifier(outReferences, 6);
		addClassifier(fieldReads, 5);
		addClassifier(fieldWrites, 5);
		addClassifier(position, 3);
	}

	private static void addClassifier(IClassifier<MethodInstance> classifier, double weight) {
		classifiers.put(classifier, weight);
		maxScore += weight;
	}

	public static double getMaxScore() {
		return maxScore;
	}

	public static List<RankResult<MethodInstance>> rank(MethodInstance src, MethodInstance[] dsts, Matcher matcher) {
		return ClassifierUtil.rank(src, dsts, classifiers, ClassifierUtil::checkPotentialEquality, matcher);
	}

	private static final Map<IClassifier<MethodInstance>, Double> classifiers = new IdentityHashMap<>();
	private static double maxScore;

	private static AbstractClassifier methodTypeCheck = new AbstractClassifier("method type check") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, Matcher matcher) {
			if (!checkAsmNodes(methodA, methodB)) return compareAsmNodes(methodA, methodB);

			int mask = Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT;
			int resultA = methodA.getAsmNode().access & mask;
			int resultB = methodB.getAsmNode().access & mask;

			return 1 - Integer.bitCount(resultA ^ resultB) / 3.;
		}
	};

	private static AbstractClassifier accessFlags = new AbstractClassifier("access flags") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, Matcher matcher) {
			if (!checkAsmNodes(methodA, methodB)) return compareAsmNodes(methodA, methodB);

			int mask = (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE) | Opcodes.ACC_FINAL | Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_BRIDGE | Opcodes.ACC_VARARGS | Opcodes.ACC_STRICT | Opcodes.ACC_SYNTHETIC;
			int resultA = methodA.getAsmNode().access & mask;
			int resultB = methodB.getAsmNode().access & mask;

			return 1 - Integer.bitCount(resultA ^ resultB) / 8.;
		}
	};

	private static AbstractClassifier argTypes = new AbstractClassifier("arg types") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, Matcher matcher) {
			return ClassifierUtil.compareClassLists(methodA.getArgs(), methodB.getArgs());
		}
	};

	private static AbstractClassifier retType = new AbstractClassifier("ret type") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, Matcher matcher) {
			return ClassifierUtil.checkPotentialEquality(methodA.getRetType(), methodB.getRetType()) ? 1 : 0;
		}
	};

	private static AbstractClassifier classRefs = new AbstractClassifier("class refs") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, Matcher matcher) {
			return ClassifierUtil.compareClassSets(methodA.getClassRefs(), methodB.getClassRefs(), true);
		}
	};

	private static AbstractClassifier stringConstants = new AbstractClassifier("string constants") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, Matcher matcher) {
			if (!checkAsmNodes(methodA, methodB)) return compareAsmNodes(methodA, methodB);

			Set<String> stringsA = new HashSet<>();
			ClassifierUtil.extractStrings(methodA.getAsmNode(), stringsA);
			Set<String> stringsB = new HashSet<>();
			ClassifierUtil.extractStrings(methodB.getAsmNode(), stringsB);

			return ClassifierUtil.compareSets(stringsA, stringsB, false);
		}
	};

	private static AbstractClassifier numericConstants = new AbstractClassifier("numeric constants") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, Matcher matcher) {
			if (!checkAsmNodes(methodA, methodB)) return compareAsmNodes(methodA, methodB);

			Set<Integer> intsA = new HashSet<>();
			Set<Integer> intsB = new HashSet<>();
			Set<Long> longsA = new HashSet<>();
			Set<Long> longsB = new HashSet<>();
			Set<Float> floatsA = new HashSet<>();
			Set<Float> floatsB = new HashSet<>();
			Set<Double> doublesA = new HashSet<>();
			Set<Double> doublesB = new HashSet<>();

			ClassifierUtil.extractNumbers(methodA.getAsmNode(), intsA, longsA, floatsA, doublesA);
			ClassifierUtil.extractNumbers(methodB.getAsmNode(), intsB, longsB, floatsB, doublesB);

			return (ClassifierUtil.compareSets(intsA, intsB, false)
					+ ClassifierUtil.compareSets(longsA, longsB, false)
					+ ClassifierUtil.compareSets(floatsA, floatsB, false)
					+ ClassifierUtil.compareSets(doublesA, doublesB, false)) / 4;
		}
	};

	private static AbstractClassifier parentMethod = new AbstractClassifier("parent method") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, Matcher matcher) {
			MethodInstance parentA = methodA.getParent();
			MethodInstance parentB = methodB.getParent();

			if ((parentA == null) != (parentB == null)) return 0;

			if (parentA == null) {
				return 1;
			} else {
				return ClassifierUtil.checkPotentialEquality(parentA, parentB) ? 1 : 0;
			}
		}
	};

	private static AbstractClassifier childMethods = new AbstractClassifier("child methods") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, Matcher matcher) {
			return ClassifierUtil.compareMethodSets(methodA.getChildren(), methodB.getChildren(), true);
		}
	};

	private static AbstractClassifier outReferences = new AbstractClassifier("out references") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, Matcher matcher) {
			return ClassifierUtil.compareMethodSets(methodA.getRefsOut(), methodB.getRefsOut(), true);
		}
	};

	private static AbstractClassifier inReferences = new AbstractClassifier("in references") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, Matcher matcher) {
			return ClassifierUtil.compareMethodSets(methodA.getRefsIn(), methodB.getRefsIn(), true);
		}
	};

	private static AbstractClassifier fieldReads = new AbstractClassifier("field reads") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, Matcher matcher) {
			return ClassifierUtil.compareFieldSets(methodA.getFieldReadRefs(), methodB.getFieldReadRefs(), true);
		}
	};

	private static AbstractClassifier fieldWrites = new AbstractClassifier("field writes") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, Matcher matcher) {
			return ClassifierUtil.compareFieldSets(methodA.getFieldWriteRefs(), methodB.getFieldWriteRefs(), true);
		}
	};

	private static AbstractClassifier position = new AbstractClassifier("position") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, Matcher matcher) {
			return ClassifierUtil.classifyPosition(methodA, methodB, (cls, idx) -> cls.getMethod(idx), ClassInstance::getMethods);
		}
	};

	private static boolean checkAsmNodes(MethodInstance a, MethodInstance b) {
		return a.getAsmNode() != null && b.getAsmNode() != null;
	}

	private static double compareAsmNodes(MethodInstance a, MethodInstance b) {
		return a.getAsmNode() == null && b.getAsmNode() == null ? 1 : 0;
	}

	private static abstract class AbstractClassifier implements IClassifier<MethodInstance> {
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
