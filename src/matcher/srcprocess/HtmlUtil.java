package matcher.srcprocess;

import matcher.type.FieldInstance;
import matcher.type.MethodInstance;

public class HtmlUtil {
	public static String getId(MethodInstance method) {
		return "method-".concat(escapeId(method.getId()));
	}

	public static String getId(FieldInstance field) {
		return "field-".concat(escapeId(field.getId()));
	}

	private static String escapeId(String str) {
		StringBuilder ret = null;
		int retEnd = 0;

		for (int i = 0, max = str.length(); i < max; i++) {
			char c = str.charAt(i);

			if ((c < 'A' || c > 'Z') && (c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '-' && c != '_' && c != '.') { // use : as an escape identifier
				if (ret == null) ret = new StringBuilder(max * 2);

				ret.append(str, retEnd, i);

				ret.append(':');

				for (int j = 0; j < 4; j++) {
					int v = (c >>> ((3 - j) * 4)) & 0xf;
					ret.append("0123456789abcdef".charAt(v));
				}

				retEnd = i + 1;
			}
		}

		if (ret == null) {
			return str;
		} else {
			ret.append(str, retEnd, str.length());

			return ret.toString();
		}
	}

	public static String escape(String str) {
		StringBuilder ret = null;
		int retEnd = 0;

		for (int i = 0, max = str.length(); i < max; i++) {
			char c = str.charAt(i);

			if (c == '<' || c == '>' || c == '&') {
				if (ret == null) ret = new StringBuilder(max * 2);

				ret.append(str, retEnd, i);

				if (c == '<') {
					ret.append("&lt;");
				} else if (c == '>') {
					ret.append("&gt;");
				} else {
					ret.append("&amp;");
				}

				retEnd = i + 1;
			}
		}

		if (ret == null) {
			return str;
		} else {
			ret.append(str, retEnd, str.length());

			return ret.toString();
		}
	}
}
