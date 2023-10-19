package matcher.gui.srcprocess;

import java.util.regex.Pattern;

import matcher.model.type.FieldInstance;
import matcher.model.type.MethodInstance;

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

	public static String escape(String str, String... allowedTags) {
		Pattern tagPattern = compileTagPattern(allowedTags);
		StringBuilder ret = null;
		int retEnd = 0;

		for (int i = 0, max = str.length(); i < max; i++) {
			char c = str.charAt(i);

			if (c == '<' || c == '>' || c == '&') {
				if (ret == null) ret = new StringBuilder(max * 2);

				if (c == '<' && allowedTags != null) {
					int pos = str.substring(i, str.length()).indexOf('>');

					if (tagPattern.matcher(str.substring(i, i + pos + 1)).find()) {
						// Skip ahead to after the tag
						i += pos;
						continue;
					}
				}

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

	private static Pattern compileTagPattern(String... tags) {
		StringBuilder builder = new StringBuilder();
		builder.append("^<(/)?(");

		for (int i = 0; i < tags.length; i++) {
			builder.append(tags[i]);

			if (i < tags.length - 1) {
				builder.append('|');
			}
		}

		builder.append(")[^>]*?>");

		return Pattern.compile(builder.toString());
	}
}
