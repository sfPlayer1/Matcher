package matcher.type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import matcher.NameType;
import matcher.classifier.ClassifierUtil;

public class Signature {
	public static class ClassSignature implements PotentialComparable<ClassSignature> {
		public static ClassSignature parse(String sig, ClassEnv env) {
			// [<TypeParameter+>] ClassTypeSignature ClassTypeSignature*
			ClassSignature ret = new ClassSignature();
			MutableInt pos = new MutableInt();

			if (sig.startsWith("<")) {
				pos.val++;
				ret.typeParameters = new ArrayList<>();

				do {
					ret.typeParameters.add(TypeParameter.parse(sig, pos, env));
				} while (sig.charAt(pos.val) != '>');

				pos.val++;
			}

			ret.superClassSignature = ClassTypeSignature.parse(sig, pos, env);

			if (pos.val < sig.length()) {
				ret.superInterfaceSignatures = new ArrayList<>();

				do {
					ret.superInterfaceSignatures.add(ClassTypeSignature.parse(sig, pos, env));
				} while (pos.val < sig.length());
			}

			assert ret.toString().equals(sig);

			return ret;
		}

		public String toString(NameType nameType) {
			StringBuilder ret = new StringBuilder();

			if (typeParameters != null) {
				ret.append('<');

				for (TypeParameter tp : typeParameters) {
					ret.append(tp.toString(nameType));
				}

				ret.append('>');
			}

			ret.append(superClassSignature.toString(nameType));

			if (superInterfaceSignatures != null) {
				for (ClassTypeSignature ts : superInterfaceSignatures) {
					ret.append(ts.toString(nameType));
				}
			}

			return ret.toString();
		}

		@Override
		public String toString() {
			return toString(NameType.PLAIN);
		}

		@Override
		public boolean isPotentiallyEqual(ClassSignature o) {
			return Signature.isPotentiallyEqual(typeParameters, o.typeParameters)
					&& superClassSignature.isPotentiallyEqual(o.superClassSignature)
					&& Signature.isPotentiallyEqual(superInterfaceSignatures, o.superInterfaceSignatures);
		}

		// [<
		List<TypeParameter> typeParameters;
		// >]
		ClassTypeSignature superClassSignature;
		List<ClassTypeSignature> superInterfaceSignatures;
	}

	public static class TypeParameter implements PotentialComparable<TypeParameter> {
		static TypeParameter parse(String sig, MutableInt pos, ClassEnv env) {
			// Identifier ':' ReferenceTypeSignature ( ':' ReferenceTypeSignature )*
			TypeParameter ret = new TypeParameter();

			int idEnd = sig.indexOf(':', pos.val);
			ret.identifier = sig.substring(pos.val, idEnd);
			pos.val = idEnd + 1;

			char next = sig.charAt(pos.val);

			if (next == 'L' || next == 'T' || next == '[') {
				ret.classBound = ReferenceTypeSignature.parse(sig, pos, env);
			}

			if (sig.charAt(pos.val) == ':') {
				ret.interfaceBounds = new ArrayList<>();

				do {
					pos.val++;
					ret.interfaceBounds.add(ReferenceTypeSignature.parse(sig, pos, env));
				} while (sig.charAt(pos.val) == ':');
			}

			return ret;
		}

		public String toString(NameType nameType) {
			StringBuilder ret = new StringBuilder();
			ret.append(identifier);
			ret.append(':');
			if (classBound != null) ret.append(classBound.toString(nameType));

			if (interfaceBounds != null) {
				for (ReferenceTypeSignature ts : interfaceBounds) {
					ret.append(':');
					ret.append(ts.toString(nameType));
				}
			}

			return ret.toString();
		}

		@Override
		public String toString() {
			return toString(NameType.PLAIN);
		}

