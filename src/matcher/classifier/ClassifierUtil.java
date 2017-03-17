package matcher.classifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import matcher.Matcher;
import matcher.Util;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.IMatchable;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;

public class ClassifierUtil {
	public static boolean checkPotentialEquality(ClassInstance a, ClassInstance b) {
		if (a == b) return true;
		if (a.getMatch() != null) return a.getMatch() == b;
		if (b.getMatch() != null) return b.getMatch() == a;
		if (assumeBothOrNoneObfuscated && (a.isNameObfuscated() != b.isNameObfuscated())) return false;
		if (a.isArray() != b.isArray()) return false;
		if (a.isArray() && !checkPotentialEquality(a.getElementClass(), b.getElementClass())) return false;

		return true;
	}

	public static boolean checkPotentialEquality(MethodInstance a, MethodInstance b) {
		if (a == b) return true;
		if (a.getMatch() != null) return a.getMatch() == b;
		if (b.getMatch() != null) return b.getMatch() == a;
		if (!checkPotentialEquality(a.getCls(), b.getCls())) return false;
		if (!a.isNameObfuscated() && !b.isNameObfuscated()) return a.getOrigName().equals(b.getOrigName());
		if (assumeBothOrNoneObfuscated && (!a.isNameObfuscated() || !b.isNameObfuscated())) return false;

		return true;
	}

	public static boolean checkPotentialEquality(FieldInstance a, FieldInstance b) {
		if (a == b) return true;
		if (a.getMatch() != null) return a.getMatch() == b;
		if (b.getMatch() != null) return b.getMatch() == a;
		if (!checkPotentialEquality(a.getCls(), b.getCls())) return false;
		if (!a.isNameObfuscated() && !b.isNameObfuscated()) return a.getOrigName().equals(b.getOrigName());
		if (assumeBothOrNoneObfuscated && (!a.isNameObfuscated() || !b.isNameObfuscated())) return false;

		return true;
	}

	public static double compareCounts(int countA, int countB) {
		int delta = Math.abs(countA - countB);
		if (delta == 0) return 1;

		return 1 - (double) delta / Math.max(countA, countB);
	}

	public static <T> double compareSets(Set<T> setA, Set<T> setB, boolean readOnly) {
		if (readOnly) setB = Util.copySet(setB);

		int oldSize = setB.size();
		setB.removeAll(setA);

		int matched = oldSize - setB.size();
		int total = setA.size() - matched + oldSize;

		return total == 0 ? 1 : (double) matched / total;
	}

	public static double compareClassSets(Set<ClassInstance> setA, Set<ClassInstance> setB, boolean readOnly) {
		return compareIdentitySets(setA, setB, readOnly, ClassifierUtil::checkPotentialEquality);
	}

	public static double compareMethodSets(Set<MethodInstance> setA, Set<MethodInstance> setB, boolean readOnly) {
		return compareIdentitySets(setA, setB, readOnly, ClassifierUtil::checkPotentialEquality);
	}

	public static double compareFieldSets(Set<FieldInstance> setA, Set<FieldInstance> setB, boolean readOnly) {
		return compareIdentitySets(setA, setB, readOnly, ClassifierUtil::checkPotentialEquality);
	}

	private static <T extends IMatchable<T>> double compareIdentitySets(Set<T> setA, Set<T> setB, boolean readOnly, BiPredicate<T, T> comparator) {
		if (setA.isEmpty() || setB.isEmpty()) {
			return setA.isEmpty() && setB.isEmpty() ? 1 : 0;
		}

		if (readOnly) {
			setA = Util.newIdentityHashSet(setA);
			setB = Util.newIdentityHashSet(setB);
		}

		final int total = setA.size() + setB.size();
		int unmatched = 0;

		// precise matches, nameObfuscated a
		for (Iterator<T> it = setA.iterator(); it.hasNext(); ) {
			T a = it.next();

			if (setB.remove(a)) {
				it.remove();
			} else if (a.getMatch() != null) {
				if (!setB.remove(a.getMatch())) {
					unmatched++;
				}

				it.remove();
			} else if (assumeBothOrNoneObfuscated && !a.isNameObfuscated()) {
				unmatched++;
				it.remove();
			}
		}

		// nameObfuscated b
		if (assumeBothOrNoneObfuscated) {
			for (Iterator<T> it = setB.iterator(); it.hasNext(); ) {
				T b = it.next();

				if (!b.isNameObfuscated()) {
					unmatched++;
					it.remove();
				}
			}
		}

		for (Iterator<T> it = setA.iterator(); it.hasNext(); ) {
			T a = it.next();

			assert a.getMatch() == null && a.isNameObfuscated();
			boolean found = false;

			for (T b : setB) {
				if (comparator.test(a, b)) {
					found = true;
					break;
				}
			}

			if (!found) {
				unmatched++;
				it.remove();
			}
		}

		for (T b : setB) {
			boolean found = false;

			for (T a : setA) {
				if (comparator.test(a, b)) {
					found = true;
					break;
				}
			}

			if (!found) {
				unmatched++;
			}
		}

		assert unmatched <= total;

		return (double) (total - unmatched) / total;
	}

