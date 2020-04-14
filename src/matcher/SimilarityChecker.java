package matcher;

import matcher.classifier.ClassifierUtil;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public final class SimilarityChecker {
	public static float compare(ClassInstance a, ClassInstance b) {
		if (a.getMatch() != b) return 0;

		float ret = 0;

		for (MethodInstance m : a.getMethods()) {
			if (m.getMatch() != null) {
				ret += compare(m, m.getMatch());
			}
		}

		for (FieldInstance m : a.getFields()) {
			if (m.getMatch() != null) {
				ret += compare(m, m.getMatch());
			}
		}

		int div = a.getMethods().length + a.getFields().length;

		for (MethodInstance m : b.getMethods()) {
			if (m.getMatch() == null) {
				div++;
			}
		}

		for (FieldInstance m : b.getFields()) {
			if (m.getMatch() == null) {
				div++;
			}
		}

		return div > 0 ? ret / div : 1;
	}

	public static <T extends MemberInstance<T>> float compare(MemberInstance<T> a, MemberInstance<T> b) {
		if (a instanceof MethodInstance) {
			return compare((MethodInstance) a, (MethodInstance) b);
		} else {
			return compare((FieldInstance) a, (FieldInstance) b);
		}
	}

	public static float compare(MethodInstance a, MethodInstance b) {
		if (a.getMatch() != b) return 0;

		if (a.getAsmNode() == null || b.getAsmNode() == null) {
			return (a.getAsmNode() == null) == (b.getAsmNode() == null) ? 1 : 0;
		}

		float retTypeScore = ClassifierUtil.checkPotentialEquality(a.getRetType(), b.getRetType()) ? 1 : 0;
		float argTypeScore = 0;

		for (MethodVarInstance v : a.getArgs()) {
			if (v.getMatch() != null) {
				argTypeScore += compare(v, v.getMatch());
			}
		}

		int div = a.getArgs().length;

		for (MethodVarInstance v : b.getArgs()) {
			if (v.getMatch() == null) {
				div++;
			}
		}

		argTypeScore = div > 0 ? argTypeScore / div : 1;

		float contentScore = 0;
		int[] insnMap = ClassifierUtil.mapInsns(a, b);

		for (int i = 0; i < insnMap.length; i++) {
			if (insnMap[i] >= 0) contentScore++;
		}

		div = Math.max(insnMap.length, b.getAsmNode().instructions.size());
		contentScore = div > 0 ? contentScore / div : 1;

		return retTypeScore * METHOD_RETTYPE_WEIGHT + argTypeScore * METHOD_ARGS_WEIGHT + contentScore * METHOD_CONTENT_WEIGHT;
	}

	public static float compare(FieldInstance a, FieldInstance b) {
		if (a.getMatch() != b) return 0;

		return ClassifierUtil.checkPotentialEquality(a.getType(), b.getType()) ? 1 : SIMILARITY_MATCHED_TYPE_MISMATCH;
	}

	public static float compare(MethodVarInstance a, MethodVarInstance b) {
		if (a.getMatch() != b) return 0;

		return ClassifierUtil.checkPotentialEquality(a.getType(), b.getType()) ? 1 : SIMILARITY_MATCHED_TYPE_MISMATCH;
	}

	private static final float SIMILARITY_MATCHED_TYPE_MISMATCH = 0.5f;

	private static final float METHOD_RETTYPE_WEIGHT = 0.05f;
	private static final float METHOD_ARGS_WEIGHT = 0.2f;
	private static final float METHOD_CONTENT_WEIGHT = 1 - METHOD_RETTYPE_WEIGHT - METHOD_ARGS_WEIGHT;
}