		@Override
		public boolean isPotentiallyEqual(TypeParameter o) {
			return /*identifier.equals(o.identifier)
					&&*/ Signature.isPotentiallyEqual(classBound, o.classBound)
					&& Signature.isPotentiallyEqual(interfaceBounds, o.interfaceBounds);
		}

		String identifier;
		// :[
		ReferenceTypeSignature classBound;
		// ][:
		List<ReferenceTypeSignature> interfaceBounds; // separated by :
		// ]
	}

	public static class ReferenceTypeSignature implements PotentialComparable<ReferenceTypeSignature> {
		static ReferenceTypeSignature parse(String sig, MutableInt pos, ClassEnv env) {
			// ClassTypeSignature | TypeVariableSignature | ( '[' JavaTypeSignature )
			ReferenceTypeSignature ret = new ReferenceTypeSignature();
			char next = sig.charAt(pos.val);

			if (next == 'L') { // ClassTypeSignature: L[pkg/]cls[<arg>][.innerCls[<arg>]];
				ret.cls = ClassTypeSignature.parse(sig, pos, env);
			} else if (next == 'T') { // TypeVariableSignature: 'T' Identifier ';'
				pos.val++;
				int end = sig.indexOf(';', pos.val);
				ret.var = sig.substring(pos.val, end);
				pos.val = end + 1;
			} else if (next == '[') { // ArrayTypeSignature: '[' JavaTypeSignature
				pos.val++;
				ret.arrayElemCls = JavaTypeSignature.parse(sig, pos, env);
			} else {
				throw new RuntimeException("invalid char: "+next);
			}

			return ret;
		}

		public String toString(NameType nameType) {
			if (cls != null) {
				return cls.toString(nameType);
			} else if (var != null) {
				return "T"+var+";";
			} else {
				return "["+arrayElemCls.toString(nameType);
			}
		}

		@Override
		public String toString() {
			return toString(NameType.PLAIN);
		}

		@Override
		public boolean isPotentiallyEqual(ReferenceTypeSignature o) {
			if (cls != null) {
				return o.cls != null && cls.isPotentiallyEqual(o.cls);
			} else if (var != null) {
				return true;//var.equals(o.var);
			} else {
				assert arrayElemCls != null;
				return o.arrayElemCls != null && arrayElemCls.isPotentiallyEqual(o.arrayElemCls);
			}
		}

		ClassTypeSignature cls;
		// | (T
		String var;
		// ;) | (\[
		JavaTypeSignature arrayElemCls;
		// )
	}

	public static class ClassTypeSignature implements PotentialComparable<ClassTypeSignature> {
		static ClassTypeSignature parse(String sig, MutableInt pos, ClassEnv env) {
			// 'L' [PackageSpecifier] SimpleClassTypeSignature ( '.' SimpleClassTypeSignature )* ';'
			// [PackageSpecifier] SimpleClassTypeSignature -> cls + typeArguments
			ClassTypeSignature ret = new ClassTypeSignature();

			assert sig.charAt(pos.val) == 'L';
			pos.val++;

			int end = pos.val;
			char c;

			while ((c = sig.charAt(end)) != '<' && c != '.' && c != ';') {
				end++;
			}

			ret.cls = env.getCreateClassInstance(c == ';' ? sig.substring(pos.val - 1, end + 1) : sig.substring(pos.val - 1, end).concat(";"));
			pos.val = end;

			ret.typeArguments = SimpleClassTypeSignature.parseTypeArguments(sig, pos, env);

			if (sig.charAt(pos.val) == '.') {
				ret.suffixes = new ArrayList<>();

				do {
					pos.val++;
					ret.suffixes.add(SimpleClassTypeSignature.parse(sig, pos, env));
				} while (sig.charAt(pos.val) == '.');
			}

			assert sig.charAt(pos.val) == ';';
			pos.val++;

			return ret;
		}

