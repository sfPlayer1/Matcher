package matcher.mapping;

import java.io.IOException;
import java.io.Writer;

final class Tiny2Util {
	public static boolean needEscape(String s) {
		for (int pos = 0, len = s.length(); pos < len; pos++) {
			char c = s.charAt(pos);
			if (toEscape.indexOf(c) >= 0) return true;
		}

		return false;
	}

	public static void writeEscaped(String s, Writer out) throws IOException {
		final int len = s.length();
		int start = 0;

		for (int pos = 0; pos < len; pos++) {
			char c = s.charAt(pos);
			int idx = toEscape.indexOf(c);

			if (idx >= 0) {
				out.write(s, start, pos - start);
				out.write('\\');
				out.write(escaped.charAt(idx));
				start = pos + 1;
			}
		}

		out.write(s, start, len - start);
	}

	public static String unescape(String str) {
		int pos = str.indexOf('\\');
		if (pos < 0) return str;

		StringBuilder ret = new StringBuilder(str.length() - 1);
		int start = 0;

		do {
			ret.append(str, start, pos);
			pos++;
			int type;

			if (pos >= str.length()) {
				throw new RuntimeException("incomplete escape sequence at the end");
			} else if ((type = escaped.indexOf(str.charAt(pos))) < 0) {
				throw new RuntimeException("invalid escape character: \\"+str.charAt(pos));
			} else {
				ret.append(toEscape.charAt(type));
			}

			start = pos + 1;
		} while ((pos = str.indexOf('\\', start)) >= 0);

		ret.append(str, start, str.length());

		return ret.toString();
	}

	private static final String toEscape = "\\\n\r\0\t";
	private static final String escaped = "\\nr0t";

	static final String escapedNamesProperty = "escaped-names";
}
