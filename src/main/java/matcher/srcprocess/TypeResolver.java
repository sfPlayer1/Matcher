package matcher.srcprocess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.nodeTypes.NodeWithParameters;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;

import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.NameType;
import matcher.type.ClassEnv;
import matcher.type.Matchable;
import matcher.type.MethodInstance;

class TypeResolver {
	public void setup(ClassInstance rootCls, NameType nameType, CompilationUnit cu) {
		this.rootCls = rootCls;
		this.env = rootCls.getEnv();
		this.nameType = nameType;

		if (cu.getPackageDeclaration().isPresent()) {
			pkg = cu.getPackageDeclaration().get().getNameAsString().replace('.', '/');
			wildcardImports.add(pkg.concat("/"));
		} else {
			pkg = null;
			wildcardImports.add("");
		}

		wildcardImports.add("java/lang/");

		for (ImportDeclaration imp : cu.getImports()) {
			if (imp.isStatic()) continue;

			if (imp.isAsterisk()) {
				wildcardImports.add(imp.getNameAsString().replace('.', '/').concat("/"));
			} else {
				String name = imp.getNameAsString();
				int pos = name.lastIndexOf('.');

				if (pos != -1) imports.put(name.substring(pos + 1), name.replace('.', '/'));
			}
		}
	}

	public ClassInstance getCls(Node node) {
		StringBuilder sb = new StringBuilder();

		if (pkg != null) {
			sb.append(pkg);
			sb.append('/');
		}

		int pkgEnd = sb.length();

		do {
			if (node instanceof ClassOrInterfaceDeclaration || node instanceof EnumDeclaration) {
				TypeDeclaration<?> typeDecl = (TypeDeclaration<?>) node;

				String namePart = typeDecl.getName().getIdentifier();

				if (sb.length() > pkgEnd) {
					sb.insert(pkgEnd, '$');
					sb.insert(pkgEnd, namePart);
				} else {
					sb.append(namePart);
				}
			}
		} while ((node = node.getParentNode().orElse(null)) != null);

		ClassInstance cls = getClsByName(sb.toString());

		return cls;
	}

	public <T extends CallableDeclaration<T> & NodeWithParameters<T>> MethodInstance getMethod(T methodDecl) {
		ClassInstance cls = getCls(methodDecl);
		if (cls == null) return null;

		StringBuilder sb = new StringBuilder();
		sb.append('(');

		if (methodDecl instanceof ConstructorDeclaration && cls.isEnum()) { // implicit extra String name + int ordinal for enum constructors
			sb.append("Ljava/lang/String;I");
		}

		for (Parameter p : methodDecl.getParameters()) {
			sb.append(toDesc(p.getType(), rootCls));
		}

		sb.append(')');

		if (methodDecl instanceof NodeWithType) {
			sb.append(toDesc(((NodeWithType<?, ?>) methodDecl).getType(), rootCls));
		} else {
			sb.append('V');
		}

		String desc = sb.toString();
		String name;

		if (methodDecl instanceof ConstructorDeclaration) {
			name = "<init>";
		} else {
			name = methodDecl.getName().getIdentifier();
		}

		return cls.getMethod(name, desc, nameType);
	}

	public FieldInstance getField(VariableDeclarator var) {
		ClassInstance cls = getCls(var);
		if (cls == null) return null;

		String name = var.getName().getIdentifier();
		String desc = toDesc(var.getType(), rootCls);

		return cls.getField(name, desc, nameType);
	}

	public FieldInstance getField(EnumConstantDeclaration var) {
		ClassInstance cls = getCls(var);
		if (cls == null) return null;

		String name = var.getName().getIdentifier();
		String desc = !cls.isPrimitive() ? "L"+cls.getName(nameType)+";" : cls.getId();

		return cls.getField(name, desc, nameType);
	}

	private String toDesc(Type type, ClassInstance context) {
		if (type instanceof PrimitiveType) {
			PrimitiveType t = (PrimitiveType) type;

			switch (t.getType()) {
			case BOOLEAN: return "Z";
			case BYTE: return "B";
			case CHAR: return "C";
			case DOUBLE: return "D";
			case FLOAT: return "F";
			case INT: return "I";
			case LONG: return "J";
			case SHORT: return "S";
			default:
				throw new IllegalArgumentException("invalid primitive type class: "+t.getType().getClass().getName());
			}
		} else if (type instanceof VoidType) {
			return "V";
		} else if (type instanceof ClassOrInterfaceType) {
			ClassOrInterfaceType t = (ClassOrInterfaceType) type;
			String name;

			if (!t.getScope().isPresent()) {
				name = t.getNameAsString();
			} else {
				List<String> parts = new ArrayList<>();

				do {
					parts.add(t.getName().getIdentifier());
				} while ((t = t.getScope().orElse(null)) != null);

				StringBuilder sb = new StringBuilder();

				for (int i = parts.size() - 1; i >= 0; i--) {
					if (sb.length() > 1) sb.append('/');
					sb.append(parts.get(i));
				}

				name = sb.toString();
			}

			assert name.indexOf('.') == -1;

			// direct lookup (for fully qualified name without nested classes)
			ClassInstance cls = getClsByName(name);
			if (cls != null) return ClassInstance.getId(name);

			// nested lookup (if name is a nested class of the context class)
			String nestedName = getName(context) + '$' + name.replace('/', '$');
			cls = getClsByName(nestedName);
			if (cls != null) return ClassInstance.getId(nestedName);

			int pkgEnd = name.lastIndexOf('/');
			int nameEnd = name.length();

			for (;;) {
				if (pkgEnd == -1) { // non-fqn (fqn without package was already checked)
					String cName = name.substring(pkgEnd + 1, nameEnd);
					String suffix = name.substring(nameEnd).replace('/', '$');

					// plain import lookup
					String importedName = imports.get(cName);

					if (importedName != null) {
						return ClassInstance.getId(importedName.concat(suffix));
					}

					// wildcard import lookup
					for (String wildcardImport : wildcardImports) {
						String fullName = wildcardImport + cName + suffix;
						cls = getClsByName(fullName);
						if (cls != null) return ClassInstance.getId(fullName);
					}

					break;
				}

				nameEnd = pkgEnd;
				pkgEnd = name.lastIndexOf('/', nameEnd - 1);

				// direct lookup (for fully qualified name with nested classes)
				String fullName = name.substring(0, nameEnd).concat(name.substring(nameEnd).replace('/', '$'));
				cls = getClsByName(fullName);
				if (cls != null) return ClassInstance.getId(fullName);
			}

			return ClassInstance.getId(name);
		} else if (type instanceof ArrayType) {
			ArrayType t = (ArrayType) type;
			Type componentType = t.getComponentType();
			int dims = 1;

			while (componentType instanceof ArrayType) {
				componentType = ((ArrayType) componentType).getComponentType();
				dims++;
			}

			StringBuilder ret = new StringBuilder();
			for (int i = 0; i < dims; i++) ret.append('[');
			ret.append(toDesc(componentType, context));

			return ret.toString();
		} else {
			throw new IllegalArgumentException("invalid type class: "+type.getClass().getName());
		}
	}

	public ClassInstance getClsByName(String name) {
		return env.getClsByName(name, nameType);
	}

	public String getName(Matchable<?> e) {
		return e.getName(nameType);
	}

	private ClassInstance rootCls;
	private ClassEnv env;
	private NameType nameType;
	private String pkg;
	private final Map<String, String> imports = new HashMap<>();
	private final List<String> wildcardImports = new ArrayList<>();
}