		public String toString(NameType nameType) {
			StringBuilder ret = new StringBuilder();
			ret.append('L');
			ret.append(cls.getName(nameType));
			SimpleClassTypeSignature.printTypeArguments(typeArguments, nameType, ret);

			if (suffixes != null) {
				for (SimpleClassTypeSignature ts : suffixes) {
					ret.append('.');
					ret.append(ts.toString());
				}
			}

			ret.append(';');

			return ret.toString();
		}

		@Override
		public String toString() {
			return toString(NameType.PLAIN);
		}

		@Override
		public boolean isPotentiallyEqual(ClassTypeSignature o) {
			return ClassifierUtil.checkPotentialEquality(cls, o.cls)
					&& Signature.isPotentiallyEqual(typeArguments, o.typeArguments)
					&& Signature.isPotentiallyEqual(suffixes, o.suffixes);
		}

		ClassInstance cls;
		List<TypeArgument> typeArguments;
		List<SimpleClassTypeSignature> suffixes;
	}

	public static class JavaTypeSignature implements PotentialComparable<JavaTypeSignature> {
		static JavaTypeSignature parse(String sig, MutableInt pos, ClassEnv env) {
			// ReferenceTypeSignature | 'B' | 'C' | 'D' | 'F' | 'I' | 'J' | 'S' | 'Z'
			JavaTypeSignature ret = new JavaTypeSignature();
			char next = sig.charAt(pos.val);

			if (next == 'B' || next == 'C' || next == 'D' || next == 'F' || next == 'I' || next == 'J' || next == 'S' || next == 'Z') {
				ret.baseType = next;
				pos.val++;
			} else {
				ret.cls = ReferenceTypeSignature.parse(sig, pos, env);
			}

			return ret;
		}

		public String toString(NameType nameType) {
			if (cls != null) {
				return cls.toString(nameType);
			} else {
				return String.valueOf(baseType);
			}
		}

		@Override
		public String toString() {
			return toString(NameType.PLAIN);
		}

		@Override
		public boolean isPotentiallyEqual(JavaTypeSignature o) {
			if (cls != null) {
				return o.cls != null && cls.isPotentiallyEqual(o.cls);
			} else {
				return o.cls == null && baseType == o.baseType;
			}
		}

		ReferenceTypeSignature cls;
		// |
		char baseType; // B C D F I J S Z
	}

	public static class SimpleClassTypeSignature implements PotentialComparable<SimpleClassTypeSignature> {
		static SimpleClassTypeSignature parse(String sig, MutableInt pos, ClassEnv env) {
			// Identifier [<TypeArgument+>]
			SimpleClassTypeSignature ret = new SimpleClassTypeSignature();

			int end = pos.val;
			char c;

			while ((c = sig.charAt(end)) != '<' && c != '.' && c != ';') {
				end++;
			}

			ret.identifier = sig.substring(pos.val, end);
			pos.val = end;

			ret.typeArguments = parseTypeArguments(sig, pos, env);

			return ret;
		}

		static List<TypeArgument> parseTypeArguments(String sig, MutableInt pos, ClassEnv env) {
			if (sig.charAt(pos.val) == '<') {
				pos.val++;
				List<TypeArgument> ret = new ArrayList<>();

				do {
					ret.add(TypeArgument.parse(sig, pos, env));
				} while (sig.charAt(pos.val) != '>');

				pos.val++;

				return ret;
			} else {
				return null;
			}
		}

		public String toString(NameType nameType) {
			if (typeArguments == null) {
				return identifier;
			} else {
				StringBuilder ret = new StringBuilder();
				ret.append(identifier);
				printTypeArguments(typeArguments, nameType, ret);

				return ret.toString();
			}
		}

		@Override
		public String toString() {
			return toString(NameType.PLAIN);
		}

		static void printTypeArguments(List<TypeArgument> typeArguments, NameType nameType, StringBuilder ret) {
			if (typeArguments == null) return;

			ret.append('<');

			for (TypeArgument ta : typeArguments) {
				ret.append(ta.toString(nameType));
			}

			ret.append('>');
		}

