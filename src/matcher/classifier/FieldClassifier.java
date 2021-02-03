package matcher.classifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;

import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import matcher.type.Signature.FieldSignature;

public class FieldClassifier {
	public static void init() {
		addClassifier(fieldTypeCheck, 10);
		addClassifier(accessFlags, 4);
		addClassifier(type, 10);
		addClassifier(signature, 5);
		addClassifier(readReferences, 6);
		addClassifier(writeReferences, 6);
		addClassifier(position, 3);
		addClassifier(initValue, 7);
		addClassifier(initStrings, 8);
		addClassifier(initCode, 10, ClassifierLevel.Intermediate, ClassifierLevel.Full, ClassifierLevel.Extra);
		addClassifier(readRefsBci, 6, ClassifierLevel.Extra);
		addClassifier(writeRefsBci, 6, ClassifierLevel.Extra);
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

	public static List<RankResult<FieldInstance>> rank(FieldInstance src, FieldInstance[] dsts, ClassifierLevel level, ClassEnvironment env) {
		return rank(src, dsts, level, env, Double.POSITIVE_INFINITY);
	}

	public static List<RankResult<FieldInstance>> rank(FieldInstance src, FieldInstance[] dsts, ClassifierLevel level, ClassEnvironment env, double maxMismatch) {
		return ClassifierUtil.rank(src, dsts, classifiers.getOrDefault(level, Collections.emptyList()), ClassifierUtil::checkPotentialEquality, env, maxMismatch);
	}

	private static final Map<ClassifierLevel, List<IClassifier<FieldInstance>>> classifiers = new IdentityHashMap<>();
	private static final Map<ClassifierLevel, Double> maxScore = new EnumMap<>(ClassifierLevel.class);

	private static AbstractClassifier fieldTypeCheck = new AbstractClassifier("field type check") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, ClassEnvironment env) {
			if (!checkAsmNodes(fieldA, fieldB)) return compareAsmNodes(fieldA, fieldB);

			int mask = Opcodes.ACC_STATIC;
			int resultA = fieldA.getAsmNode().access & mask;
			int resultB = fieldB.getAsmNode().access & mask;

			return 1 - Integer.bitCount(resultA ^ resultB);
		}
	};

	private static AbstractClassifier accessFlags = new AbstractClassifier("access flags") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, ClassEnvironment env) {
			if (!checkAsmNodes(fieldA, fieldB)) return compareAsmNodes(fieldA, fieldB);

			int mask = (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE) | Opcodes.ACC_FINAL | Opcodes.ACC_VOLATILE | Opcodes.ACC_TRANSIENT | Opcodes.ACC_SYNTHETIC;
			int resultA = fieldA.getAsmNode().access & mask;
			int resultB = fieldB.getAsmNode().access & mask;

			return 1 - Integer.bitCount(resultA ^ resultB) / 6.;
		}
	};

	private static AbstractClassifier type = new AbstractClassifier("types") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, ClassEnvironment env) {
			return ClassifierUtil.checkPotentialEquality(fieldA.getType(), fieldB.getType()) ? 1 : 0;
		}
	};

	private static AbstractClassifier signature = new AbstractClassifier("signature") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, ClassEnvironment env) {
			FieldSignature sigA = fieldA.getSignature();
			FieldSignature sigB = fieldB.getSignature();

			if (sigA == null && sigB == null) return 1;
			if (sigA == null || sigB == null) return 0;

			return sigA.isPotentiallyEqual(sigB) ? 1 : 0;
		}
	};

	private static AbstractClassifier readReferences = new AbstractClassifier("read references") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, ClassEnvironment env) {
			return ClassifierUtil.compareMethodSets(fieldA.getReadRefs(), fieldB.getReadRefs(), true);
		}
	};

	private static AbstractClassifier writeReferences = new AbstractClassifier("write references") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, ClassEnvironment env) {
			return ClassifierUtil.compareMethodSets(fieldA.getWriteRefs(), fieldB.getWriteRefs(), true);
		}
	};

	private static AbstractClassifier position = new AbstractClassifier("position") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, ClassEnvironment env) {
			/*if (fieldA.position == fieldB.position) return 1;

			double relPosA = ClassifierUtil.getRelativePosition(fieldA.position, fieldA.cls.fields.size());
			double relPosB = ClassifierUtil.getRelativePosition(fieldB.position, fieldB.cls.fields.size());

			return 1 - Math.abs(relPosA - relPosB);*/
			return ClassifierUtil.classifyPosition(fieldA, fieldB, MemberInstance::getPosition, (f, idx) -> f.getCls().getField(idx), f -> f.getCls().getFields());
		}
	};

	private static AbstractClassifier initValue = new AbstractClassifier("init value") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, ClassEnvironment env) {
			if (!checkAsmNodes(fieldA, fieldB)) return compareAsmNodes(fieldA, fieldB);

			Object valA = fieldA.getAsmNode().value;
			Object valB = fieldB.getAsmNode().value;

			if (valA == null && valB == null) return 1;
			if (valA == null || valB == null) return 0;

			return valA.equals(valB) ? 1 : 0;
		}
	};

	private static AbstractClassifier initStrings = new AbstractClassifier("init strings") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, ClassEnvironment env) {
			List<AbstractInsnNode> initA = fieldA.getInitializer();
			List<AbstractInsnNode> initB = fieldB.getInitializer();

			if (initA == null && initB == null) return 1;
			if (initA == null || initB == null) return 0;

			Set<String> stringsA = new HashSet<>();
			ClassifierUtil.extractStrings(initA, stringsA);
			Set<String> stringsB = new HashSet<>();
			ClassifierUtil.extractStrings(initB, stringsB);

			return ClassifierUtil.compareSets(stringsA, stringsB, false);
		}
	};

	private static AbstractClassifier initCode = new AbstractClassifier("init code") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, ClassEnvironment env) {
			List<AbstractInsnNode> initA = fieldA.getInitializer();
			List<AbstractInsnNode> initB = fieldB.getInitializer();

			if (initA == null && initB == null) return 1;
			if (initA == null || initB == null) return 0;

			return ClassifierUtil.compareInsns(initA, initB, env);
		}
	};

	private static AbstractClassifier readRefsBci = new AbstractClassifier("read refs (bci)") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, ClassEnvironment env) {
			String ownerA = fieldA.getCls().getName();
			String nameA = fieldA.getName();
			String descA = fieldA.getDesc();
			String ownerB = fieldB.getCls().getName();
			String nameB = fieldB.getName();
			String descB = fieldB.getDesc();

			int matched = 0;
			int mismatched = 0;

			for (MethodInstance src : fieldA.getReadRefs()) {
				MethodInstance dst = src.getMatch();

				if (dst == null || !fieldB.getReadRefs().contains(dst)) {
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
					if (in.getOpcode() != Opcodes.GETFIELD && in.getOpcode() != Opcodes.GETSTATIC) continue;

					FieldInsnNode fin = (FieldInsnNode) in;
					if (!isSameField(fin, ownerA, nameA, descA, fieldA)) continue;

					in = ilB.get(map[srcIdx]);
					fin = (FieldInsnNode) in;

					if (!isSameField(fin, ownerB, nameB, descB, fieldB)) {
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

	private static AbstractClassifier writeRefsBci = new AbstractClassifier("write refs (bci)") {
		@Override
		public double getScore(FieldInstance fieldA, FieldInstance fieldB, ClassEnvironment env) {
			String ownerA = fieldA.getCls().getName();
			String nameA = fieldA.getName();
			String descA = fieldA.getDesc();
			String ownerB = fieldB.getCls().getName();
			String nameB = fieldB.getName();
			String descB = fieldB.getDesc();

			int matched = 0;
			int mismatched = 0;

			for (MethodInstance src : fieldA.getWriteRefs()) {
				MethodInstance dst = src.getMatch();

				if (dst == null || !fieldB.getWriteRefs().contains(dst)) {
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
					if (in.getOpcode() != Opcodes.PUTFIELD && in.getOpcode() != Opcodes.PUTSTATIC) continue;

					FieldInsnNode fin = (FieldInsnNode) in;
					if (!isSameField(fin, ownerA, nameA, descA, fieldA)) continue;

					in = ilB.get(map[srcIdx]);
					fin = (FieldInsnNode) in;

					if (!isSameField(fin, ownerB, nameB, descB, fieldB)) {
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

	private static boolean isSameField(FieldInsnNode fin, String owner, String name, String desc, FieldInstance field) {
		ClassInstance target;

		return fin.name.equals(name)
				&& fin.desc.equals(desc)
				&& (fin.owner.equals(owner) || (target = field.getEnv().getClsByName(fin.owner)) != null && target.resolveField(name, desc) == field);
	}

	private static boolean checkAsmNodes(FieldInstance a, FieldInstance b) {
		return a.getAsmNode() != null && b.getAsmNode() != null;
	}

	private static double compareAsmNodes(FieldInstance a, FieldInstance b) {
		return a.getAsmNode() == null && b.getAsmNode() == null ? 1 : 0;
	}

	public static abstract class AbstractClassifier implements IClassifier<FieldInstance> {
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
