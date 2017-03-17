package matcher.classifier;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import matcher.Matcher;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;

import org.objectweb.asm.Opcodes;

public class FieldClassifier {
	public static void init() {
		addClassifier(fieldTypeCheck, 10);
		addClassifier(accessFlags, 4);
		addClassifier(type, 10);
		addClassifier(readReferences, 6);
		addClassifier(writeReferences, 6);
		addClassifier(position, 3);
	}

	private static void addClassifier(IClassifier<FieldInstance> classifier, double weight) {
		classifiers.put(classifier, weight);
		maxScore += weight;
	}

	public static double getMaxScore() {
		return maxScore;
	}

	public static List<RankResult<FieldInstance>> rank(FieldInstance src, FieldInstance[] dsts, Matcher matcher) {
		return ClassifierUtil.rank(src, dsts, classifiers, ClassifierUtil::checkPotentialEquality, matcher);
	}

	private static final Map<IClassifier<FieldInstance>, Double> classifiers = new IdentityHashMap<>();
	private static double maxScore;

	private static AbstractClassifier fieldTypeCheck = new AbstractClassifier("field type check") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, Matcher matcher) {
			if (!checkAsmNodes(fieldA, fieldB)) return compareAsmNodes(fieldA, fieldB);

			int mask = Opcodes.ACC_STATIC;
			int resultA = fieldA.getAsmNode().access & mask;
			int resultB = fieldB.getAsmNode().access & mask;

			return 1 - Integer.bitCount(resultA ^ resultB);
		}
	};

	private static AbstractClassifier accessFlags = new AbstractClassifier("access flags") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, Matcher matcher) {
			if (!checkAsmNodes(fieldA, fieldB)) return compareAsmNodes(fieldA, fieldB);

			int mask = (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE) | Opcodes.ACC_FINAL | Opcodes.ACC_VOLATILE | Opcodes.ACC_TRANSIENT | Opcodes.ACC_SYNTHETIC;
			int resultA = fieldA.getAsmNode().access & mask;
			int resultB = fieldB.getAsmNode().access & mask;

			return 1 - Integer.bitCount(resultA ^ resultB) / 6.;
		}
	};

	private static AbstractClassifier type = new AbstractClassifier("types") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, Matcher matcher) {
			return ClassifierUtil.checkPotentialEquality(fieldA.getType(), fieldB.getType()) ? 1 : 0;
		}
	};

	private static AbstractClassifier readReferences = new AbstractClassifier("read references") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, Matcher matcher) {
			return ClassifierUtil.compareMethodSets(fieldA.getReadRefs(), fieldB.getReadRefs(), true);
		}
	};

	private static AbstractClassifier writeReferences = new AbstractClassifier("write references") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, Matcher matcher) {
			return ClassifierUtil.compareMethodSets(fieldA.getWriteRefs(), fieldB.getWriteRefs(), true);
		}
	};

	private static AbstractClassifier position = new AbstractClassifier("position") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, Matcher matcher) {
			/*if (fieldA.position == fieldB.position) return 1;

			double relPosA = ClassifierUtil.getRelativePosition(fieldA.position, fieldA.cls.fields.size());
			double relPosB = ClassifierUtil.getRelativePosition(fieldB.position, fieldB.cls.fields.size());

			return 1 - Math.abs(relPosA - relPosB);*/
			return ClassifierUtil.classifyPosition(fieldA, fieldB, (cls, idx) -> cls.getField(idx), ClassInstance::getFields);
		}
	};

	private static boolean checkAsmNodes(FieldInstance a, FieldInstance b) {
		return a.getAsmNode() != null && b.getAsmNode() != null;
	}

	private static double compareAsmNodes(FieldInstance a, FieldInstance b) {
		return a.getAsmNode() == null && b.getAsmNode() == null ? 1 : 0;
	}

	private static abstract class AbstractClassifier implements IClassifier<FieldInstance> {
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