		@Override
		public boolean isPotentiallyEqual(SimpleClassTypeSignature o) {
			return /*identifier.equals(o.identifier)
					&&*/ Signature.isPotentiallyEqual(typeArguments, o.typeArguments);
		}

		String identifier;
		// [<
		List<TypeArgument> typeArguments;
		// >]
	}

	public static class TypeArgument implements PotentialComparable<TypeArgument> {
		static TypeArgument parse(String sig, MutableInt pos, ClassEnv env) {
			// ( ['+'|'-'] ReferenceTypeSignature ) | '*'
			TypeArgument ret = new TypeArgument();
			char next = sig.charAt(pos.val);

			if (next == '*') { // unbounded
				pos.val++;
			} else {
				if (next == '+' || next == '-') { // extends / super
					ret.wildcardIndicator = next;
					pos.val++;
				}

				ret.cls = ReferenceTypeSignature.parse(sig, pos, env);
			}

			return ret;
		}

		public String toString(NameType nameType) {
			if (wildcardIndicator != 0) {
				return wildcardIndicator+cls.toString(nameType);
			} else if (cls == null) {
				return "*";
			} else {
				return cls.toString(nameType);
			}
		}

		@Override
		public String toString() {
			return toString(NameType.PLAIN);
		}

		@Override
		public boolean isPotentiallyEqual(TypeArgument o) {
			return wildcardIndicator == o.wildcardIndicator
					&& Signature.isPotentiallyEqual(cls, o.cls);
		}

		// [
		char wildcardIndicator; // + (extends) or - (super) if present, otherwise 0
		// ]
		ReferenceTypeSignature cls; // null if TypeArgument = "*" (unbounded)
	}

	public static class MethodSignature implements PotentialComparable<MethodSignature> {
		public static MethodSignature parse(String sig, ClassEnv env) {
			// [<TypeParameter+>] '(' JavaTypeSignature* ')' ( 'V' | JavaTypeSignature ) ThrowsSignature*
			MethodSignature ret = new MethodSignature();
			MutableInt pos = new MutableInt();

			if (sig.startsWith("<")) {
				pos.val++;
				ret.typeParameters = new ArrayList<>();

				do {
					ret.typeParameters.add(TypeParameter.parse(sig, pos, env));
				} while (sig.charAt(pos.val) != '>');

				pos.val++;
			}

			assert sig.charAt(pos.val) == '(';
			pos.val++;

			ret.args = new ArrayList<>();

			while (sig.charAt(pos.val) != ')') {
				ret.args.add(JavaTypeSignature.parse(sig, pos, env));
			}

			assert sig.charAt(pos.val) == ')';
			pos.val++;

			if (sig.charAt(pos.val) == 'V') {
				pos.val++;
			} else {
				ret.result = JavaTypeSignature.parse(sig, pos, env);
			}

			if (pos.val < sig.length()) {
				ret.throwsSignatures = new ArrayList<>();

				do {
					ret.throwsSignatures.add(ThrowsSignature.parse(sig, pos, env));
				} while (pos.val < sig.length());
			}

			assert ret.toString().equals(sig);

			return ret;
		}

		public String toString(NameType nameType) {
			StringBuilder ret = new StringBuilder();

			if (typeParameters != null) {
				ret.append('<');

				for (TypeParameter tp : typeParameters) {
					ret.append(tp.toString(nameType));
				}

				ret.append('>');
			}

			ret.append('(');

			for (JavaTypeSignature arg : args) {
				ret.append(arg.toString(nameType));
			}

			ret.append(')');

			if (result == null) {
				ret.append('V');
			} else {
				ret.append(result.toString(nameType));
			}

			if (throwsSignatures != null) {
				for (ThrowsSignature ts : throwsSignatures) {
					ret.append(ts.toString(nameType));
				}
			}

			return ret.toString();
		}