	public static double compareClassLists(List<ClassInstance> listA, List<ClassInstance> listB) {
		if (listA.isEmpty() && listB.isEmpty()) return 1;
		if (listA.isEmpty() || listB.isEmpty()) return 0;

		if (listA.size() == listB.size()) {
			boolean match = true;

			for (int i = 0; i < listA.size(); i++) {
				if (!checkPotentialEquality(listA.get(i), listB.get(i))) {
					match = false;
					break;
				}
			}

			if (match) return 1;
		}

		// levenshtein distance as per wp (https://en.wikipedia.org/wiki/Levenshtein_distance#Iterative_with_two_matrix_rows)
		int[] v0 = new int[listB.size() + 1];
		int[] v1 = new int[listB.size() + 1];

		for (int i = 0; i < v0.length; i++) {
			v0[i] = i;
		}

		for (int i = 0; i < listA.size(); i++) {
			v1[0] = i + 1;

			for (int j = 0; j < listB.size(); j++) {
				int cost = checkPotentialEquality(listA.get(i), listB.get(j)) ? 0 : 1;
				v1[j + 1] = Math.min(Math.min(v1[j] + 1, v0[j + 1] + 1), v0[j] + cost);
			}

			for (int j = 0; j < v0.length; j++) {
				v0[j] = v1[j];
			}
		}

		int distance = v1[listB.size()];
		int upperBound = Math.max(listA.size(), listB.size());
		assert distance >= 0 && distance <= upperBound;

		return 1 - (double) distance / upperBound;
	}

	public static <T> List<RankResult<T>> rank(T src, T[] dsts, Map<IClassifier<T>, Double> classifiers, BiPredicate<T, T> potentialEqualityCheck, Matcher matcher) {
		List<RankResult<T>> ret = new ArrayList<>(dsts.length);

		for (T dst : dsts) {
			if (!potentialEqualityCheck.test(src, dst)) continue;

			double score = 0;
			List<ClassifierResult<T>> results = new ArrayList<>(classifiers.size());

			for (Map.Entry<IClassifier<T>, Double> entry : classifiers.entrySet()) {
				double cScore = entry.getKey().getScore(src, dst, matcher);
				assert cScore > -epsilon && cScore < 1 + epsilon : "invalid score from "+entry.getKey().getName()+": "+cScore;

				score += cScore * entry.getValue();
				results.add(new ClassifierResult<>(entry.getKey(), cScore));
			}

			ret.add(new RankResult<>(dst, score, results));
		}

		ret.sort(Comparator.<RankResult<T>, Double>comparing(RankResult::getScore).reversed());

		return ret;
	}

	public static void extractStrings(MethodNode node, Set<String> out) {
		for (Iterator<AbstractInsnNode> it = node.instructions.iterator(); it.hasNext(); ) {
			AbstractInsnNode aInsn = it.next();

			if (aInsn instanceof LdcInsnNode) {
				LdcInsnNode insn = (LdcInsnNode) aInsn;

				if (insn.cst instanceof String) {
					out.add((String) insn.cst);
				}
			}
		}
	}

	public static void extractNumbers(MethodNode node, Set<Integer> ints, Set<Long> longs, Set<Float> floats, Set<Double> doubles) {
		for (Iterator<AbstractInsnNode> it = node.instructions.iterator(); it.hasNext(); ) {
			AbstractInsnNode aInsn = it.next();

			if (aInsn instanceof LdcInsnNode) {
				LdcInsnNode insn = (LdcInsnNode) aInsn;

				handleNumberValue(insn.cst, ints, longs, floats, doubles);
			} else if (aInsn instanceof IntInsnNode) {
				IntInsnNode insn = (IntInsnNode) aInsn;

				ints.add(insn.operand);
			}
		}
	}

	public static void handleNumberValue(Object number, Set<Integer> ints, Set<Long> longs, Set<Float> floats, Set<Double> doubles) {
		if (number == null) return;

		if (number instanceof Integer) {
			ints.add((Integer) number);
		} else if (number instanceof Long) {
			longs.add((Long) number);
		} else if (number instanceof Float) {
			floats.add((Float) number);
		} else if (number instanceof Double) {
			doubles.add((Double) number);
		}
	}

	public static <T extends MemberInstance<T>> double classifyPosition(T a, T b, BiFunction<ClassInstance, Integer, T> siblingSupplier, Function<ClassInstance, T[]> siblingsSupplier) {
		int posA = a.getPosition();
		int posB = b.getPosition();
		T[] siblingsA = siblingsSupplier.apply(a.getCls());
		T[] siblingsB = siblingsSupplier.apply(b.getCls());

		if (posA == posB && siblingsA.length == siblingsB.length) return 1;
		if (posA == -1 || posB == -1) return posA == posB ? 1 : 0;

		// try to find the index range enclosed by other mapped members and compare relative to it
		int startPosA = 0;
		int startPosB = 0;
		int endPosA = siblingsA.length;
		int endPosB = siblingsB.length;

		if (posA > 0) {
			for (int i = posA - 1; i >= 0; i--) {
				T c = siblingSupplier.apply(a.getCls(), i);

				if (c.getMatch() != null) {
					startPosA = i + 1;
					startPosB = c.getMatch().getPosition() + 1;
					break;
				}
			}
		}

		if (posA < endPosA - 1) {
			for (int i = posA + 1; i < endPosA; i++) {
				T c = siblingSupplier.apply(a.getCls(), i);

				if (c.getMatch() != null) {
					endPosA = i;
					endPosB = c.getMatch().getPosition();
					break;
				}
			}
		}

		if (startPosB >= endPosB || startPosB > posB || endPosB <= posB) {
			startPosA = startPosB = 0;
			endPosA = siblingsA.length;
			endPosB = siblingsB.length;
		}

		double relPosA = getRelativePosition(posA - startPosA, endPosA - startPosA);
		assert relPosA >= 0 && relPosA <= 1;
		double relPosB = getRelativePosition(posB - startPosB, endPosB - startPosB);
		assert relPosB >= 0 && relPosB <= 1;

		return 1 - Math.abs(relPosA - relPosB);
	}

	private static double getRelativePosition(int position, int size) {
		if (size == 1) return 0.5;
		assert size > 1;

		return (double) position / (size - 1);
	}

	private static final boolean assumeBothOrNoneObfuscated = true;
	private static final double epsilon = 1e-6;
}
