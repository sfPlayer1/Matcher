package matcher.model.mapping;

import java.util.Comparator;
import java.util.Objects;

import matcher.model.NameType;
import matcher.model.type.ClassInstance;
import matcher.model.type.Matchable;
import matcher.model.type.MemberInstance;
import matcher.model.type.MethodVarInstance;

public final class MappedElementComparators {
	public static <T extends MemberInstance<T>> Comparator<T> byName(NameType ns) {
		return new Comparator<T>() {
			@Override
			public int compare(T a, T b) {
				return compareNullLast(a.getName(ns), b.getName(ns));
			}
		};
	}

	public static <T extends Matchable<T>> Comparator<T> byNameShortFirst(NameType ns) {
		return new Comparator<T>() {
			@Override
			public int compare(T a, T b) {
				String nameA = a.getName(ns);
				String nameB = b.getName(ns);

				if (nameA == null || nameB == null) {
					return compareNullLast(nameA, nameB);
				} else {
					return compareNameShortFirst(nameA, 0, nameA.length(), nameB, 0, nameB.length());
				}
			}
		};
	}

	public static Comparator<ClassInstance> byNameShortFirstNestaware(NameType ns) {
		return new Comparator<ClassInstance>() {
			@Override
			public int compare(ClassInstance a, ClassInstance b) {
				String nameA = a.getName(ns);
				String nameB = b.getName(ns);

				if (nameA == null || nameB == null) {
					return compareNullLast(nameA, nameB);
				}

				int pos = 0;

				do {
					int endA = nameA.indexOf('$', pos);
					int endB = nameB.indexOf('$', pos);

					int ret = compareNameShortFirst(nameA, pos, endA >= 0 ? endA : nameA.length(),
							nameB, pos, endB >= 0 ? endB : nameB.length());

					if (ret != 0) {
						return ret;
					} else if ((endA < 0) != (endB < 0)) {
						return endA < 0 ? -1 : 1;
					}

					pos = endA + 1;
				} while (pos > 0);

				return 0;
			}
		};
	}

	private static int compareNameShortFirst(String nameA, int startA, int endA, String nameB, int startB, int endB) {
		int lenA = endA - startA;
		int ret = Integer.compare(lenA, endB - startB);
		if (ret != 0) return ret;

		for (int i = 0; i < lenA; i++) {
			char a = nameA.charAt(startA + i);
			char b = nameB.charAt(startB + i);

			if (a != b) {
				return a - b;
			}
		}

		return 0;
	}

	public static <T extends MemberInstance<?>> Comparator<T> byNameDescConcat(NameType ns) {
		return new Comparator<T>() {
			@Override
			public int compare(T a, T b) {
				String valA = Objects.toString(a.getName(ns)).concat(Objects.toString(a.getDesc(ns)));
				String valB = Objects.toString(b.getName(ns)).concat(Objects.toString(b.getDesc(ns)));

				return valA.compareTo(valB);
			}
		};
	}

	public static Comparator<MethodVarInstance> byLvIndex() {
		return lvIndexComparator;
	}

	private static final Comparator<MethodVarInstance> lvIndexComparator = new Comparator<MethodVarInstance>() {
		@Override
		public int compare(MethodVarInstance a, MethodVarInstance b) {
			int ret = Integer.compare(a.getLvIndex(), b.getLvIndex());

			if (ret == 0) {
				ret = Integer.compare(a.getStartOpIdx(), b.getStartOpIdx());
			}

			return ret;
		}
	};

	public static Comparator<MethodVarInstance> byLvtIndex() {
		return lvtIndexComparator;
	}

	private static final Comparator<MethodVarInstance> lvtIndexComparator = new Comparator<MethodVarInstance>() {
		@Override
		public int compare(MethodVarInstance a, MethodVarInstance b) {
			return Integer.compare(a.getAsmIndex(), b.getAsmIndex());
		}
	};

	private static int compareNullLast(String a, String b) {
		if (a == null) {
			if (b == null) { // both null
				return 0;
			} else { // only a null
				return 1;
			}
		} else if (b == null) { // only b null
			return -1;
		} else { // neither null
			return a.compareTo(b);
		}
	}
}