		@Override
		public String toString() {
			return toString(NameType.PLAIN);
		}

		@Override
		public boolean isPotentiallyEqual(MethodSignature o) {
			return Signature.isPotentiallyEqual(typeParameters, o.typeParameters)
					&& Signature.isPotentiallyEqual(args, o.args)
					& Signature.isPotentiallyEqual(result, o.result)
					&& Signature.isPotentiallyEqual(throwsSignatures, o.throwsSignatures);
		}

		// [<
		List<TypeParameter> typeParameters;
		// >]\(
		List<JavaTypeSignature> args;
		// \)
		JavaTypeSignature result; // null if V (void)
		// [
		List<ThrowsSignature> throwsSignatures;
		// ]
	}

	public static class ThrowsSignature implements PotentialComparable<ThrowsSignature> {
		static ThrowsSignature parse(String sig, MutableInt pos, ClassEnv env) {
			// '^' ( ClassTypeSignature | TypeVariableSignature )
			ThrowsSignature ret = new ThrowsSignature();

			assert sig.charAt(pos.val) == '^';
			pos.val++;

			char next = sig.charAt(pos.val);

			if (next == 'L') {
				ret.cls = ClassTypeSignature.parse(sig, pos, env);
			} else if (next == 'T') { // TypeVariableSignature: 'T' Identifier ';'
				pos.val++;
				int end = sig.indexOf(';', pos.val);
				ret.var = sig.substring(pos.val, end);
				pos.val = end + 1;
			} else {
				throw new RuntimeException("invalid char: "+next);
			}

			return ret;
		}

		public String toString(NameType nameType) {
			if (cls != null) {
				return "^"+cls.toString(nameType);
			} else {
				return "^T"+var+";";
			}
		}

		@Override
		public String toString() {
			return toString(NameType.PLAIN);
		}

		@Override
		public boolean isPotentiallyEqual(ThrowsSignature o) {
			if (cls != null) {
				return o.cls != null && cls.isPotentiallyEqual(o.cls);
			} else {
				return true;//var.equals(o.var);
			}
		}

		// ^(
		ClassTypeSignature cls;
		// | (T
		String var;
		// ;))
	}

	public static class FieldSignature implements PotentialComparable<FieldSignature> {
		public static FieldSignature parse(String sig, ClassEnv env) {
			// ReferenceTypeSignature
			FieldSignature ret = new FieldSignature();
			MutableInt pos = new MutableInt();

			ret.cls = ReferenceTypeSignature.parse(sig, pos, env);
			assert pos.val == sig.length();

			assert ret.toString().equals(sig);

			return ret;
		}

		public String toString(NameType nameType) {
			return cls.toString(nameType);
		}

		@Override
		public String toString() {
			return toString(NameType.PLAIN);
		}

		@Override
		public boolean isPotentiallyEqual(FieldSignature o) {
			return cls.isPotentiallyEqual(o.cls);
		}

		ReferenceTypeSignature cls;
	}

	private static <T extends PotentialComparable<T>> boolean isPotentiallyEqual(T a, T b) {
		assert !(a instanceof Collection);

		return (a == null) == (b == null) && (a == null || a.isPotentiallyEqual(b));
	}

	private static <T extends PotentialComparable<T>> boolean isPotentiallyEqual(List<? extends T> a, List<? extends T> b) {
		if ((a == null) != (b == null)) return false;

		if (a != null) {
			if (a.size() != b.size()) return false;

			for (int i = 0, max = a.size(); i < max; i++) {
				if (!a.get(i).isPotentiallyEqual(b.get(i))) return false;
			}
		}

		return true;
	}

	private static class MutableInt {
		@Override
		public String toString() {
			return String.valueOf(val);
		}

		public int val;
	}

	private interface PotentialComparable<T> {
		boolean isPotentiallyEqual(T o);
	}
}
