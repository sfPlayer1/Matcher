package matcher.classifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import matcher.Util;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;
import matcher.type.Signature.MethodSignature;

public class MethodClassifier {
	public static void init() {
		addClassifier(methodTypeCheck, 10);
		addClassifier(accessFlags, 4);
		addClassifier(argTypes, 10);
		addClassifier(retType, 5);
		addClassifier(signature, 5);
		addClassifier(classRefs, 3);
		addClassifier(stringConstants, 5);
		addClassifier(numericConstants, 5);
		addClassifier(parentMethods, 10);
		addClassifier(childMethods, 3);
		addClassifier(inReferences, 6);
		addClassifier(outReferences, 6);
		addClassifier(fieldReads, 5);
		addClassifier(fieldWrites, 5);
		addClassifier(position, 3);
		addClassifier(code, 12, ClassifierLevel.Full, ClassifierLevel.Extra);
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

	public static List<RankResult<MethodInstance>> rank(MethodInstance src, MethodInstance[] dsts, ClassifierLevel level, ClassEnvironment env) {
		return rank(src, dsts, level, env, Double.POSITIVE_INFINITY);
	}

	public static List<RankResult<MethodInstance>> rank(MethodInstance src, MethodInstance[] dsts, ClassifierLevel level, ClassEnvironment env, double maxMismatch) {
		if (src.hasMatch()) { // already matched,  limit dsts to the match
			if (!Arrays.asList(dsts).contains(src.getMatch())) {
				return Collections.emptyList();
			} else if (dsts.length != 1) {
				dsts = new MethodInstance[] { src.getMatch() };
			}
		} else { // limit dsts to the same method tree if there's a matched src
			MethodInstance matched = src.getMatchedHierarchyMember();

			if (matched != null) {
				Set<MethodInstance> dstHierarchyMembers = matched.getMatch().getAllHierarchyMembers();
				MethodInstance[] newDsts = new MethodInstance[dsts.length];
				int writeIdx = 0;

				for (int readIdx = 0; readIdx < dsts.length; readIdx++) {
					MethodInstance m = dsts[readIdx];

					if (dstHierarchyMembers.contains(m)) {
						newDsts[writeIdx++] = m;
					}
				}

				if (writeIdx == 0) return Collections.emptyList();
				if (writeIdx < newDsts.length) newDsts = Arrays.copyOf(newDsts, writeIdx);

				dsts = newDsts;
			}
		}

		return ClassifierUtil.rank(src, dsts, classifiers.getOrDefault(level, Collections.emptyList()), ClassifierUtil::checkPotentialEquality, env, maxMismatch);
	}

	private static final Map<ClassifierLevel, List<IClassifier<MethodInstance>>> classifiers = new EnumMap<>(ClassifierLevel.class);
	private static final Map<ClassifierLevel, Double> maxScore = new EnumMap<>(ClassifierLevel.class);

	private static AbstractClassifier methodTypeCheck = new AbstractClassifier("method type check") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			if (!checkAsmNodes(methodA, methodB)) return compareAsmNodes(methodA, methodB);

			int mask = Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT;
			int resultA = methodA.getAsmNode().access & mask;
			int resultB = methodB.getAsmNode().access & mask;

			return 1 - Integer.bitCount(resultA ^ resultB) / 3.;
		}
	};

	private static AbstractClassifier accessFlags = new AbstractClassifier("access flags") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			if (!checkAsmNodes(methodA, methodB)) return compareAsmNodes(methodA, methodB);

			int mask = (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE) | Opcodes.ACC_FINAL | Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_BRIDGE | Opcodes.ACC_VARARGS | Opcodes.ACC_STRICT | Opcodes.ACC_SYNTHETIC;
			int resultA = methodA.getAsmNode().access & mask;
			int resultB = methodB.getAsmNode().access & mask;

			return 1 - Integer.bitCount(resultA ^ resultB) / 8.;
		}
	};

	private static AbstractClassifier argTypes = new AbstractClassifier("arg types") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			return ClassifierUtil.compareClassLists(getArgTypes(methodA), getArgTypes(methodB));
		}
	};

	private static List<ClassInstance> getArgTypes(MethodInstance method) {
		MethodVarInstance[] args = method.getArgs();
		if (args.length == 0) return Collections.emptyList();

		List<ClassInstance> ret = new ArrayList<>(args.length);

		for (MethodVarInstance arg : args) {
			ret.add(arg.getType());
		}

		return ret;
	}

	private static AbstractClassifier retType = new AbstractClassifier("ret type") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			return ClassifierUtil.checkPotentialEquality(methodA.getRetType(), methodB.getRetType()) ? 1 : 0;
		}
	};

	private static AbstractClassifier signature = new AbstractClassifier("signature") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			MethodSignature sigA = methodA.getSignature();
			MethodSignature sigB = methodB.getSignature();

			if (sigA == null && sigB == null) return 1;
			if (sigA == null || sigB == null) return 0;

			return sigA.isPotentiallyEqual(sigB) ? 1 : 0;
		}
	};

	private static AbstractClassifier classRefs = new AbstractClassifier("class refs") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			return ClassifierUtil.compareClassSets(methodA.getClassRefs(), methodB.getClassRefs(), true);
		}
	};

	private static AbstractClassifier stringConstants = new AbstractClassifier("string constants") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			if (!checkAsmNodes(methodA, methodB)) return compareAsmNodes(methodA, methodB);

			Set<String> stringsA = new HashSet<>();
			ClassifierUtil.extractStrings(methodA.getAsmNode().instructions, stringsA);
			Set<String> stringsB = new HashSet<>();
			ClassifierUtil.extractStrings(methodB.getAsmNode().instructions, stringsB);

			return ClassifierUtil.compareSets(stringsA, stringsB, false);
		}
	};

	private static AbstractClassifier numericConstants = new AbstractClassifier("numeric constants") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
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

	private static AbstractClassifier parentMethods = new AbstractClassifier("parent methods") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			return ClassifierUtil.compareMethodSets(methodA.getParents(), methodB.getParents(), true);
		}
	};

	private static AbstractClassifier childMethods = new AbstractClassifier("child methods") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			return ClassifierUtil.compareMethodSets(methodA.getChildren(), methodB.getChildren(), true);
		}
	};

	private static AbstractClassifier outReferences = new AbstractClassifier("out references") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			return ClassifierUtil.compareMethodSets(methodA.getRefsOut(), methodB.getRefsOut(), true);
		}
	};

	private static AbstractClassifier inReferences = new AbstractClassifier("in references") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			return ClassifierUtil.compareMethodSets(methodA.getRefsIn(), methodB.getRefsIn(), true);
		}
	};

	private static AbstractClassifier fieldReads = new AbstractClassifier("field reads") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			return ClassifierUtil.compareFieldSets(methodA.getFieldReadRefs(), methodB.getFieldReadRefs(), true);
		}
	};

	private static AbstractClassifier fieldWrites = new AbstractClassifier("field writes") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			return ClassifierUtil.compareFieldSets(methodA.getFieldWriteRefs(), methodB.getFieldWriteRefs(), true);
		}
	};

	private static AbstractClassifier position = new AbstractClassifier("position") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			return ClassifierUtil.classifyPosition(methodA, methodB, MemberInstance::getPosition, (m, idx) -> m.getCls().getMethod(idx), m -> m.getCls().getMethods());
		}
	};

	private static AbstractClassifier code = new AbstractClassifier("code") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			if (!checkAsmNodes(methodA, methodB)) return compareAsmNodes(methodA, methodB);

			return ClassifierUtil.compareInsns(methodA, methodB);
		}
	};

	private static AbstractClassifier inRefsBci = new AbstractClassifier("in refs (bci)") {
		@Override
		public double getScore(MethodInstance methodA, MethodInstance methodB, ClassEnvironment env) {
			String ownerA = methodA.getCls().getName();
			String nameA = methodA.getName();
			String descA = methodA.getDesc();
			String ownerB = methodB.getCls().getName();
			String nameB = methodB.getName();
			String descB = methodB.getDesc();

			int matched = 0;
			int mismatched = 0;

			for (MethodInstance src : methodA.getRefsIn()) {
				if (src == methodA) continue;

				MethodInstance dst = src.getMatch();

				if (dst == null || !methodB.getRefsIn().contains(dst)) {
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
					int type = in.getType();
					if (type != AbstractInsnNode.METHOD_INSN && type != AbstractInsnNode.INVOKE_DYNAMIC_INSN) continue;

					if (!isSameMethod(in, ownerA, nameA, descA, methodA)) continue;

					in = ilB.get(map[srcIdx]);

					if (!isSameMethod(in, ownerB, nameB, descB, methodB)) {
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

	private static boolean isSameMethod(AbstractInsnNode in, String owner, String name, String desc, MethodInstance method) {
		String sOwner, sName, sDesc;
		boolean sItf;

		if (in.getType() == AbstractInsnNode.METHOD_INSN) {
			MethodInsnNode min = (MethodInsnNode) in;
			sOwner = min.owner;
			sName = min.name;
			sDesc = min.desc;
			sItf = min.itf;
		} else {
			InvokeDynamicInsnNode din = (InvokeDynamicInsnNode) in;
			Handle impl = Util.getTargetHandle(din.bsm, din.bsmArgs);
			if (impl == null) return false;

			int tag = impl.getTag();
			if (tag < Opcodes.H_INVOKEVIRTUAL || tag > Opcodes.H_INVOKEINTERFACE) return false;

			sOwner = impl.getOwner();
			sName = impl.getName();
			sDesc = impl.getDesc();
			sItf = Util.isCallToInterface(impl);
		}

		ClassInstance target;

		return sName.equals(name)
				&& sDesc.equals(desc)
				&& (sOwner.equals(owner) || (target = method.getEnv().getClsByName(sOwner)) != null && target.resolveMethod(name, desc, sItf) == method);
	}

	private static boolean checkAsmNodes(MethodInstance a, MethodInstance b) {
		return a.getAsmNode() != null && b.getAsmNode() != null;
	}

	private static double compareAsmNodes(MethodInstance a, MethodInstance b) {
		return a.getAsmNode() == null && b.getAsmNode() == null ? 1 : 0;
	}

	public static abstract class AbstractClassifier implements IClassifier<MethodInstance> {
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
