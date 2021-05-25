package matcher.mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MappingUtil {
	static String mapDesc(String desc, Map<String, String> clsMap) {
		return mapDesc(desc, 0, desc.length(), clsMap);
	}

	static String mapDesc(String desc, int start, int end, Map<String, String> clsMap) {
		StringBuilder ret = null;
		int searchStart = start;
		int clsStart;

		while ((clsStart = desc.indexOf('L', searchStart)) >= 0) {
			int clsEnd = desc.indexOf(';', clsStart + 1);
			if (clsEnd < 0) throw new IllegalArgumentException();

			String cls = desc.substring(clsStart + 1, clsEnd);
			String mappedCls = clsMap.get(cls);

			if (mappedCls != null) {
				if (ret == null) ret = new StringBuilder(end - start);

				ret.append(desc, start, clsStart + 1);
				ret.append(mappedCls);
				start = clsEnd;
			}

			searchStart = clsEnd + 1;
		}

		if (ret == null) return desc.substring(start, end);

		ret.append(desc, start, end);

		return ret.toString();
	}

	public static int getArgCount(String methodDesc) {
		int ret = 0;
		int offset = 1;
		char c;

		while ((c = methodDesc.charAt(offset++)) != ')') {
			while (c == '[') {
				c = methodDesc.charAt(offset++);
			}

			if (c == 'L') {
				offset = methodDesc.indexOf(';', offset) + 1;
				if (offset == 0) throw new IllegalArgumentException("invalid desc: "+methodDesc);
			}

			ret++;
		}

		return ret;
	}

	public static void main(String[] args) {
		String[] pdescs = { "I", "II", "JI", "JJ" };
		int rows = 6;

		for (String pdesc : pdescs) {
			String desc = String.format("(%s)V", pdesc);
			List<String> output = new ArrayList<>();
			int maxLvIndex = 1;

			{
				int offset = 1;
				char c;

				while ((c = desc.charAt(offset++)) != ')') {
					boolean isArray = c == '[';

					if (isArray) {
						do {
							c = desc.charAt(offset++);
						} while (c == '[');
					}

					if (c == 'L') {
						offset = desc.indexOf(';', offset) + 1;
						if (offset == 0) throw new IllegalArgumentException("invalid desc: "+desc);
					}

					if (desc.charAt(offset) == ')') break;

					if (isArray || c != 'J' && c != 'D') {
						maxLvIndex++;
					} else {
						maxLvIndex += 2;
					}
				}
			}

			for (int lvIndex = 0; lvIndex <= maxLvIndex; lvIndex++) {
				for (int i = 0; i < 4; i++) {
					boolean actualStatic = (i & 2) == 0;
					boolean pretendStatic = (i & 1) == 0;

					int actualResult = getArgPos(lvIndex, desc, actualStatic);
					if (actualResult < 0) continue;

					int pretendResult = getArgPos(lvIndex, desc, pretendStatic);

					output.add(actualStatic ? "y" : "n");
					output.add(pretendStatic ? "y" : "n");
					output.add(Integer.toString(lvIndex));
					output.add(Integer.toString(pretendResult));
					output.add(actualResult >= 0 ? Integer.toString(actualResult) : "E");
					output.add(actualResult < 0 ? "x" : (pretendResult < 0 ? "F" : (actualResult == pretendResult ? "y" : "n")));
				}
			}

			for (int col = 0, cols = output.size() / rows; col < cols; col++) {
				int maxWidth = 0;

				for (int row = 0; row < rows; row++) {
					int idx = col * rows + row;
					String val = output.get(idx);
					maxWidth = Math.max(maxWidth, val.length());
				}

				for (int row = 0; row < rows; row++) {
					int idx = col * rows + row;
					String val = output.get(idx);

					if (val.length() < maxWidth) {
						while (val.length() < maxWidth) val = " ".concat(val);

						output.set(idx, val);
					}
				}
			}

			System.out.printf("desc %s:%n", pdesc);

			for (int row = 0; row < rows; row++) {
				switch (row) {
				case 0: System.out.print(" act.static"); break;
				case 1: System.out.print("pret.static"); break;
				case 2: System.out.print("   lv index"); break;
				case 3: System.out.print("        res"); break;
				case 4: System.out.print("        act"); break;
				case 5: System.out.print("    correct"); break;
				}

				for (int offset = row; offset < output.size(); offset += rows) {
					System.out.print(' ');
					System.out.print(output.get(offset));
				}

				System.out.println();
			}
		}
	}

	public static int getArgPos(int lvIndex, String methodDesc, boolean isStatic) {
		if (!isStatic) lvIndex--;

		int offset = 1;
		int ret = 0;

		while (lvIndex > 0) {
			char c = methodDesc.charAt(offset++);
			if (c == ')') return -1;

			boolean isArray = c == '[';

			if (isArray) {
				do {
					c = methodDesc.charAt(offset++);
				} while (c == '[');
			}

			if (c == 'L') {
				offset = methodDesc.indexOf(';', offset) + 1;
				if (offset == 0) throw new IllegalArgumentException("invalid desc: "+methodDesc);
			}

			if (isArray || c != 'J' && c != 'D') {
				lvIndex--;
			} else {
				lvIndex -= 2;
			}

			ret++;
		}

		return lvIndex == 0 && methodDesc.charAt(offset) != ')' ? ret : -1;
	}

	public static final String NS_SOURCE_FALLBACK = "source";
	public static final String NS_TARGET_FALLBACK = "target";
}
