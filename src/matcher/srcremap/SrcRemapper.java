package matcher.srcremap;

import static com.github.javaparser.utils.PositionUtils.sortByBeginPosition;
import static com.github.javaparser.utils.Utils.isNullOrEmpty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeArguments;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.ForeachStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntryStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.PrettyPrintVisitor;
import com.github.javaparser.printer.PrettyPrinterConfiguration;

import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.IClassEnv;
import matcher.type.IMatchable;
import matcher.type.MethodInstance;

public class SrcRemapper {
	public static void main(String[] args) {
		try {
			decorate(new String(Files.readAllBytes(Paths.get("/home/m/tmp/test.java")), StandardCharsets.UTF_8), null, true);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static String decorate(String src, ClassInstance cls, boolean mapped) {
		Context context = new Context(cls.getEnv(), mapped);

		if (cls.getOuterClass() != null) {
			// replace <outer>.<inner> with <outer>$<inner> since . is not a legal identifier within class names and thus gets rejected by JavaParser

			String name = context.getName(cls);
			int pos = name.indexOf('$');

			if (pos != -1) {
				int end = name.length();
				char c;

				while ((c = name.charAt(end - 1)) >= '0' && c <= '9' || c == '$') { // FIXME: CFR strips digits only sometimes
					end--;
				}

				if (end > pos) {
					if ((pos = name.lastIndexOf('/')) != -1) {
						name = name.substring(pos + 1, end);
					} else if (end != name.length()) {
						name = name.substring(0, end);
					}

					src = src.replace(name.replace('$', '.'), name);
				}
			}
		}

		CompilationUnit cu;

		try {
			cu = JavaParser.parse(src);
		} catch (ParseProblemException e) {
			throw new ParseException(src, e);
		}

		cu.accept(remapVisitor, context);

		PrettyPrintVisitor printer = new Printer(new PrettyPrinterConfiguration().setIndent("\t").setEndOfLineCharacter("\n"));
		cu.accept(printer, null);

		return printer.getSource();
	}

	public static class ParseException extends RuntimeException {
		public ParseException(String source, Exception cause) {
			super("Parsing failed", cause);

			this.source = source;
		}

		public final String source;
	}

	private static String toDesc(Type type, Context context) {
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

			int pkgEnd = name.lastIndexOf('/');

			for (;;) {
				String cName = name.substring(pkgEnd + 1).replace('/', '$');

				if (pkgEnd == -1) {
					String importedName = context.imports.get(name);

					if (importedName != null) {
						return ClassInstance.getId(importedName);
					}

					for (String wildcardImport : context.wildcardImports) {
						String fullName = wildcardImport.concat(cName);
						ClassInstance cls = context.getClsByName(fullName);
						if (cls != null) return ClassInstance.getId(fullName);
					}

					break;
				}

				cName = name.substring(0, pkgEnd + 1).concat(cName);

				ClassInstance cls = context.getClsByName(cName);
				if (cls != null) ClassInstance.getId(cName);

				pkgEnd = name.lastIndexOf('/', pkgEnd - 1);
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

	private static ClassInstance findLocalCls(Node n, IClassEnv env) {
		Optional<Node> parent = n.getParentNode();

		while (parent.isPresent() && !(parent.get() instanceof ClassOrInterfaceDeclaration)) {
			parent = parent.get().getParentNode();
		}

		if (!parent.isPresent()) return null;

		return env.getLocalClsByName(((ClassOrInterfaceDeclaration) parent.get()).getName().getIdentifier().replace('.', '/'));
	}

	private static void handleComment(String comment, Node n) {
		if (comment == null || comment.isEmpty()) return;

		if (n.getComment().isPresent()) {
			Comment c = n.getComment().get();

			if (c.isLineComment()) {
				c = new BlockComment(c.getContent());
				n.setComment(c);
			}

			c.setContent("\n * "+comment.replace("\n", "\n * ")+'\n'+c.getContent());
		} else {
			n.setComment(new BlockComment("\n * "+comment.replace("\n", "\n * ")+"\n "));
		}
	}

	private static class Context {
		public Context(IClassEnv env, boolean mapped) {
			this.env = env;
			this.mapped = mapped;
		}

		public ClassInstance getClsByName(String name) {
			return mapped ? env.getClsByMappedName(name) : env.getClsByName(name);
		}

		public String getName(IMatchable<?> e) {
			return mapped ? e.getMappedName(true) : e.getName();
		}

		final IClassEnv env;
		final boolean mapped;
		String pkg;
		Map<String, String> imports = new HashMap<>();
		List<String> wildcardImports = new ArrayList<>();
		ClassInstance cls;
	}

	private static final VoidVisitorAdapter<Context> remapVisitor = new VoidVisitorAdapter<Context>() {
		@Override
		public void visit(CompilationUnit n, Context arg) {
			if (n.getPackageDeclaration().isPresent()) {
				String pkg = n.getPackageDeclaration().get().getNameAsString();
				arg.pkg = pkg;
				arg.wildcardImports.add(pkg.replace('.', '/')+'/'); // TODO: verify ordering
			} else {
				arg.wildcardImports.add("");
			}

			arg.wildcardImports.add("java/lang/");

			for (ImportDeclaration imp : n.getImports()) {
				if (imp.isStatic()) continue;

				if (imp.isAsterisk()) {
					arg.wildcardImports.add(imp.getNameAsString().replace('.', '/')+'/');
				} else {
					String name = imp.getNameAsString();
					int pos = name.lastIndexOf('.');

					if (pos != -1) arg.imports.put(name.substring(pos + 1), name.replace('.', '/'));
				}
			}

			n.getTypes().forEach(p -> p.accept(this, arg));

			arg.pkg = null;
			arg.imports.clear();
			arg.wildcardImports.clear();
		}

		@Override
		public void visit(ClassOrInterfaceDeclaration n, Context arg) {
			visitCls(n, arg);
		}

		@Override
		public void visit(EnumDeclaration n, Context arg) {
			visitCls(n, arg);
		}

		private void visitCls(TypeDeclaration<?> n, Context arg) {
			String name = n.getName().getIdentifier();
			if (arg.pkg != null) name = arg.pkg+"."+name;

			ClassInstance cls = arg.getClsByName(name.replace('.', '/'));
			System.out.println("cls "+n.getName().getIdentifier()+" = "+cls+" at "+n.getRange());

			handleComment(cls.getMappedComment(), n);

			ClassInstance prev = arg.cls;
			arg.cls = cls;

			n.getMembers().forEach(p -> p.accept(clsRemapVisitor, arg));

			arg.cls = prev;
		}

		@Override
		public void visit(MethodDeclaration n, Context arg) {
			throw new IllegalStateException();
		}

		@Override
		public void visit(FieldDeclaration n, Context arg) {
			throw new IllegalStateException();
		}

		/*@Override
		public void visit(ClassOrInterfaceType n, Context arg) {
			ClassInstance cls = arg.env.getLocalClsById(toDesc(n));

			if (cls != null && cls.getMappedName() != null) {
				String mapped = cls.getMappedName();
				int pos = mapped.lastIndexOf('/');

				n.getName().setIdentifier(mapped.substring(pos + 1));

				if (pos != -1) {
					// TODO
				}
			}
		}*/
	};

	private static final VoidVisitorAdapter<Context> clsRemapVisitor = new VoidVisitorAdapter<Context>() {
		@Override
		public void visit(ClassOrInterfaceDeclaration n, Context arg) {
			throw new IllegalStateException();
		}

		@Override
		public void visit(MethodDeclaration n, Context arg) {
			StringBuilder sb = new StringBuilder();
			sb.append('(');

			for (Parameter p : n.getParameters()) {
				sb.append(toDesc(p.getType(), arg));
			}

			sb.append(')');
			sb.append(toDesc(n.getType(), arg));

			String name = n.getName().getIdentifier();
			String desc = sb.toString();
			MethodInstance m = arg.mapped ? arg.cls.getMappedMethod(name, desc) : arg.cls.getMethod(name, desc);

			System.out.println("mth "+n.getName().getIdentifier()+" = "+m+" at "+n.getRange());

			if (m != null) {
				/*if (m.hasMappedName()) {
					n.getName().setIdentifier(m.getMappedName());
				}*/

				handleComment(m.getMappedComment(), n);
			} else {
				System.out.println("(not found)");
			}

			n.getBody().ifPresent(l -> l.accept(this, arg));
			/*n.getType().accept(this, arg);
			n.getParameters().forEach(p -> p.accept(this, arg));
			n.getThrownExceptions().forEach(p -> p.accept(this, arg));
			n.getTypeParameters().forEach(p -> p.accept(this, arg));
			n.getAnnotations().forEach(p -> p.accept(this, arg));*/
		}

		@Override
		public void visit(FieldDeclaration n, Context arg) {
			List<String> comments = null;

			for (VariableDeclarator v : n.getVariables()) {
				String name = v.getName().getIdentifier();
				String desc = toDesc(v.getType(), arg);
				FieldInstance f = arg.mapped ? arg.cls.getMappedField(name, desc) : arg.cls.getField(name, desc);

				System.out.println("fld "+v.getName().getIdentifier()+" = "+f+" at "+v.getRange());

				if (f != null) {
					/*if (f.hasMappedName()) {
						v.getName().setIdentifier(f.getMappedName());
					}*/

					if (f.getMappedComment() != null) {
						if (comments == null) comments = new ArrayList<>();
						comments.add(f.getMappedComment());
					}
				} else {
					System.out.println("(not found)");
				}
			}

			if (comments != null) {
				for (int i = comments.size() - 1; i >= 0; i--) {
					handleComment(comments.get(i), n);
				}
			}

			/*n.getVariables().forEach(p -> p.accept(this, arg));
			n.getAnnotations().forEach(p -> p.accept(this, arg));*/
		}

		/*@Override
		public void visit(MethodCallExpr n, Context arg) {
			ClassInstance cls;

			if (!n.getScope().isPresent()) {
				cls = arg.cls;
			} else {
				Expression expr = n.getScope().get();

				if (expr instanceof NameExpr) {
					String name = ((NameExpr) expr).getName().getIdentifier();
					// TODO: check if local var or field
					cls = arg.env.getLocalClsByName(name);
				} else {
					throw new UnsupportedOperationException();
				}

			}

			n.getArguments().forEach(p -> p.accept(this, arg));
			n.getName().accept(this, arg);
			n.getScope().ifPresent(l -> l.accept(this, arg));
			n.getTypeArguments().ifPresent(l -> l.forEach(v -> v.accept(this, arg)));
			n.getComment().ifPresent(l -> l.accept(this, arg));
		}*/

		/*@Override
		public void visit(ClassOrInterfaceType n, Context arg) {
			remapVisitor.visit(n, arg);
		}*/
	};

	/*private static class EnvTypeSolver implements TypeSolver {
		EnvTypeSolver(IClassEnv env) {
			this.env = env;
		}

		@Override
		public SymbolReference<ReferenceTypeDeclaration> tryToSolveType(String name) {
			ClassInstance cls = env.getClsByName(name.replace('.', '/'));

			if (cls != null) {
				return SymbolReference.solved(MappedDeclaration.get(cls, this));
			} else {
				return SymbolReference.unsolved(ReferenceTypeDeclaration.class);
			}
		}

		@Override
		public void setParent(TypeSolver parent) {
			this.parent = parent;
		}

		@Override
		public TypeSolver getParent() {
			return parent;
		}

		private final IClassEnv env;
		private TypeSolver parent;
	};*/

	private static class Printer extends PrettyPrintVisitor {
		public Printer(PrettyPrinterConfiguration prettyPrinterConfiguration) {
			super(prettyPrinterConfiguration);
		}

		@Override
		public void visit(BlockComment n, Void arg) {
			if (configuration.isIgnoreComments()) return;

			printer.print("/*");
			printMultiLine(n.getContent());
			printer.println("*/");
		}

		@Override
		public void visit(JavadocComment n, Void arg) {
			if (configuration.isIgnoreComments()) return;

			printer.print("/**");
			printMultiLine(n.getContent());
			printer.println("*/");
		}

		private void printMultiLine(String str) {
			int startPos = 0;
			int pos;

			while ((pos = str.indexOf('\n', startPos)) != -1) {
				printer.print(str.substring(startPos, pos));
				printer.println();
				startPos = pos + 1;
			}

			if (startPos < str.length()) printer.print(str.substring(startPos));
		}

		@Override
		public void visit(final ClassOrInterfaceDeclaration n, final Void arg) {
			printJavaComment(n.getComment(), arg);
			printMemberAnnotations(n.getAnnotations(), arg);
			printModifiers(n.getModifiers());

			if (n.isInterface()) {
				printer.print("interface ");
			} else {
				printer.print("class ");
			}

			n.getName().accept(this, arg);

			printTypeParameters(n.getTypeParameters(), arg);

			if (!n.getExtendedTypes().isEmpty()) {
				printer.print(" extends ");
				for (final Iterator<ClassOrInterfaceType> i = n.getExtendedTypes().iterator(); i.hasNext(); ) {
					final ClassOrInterfaceType c = i.next();
					c.accept(this, arg);
					if (i.hasNext()) {
						printer.print(", ");
					}
				}
			}

			if (!n.getImplementedTypes().isEmpty()) {
				printer.print(" implements ");
				for (final Iterator<ClassOrInterfaceType> i = n.getImplementedTypes().iterator(); i.hasNext(); ) {
					final ClassOrInterfaceType c = i.next();
					c.accept(this, arg);
					if (i.hasNext()) {
						printer.print(", ");
					}
				}
			}

			printer.println(" {");
			printer.indent();
			if (!isNullOrEmpty(n.getMembers())) {
				printMembers(n.getMembers(), arg);
			}

			printOrphanCommentsEnding(n);

			printer.unindent();
			printer.print("}");
		}

		@Override
		public void visit(final EnumDeclaration n, final Void arg) {
			printJavaComment(n.getComment(), arg);
			printMemberAnnotations(n.getAnnotations(), arg);
			printModifiers(n.getModifiers());

			printer.print("enum ");
			n.getName().accept(this, arg);

			if (!n.getImplementedTypes().isEmpty()) {
				printer.print(" implements ");
				for (final Iterator<ClassOrInterfaceType> i = n.getImplementedTypes().iterator(); i.hasNext(); ) {
					final ClassOrInterfaceType c = i.next();
					c.accept(this, arg);
					if (i.hasNext()) {
						printer.print(", ");
					}
				}
			}

			printer.println(" {");
			printer.indent();
			if (n.getEntries() != null) {
				for (final Iterator<EnumConstantDeclaration> i = n.getEntries().iterator(); i.hasNext(); ) {
					final EnumConstantDeclaration e = i.next();
					e.accept(this, arg);
					if (i.hasNext()) {
						printer.println(",");
					}
				}
			}
			if (!n.getMembers().isEmpty()) {
				printer.println(";");
				printer.println();
				printMembers(n.getMembers(), arg);
			} else {
				if (!n.getEntries().isEmpty()) {
					printer.println();
				}
			}
			printer.unindent();
			printer.print("}");
		}

		@Override
		public void visit(final DoStmt n, final Void arg) {
			if (canAddNewLine(n)) printer.println();

			printJavaComment(n.getComment(), arg);
			printer.print("do ");
			n.getBody().accept(this, arg);
			printer.print(" while (");
			n.getCondition().accept(this, arg);
			printer.print(");");

			if (getNext(n) != null) printer.println();
		}

		@Override
		public void visit(final ForStmt n, final Void arg) {
			if (canAddNewLine(n)) printer.println();

			printJavaComment(n.getComment(), arg);
			printer.print("for (");
			if (n.getInitialization() != null) {
				for (final Iterator<Expression> i = n.getInitialization().iterator(); i.hasNext(); ) {
					final Expression e = i.next();
					e.accept(this, arg);
					if (i.hasNext()) {
						printer.print(", ");
					}
				}
			}
			printer.print("; ");
			if (n.getCompare().isPresent()) {
				n.getCompare().get().accept(this, arg);
			}
			printer.print("; ");
			if (n.getUpdate() != null) {
				for (final Iterator<Expression> i = n.getUpdate().iterator(); i.hasNext(); ) {
					final Expression e = i.next();
					e.accept(this, arg);
					if (i.hasNext()) {
						printer.print(", ");
					}
				}
			}
			printer.print(") ");
			n.getBody().accept(this, arg);

			if (getNext(n) != null) printer.println();
		}

		@Override
		public void visit(final ForeachStmt n, final Void arg) {
			if (canAddNewLine(n)) printer.println();

			printJavaComment(n.getComment(), arg);
			printer.print("for (");
			n.getVariable().accept(this, arg);
			printer.print(" : ");
			n.getIterable().accept(this, arg);
			printer.print(") ");
			n.getBody().accept(this, arg);

			if (getNext(n) != null) printer.println();
		}

		@Override
		public void visit(final IfStmt n, final Void arg) {
			boolean thenBlock = n.getThenStmt() instanceof BlockStmt;

			while (thenBlock
					&& !n.getElseStmt().isPresent()
					&& ((BlockStmt) n.getThenStmt()).getStatements().size() == 1
					&& !(n.getParentNode().orElse(null) instanceof IfStmt)) {
				Statement stmt = ((BlockStmt) n.getThenStmt()).getStatements().get(0);
				if (isBlockStmt(stmt) && !(stmt instanceof BlockStmt)) break;

				n.setThenStmt(stmt);
				thenBlock = n.getThenStmt() instanceof BlockStmt;
			}

			Node prev = getPrev(n);

			if (thenBlock
					&& (canAddNewLine(n) || prev instanceof IfStmt && !((IfStmt) prev).hasThenBlock())
					&& !(n.getParentNode().orElse(null) instanceof IfStmt)) {
				printer.println();
			}

			printJavaComment(n.getComment(), arg);

			printer.print("if (");
			n.getCondition().accept(this, arg);
			printer.print(") ");

			n.getThenStmt().accept(this, arg);

			Node next = getNext(n);

			if (n.getElseStmt().isPresent()) {
				Statement elseStmt = n.getElseStmt().get();

				if (thenBlock)
					printer.print(" ");
				else
					printer.println();
				final boolean elseIf = elseStmt instanceof IfStmt;
				final boolean elseBlock = elseStmt instanceof BlockStmt;
				if (elseIf || elseBlock) // put chained if and start of block statement on a same level
					printer.print("else ");
				else {
					printer.println("else");
					printer.indent();
				}
				elseStmt.accept(this, arg);
				if (!(elseIf || elseBlock))
					printer.unindent();

				if (next != null) printer.println();
			} else {
				if (next != null && (thenBlock || !(next instanceof IfStmt))) printer.println();
			}
		}

		@Override
		public void visit(final SwitchStmt n, final Void arg) {
			if (canAddNewLine(n)) printer.println();

			printJavaComment(n.getComment(), arg);
			printer.print("switch (");
			n.getSelector().accept(this, arg);
			printer.println(") {");

			if (n.getEntries() != null) {
				for (final SwitchEntryStmt e : n.getEntries()) {
					e.accept(this, arg);
				}
			}

			printer.print("}");

			if (getNext(n) != null) printer.println();
		}

		@Override
		public void visit(final SwitchEntryStmt n, final Void arg) {
			printJavaComment(n.getComment(), arg);

			if (n.getLabel().isPresent()) {
				printer.print("case ");
				n.getLabel().get().accept(this, arg);
				printer.print(":");
			} else {
				printer.print("default:");
			}

			if (n.getStatements() != null
					&& n.getStatements().size() == 1
					&& n.getStatements().get(0) instanceof BlockStmt) {
				printer.print(" ");
				n.getStatements().get(0).accept(this, arg);
				printer.println();
			} else {
				printer.println();

				if (n.getStatements() != null) {
					printer.indent();

					for (final Statement s : n.getStatements()) {
						s.accept(this, arg);
						printer.println();
					}

					printer.unindent();
				}
			}
		}

		@Override
		public void visit(final TryStmt n, final Void arg) {
			if (canAddNewLine(n)) printer.println();

			printJavaComment(n.getComment(), arg);
			printer.print("try ");
			if (!n.getResources().isEmpty()) {
				printer.print("(");
				Iterator<Expression> resources = n.getResources().iterator();
				boolean first = true;
				while (resources.hasNext()) {
					resources.next().accept(this, arg);
					if (resources.hasNext()) {
						printer.print(";");
						printer.println();
						if (first) {
							printer.indent();
						}
					}
					first = false;
				}
				if (n.getResources().size() > 1) {
					printer.unindent();
				}
				printer.print(") ");
			}
			n.getTryBlock().accept(this, arg);
			for (final CatchClause c : n.getCatchClauses()) {
				c.accept(this, arg);
			}
			if (n.getFinallyBlock().isPresent()) {
				printer.print(" finally ");
				n.getFinallyBlock().get().accept(this, arg);
			}

			if (getNext(n) != null) printer.println();
		}

		@Override
		public void visit(final WhileStmt n, final Void arg) {
			if (canAddNewLine(n)) printer.println();

			printJavaComment(n.getComment(), arg);
			printer.print("while (");
			n.getCondition().accept(this, arg);
			printer.print(") ");
			n.getBody().accept(this, arg);

			if (getNext(n) != null) printer.println();
		}

		private static boolean canAddNewLine(Node n) {
			Node prev = getPrev(n);

			return prev != null && !isBlockStmt(prev);
		}

		private static boolean isBlockStmt(Node n) {
			return n instanceof BlockStmt
					|| n instanceof DoStmt
					|| n instanceof ForStmt
					|| n instanceof ForeachStmt
					|| n instanceof IfStmt
					|| n instanceof SwitchStmt
					|| n instanceof TryStmt
					|| n instanceof WhileStmt;
		}

		private static Node getPrev(Node n) {
			Node parent = n.getParentNode().orElse(null);
			if (parent == null) return null;

			int idx = parent.getChildNodes().indexOf(n);
			if (idx == 0) return null;

			return parent.getChildNodes().get(idx - 1);
		}

		private static Node getNext(Node n) {
			Node parent = n.getParentNode().orElse(null);
			if (parent == null) return null;

			int idx = parent.getChildNodes().indexOf(n);
			if (idx == parent.getChildNodes().size() - 1) return null;

			return parent.getChildNodes().get(idx + 1);
		}

		private void printModifiers(final EnumSet<Modifier> modifiers) {
			for (Modifier m : modifiers) {
				printer.print(m.asString());
				printer.print(" ");
			}
		}

		private void printMembers(final NodeList<BodyDeclaration<?>> members, final Void arg) {
			BodyDeclaration<?> prev = null;

			members.sort((a, b) -> {
				if (a instanceof FieldDeclaration && b instanceof CallableDeclaration) {
					return 1;
				} else if (b instanceof FieldDeclaration && a instanceof CallableDeclaration) {
					return -1;
				} else if (a instanceof MethodDeclaration && !((MethodDeclaration) a).getModifiers().contains(Modifier.STATIC) && b instanceof ConstructorDeclaration) {
					return 1;
				} else if (b instanceof MethodDeclaration && !((MethodDeclaration) b).getModifiers().contains(Modifier.STATIC) && a instanceof ConstructorDeclaration) {
					return -1;
				} else {
					return 0;
				}
			});

			for (final BodyDeclaration<?> member : members) {
				if (prev != null && (!prev.isFieldDeclaration() || !member.isFieldDeclaration())) printer.println();
				member.accept(this, arg);
				printer.println();

				prev = member;
			}
		}

		private void printMemberAnnotations(final NodeList<AnnotationExpr> annotations, final Void arg) {
			if (annotations.isEmpty()) {
				return;
			}
			for (final AnnotationExpr a : annotations) {
				a.accept(this, arg);
				printer.println();
			}
		}

		private void printAnnotations(final NodeList<AnnotationExpr> annotations, boolean prefixWithASpace,
				final Void arg) {
			if (annotations.isEmpty()) {
				return;
			}
			if (prefixWithASpace) {
				printer.print(" ");
			}
			for (AnnotationExpr annotation : annotations) {
				annotation.accept(this, arg);
				printer.print(" ");
			}
		}

		private void printTypeArgs(final NodeWithTypeArguments<?> nodeWithTypeArguments, final Void arg) {
			NodeList<Type> typeArguments = nodeWithTypeArguments.getTypeArguments().orElse(null);
			if (!isNullOrEmpty(typeArguments)) {
				printer.print("<");
				for (final Iterator<Type> i = typeArguments.iterator(); i.hasNext(); ) {
					final Type t = i.next();
					t.accept(this, arg);
					if (i.hasNext()) {
						printer.print(", ");
					}
				}
				printer.print(">");
			}
		}

		private void printTypeParameters(final NodeList<TypeParameter> args, final Void arg) {
			if (!isNullOrEmpty(args)) {
				printer.print("<");
				for (final Iterator<TypeParameter> i = args.iterator(); i.hasNext(); ) {
					final TypeParameter t = i.next();
					t.accept(this, arg);
					if (i.hasNext()) {
						printer.print(", ");
					}
				}
				printer.print(">");
			}
		}

		private void printArguments(final NodeList<Expression> args, final Void arg) {
			printer.print("(");
			Position cursorRef = printer.getCursor();
			if (!isNullOrEmpty(args)) {
				for (final Iterator<Expression> i = args.iterator(); i.hasNext(); ) {
					final Expression e = i.next();
					e.accept(this, arg);
					if (i.hasNext()) {
						printer.print(",");
						if (configuration.isColumnAlignParameters()) {
							printer.wrapToColumn(cursorRef.column);
						} else {
							printer.print(" ");
						}
					}
				}
			}
			printer.print(")");
		}

		private void printPrePostFixOptionalList(final NodeList<? extends Visitable> args, final Void arg, String prefix, String separator, String postfix) {
			if (!args.isEmpty()) {
				printer.print(prefix);
				for (final Iterator<? extends Visitable> i = args.iterator(); i.hasNext(); ) {
					final Visitable v = i.next();
					v.accept(this, arg);
					if (i.hasNext()) {
						printer.print(separator);
					}
				}
				printer.print(postfix);
			}
		}

		private void printPrePostFixRequiredList(final NodeList<? extends Visitable> args, final Void arg, String prefix, String separator, String postfix) {
			printer.print(prefix);
			if (!args.isEmpty()) {
				for (final Iterator<? extends Visitable> i = args.iterator(); i.hasNext(); ) {
					final Visitable v = i.next();
					v.accept(this, arg);
					if (i.hasNext()) {
						printer.print(separator);
					}
				}
			}
			printer.print(postfix);
		}

		private void printJavaComment(final Optional<Comment> javacomment, final Void arg) {
			if (configuration.isPrintJavaDoc()) {
				javacomment.ifPresent(c -> c.accept(this, arg));
			}
		}

		private void printOrphanCommentsBeforeThisChildNode(final Node node) {
			if (configuration.isIgnoreComments()) return;
			if (node instanceof Comment) return;

			Node parent = node.getParentNode().orElse(null);
			if (parent == null) return;
			List<Node> everything = new LinkedList<>();
			everything.addAll(parent.getChildNodes());
			sortByBeginPosition(everything);
			int positionOfTheChild = -1;
			for (int i = 0; i < everything.size(); i++) {
				if (everything.get(i) == node) positionOfTheChild = i;
			}
			if (positionOfTheChild == -1) {
				throw new AssertionError("I am not a child of my parent.");
			}
			int positionOfPreviousChild = -1;
			for (int i = positionOfTheChild - 1; i >= 0 && positionOfPreviousChild == -1; i--) {
				if (!(everything.get(i) instanceof Comment)) positionOfPreviousChild = i;
			}
			for (int i = positionOfPreviousChild + 1; i < positionOfTheChild; i++) {
				Node nodeToPrint = everything.get(i);
				if (!(nodeToPrint instanceof Comment))
					throw new RuntimeException(
							"Expected comment, instead " + nodeToPrint.getClass() + ". Position of previous child: "
									+ positionOfPreviousChild + ", position of child " + positionOfTheChild);
				nodeToPrint.accept(this, null);
			}
		}

		private void printOrphanCommentsEnding(final Node node) {
			if (configuration.isIgnoreComments()) return;

			List<Node> everything = new LinkedList<>();
			everything.addAll(node.getChildNodes());
			sortByBeginPosition(everything);
			if (everything.isEmpty()) {
				return;
			}

			int commentsAtEnd = 0;
			boolean findingComments = true;
			while (findingComments && commentsAtEnd < everything.size()) {
				Node last = everything.get(everything.size() - 1 - commentsAtEnd);
				findingComments = (last instanceof Comment);
				if (findingComments) {
					commentsAtEnd++;
				}
			}
			for (int i = 0; i < commentsAtEnd; i++) {
				everything.get(everything.size() - commentsAtEnd + i).accept(this, null);
			}
		}
	}
}
