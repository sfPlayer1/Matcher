package matcher.srcprocess;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import matcher.NameType;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class SrcDecorator {
	private static void setup() {
		JavaParser.getStaticConfiguration().setLanguageLevel(LanguageLevel.RAW);
	}

	public static String decorate(String src, ClassInstance cls, NameType nameType) {
		if (cls.getOuterClass() != null) {
			// replace <outer>.<inner> with <outer>$<inner> since . is not a legal identifier within class names and thus gets rejected by JavaParser

			String name = cls.getName(nameType);
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
			throw new SrcParseException(src, e);
		}

		TypeResolver resolver = new TypeResolver();
		resolver.setup(cls, nameType, cu);

		cu.accept(remapVisitor, resolver);

		HtmlPrinter printer = new HtmlPrinter(resolver);
		cu.accept(printer, null);

		return printer.getSource();
	}

	public static class SrcParseException extends RuntimeException {
		public SrcParseException(String source, Exception cause) {
			super("Parsing failed", cause);

			this.source = source;
		}

		private static final long serialVersionUID = 6164216517595646716L;

		public final String source;
	}

	private static void handleComment(String comment, Node n) {
		if (comment == null || comment.isEmpty()) return;

		if (n.getComment().isPresent()) {
			Comment c = n.getComment().get();

			if (c.isLineComment()) {
				c = new JavadocComment(c.getContent());
				n.setComment(c);
			}

			c.setContent("\n * "+comment.replace("\n", "\n * ")+'\n'+c.getContent());
		} else {
			n.setComment(new JavadocComment("\n * "+comment.replace("\n", "\n * ")+"\n "));
		}
	}

	private static void handleMethodComment(MethodInstance method, Node n, TypeResolver resolver) {
		String comment = method.getMappedComment();
		StringBuilder argComments = null;

		for (MethodVarInstance arg : method.getArgs()) {
			String argComment = arg.getMappedComment();

			if (argComment != null) {
				if (argComments == null) argComments = new StringBuilder();

				argComments.append("@param ");
				argComments.append(resolver.getName(arg));
				argComments.append(' ');
				argComments.append(argComment.replace("\n", "\n  "));
				argComments.append('\n');
			}
		}

		if (argComments != null) {
			int pos;

			if (comment == null) {
				comment = argComments.toString();
			} else if ((pos = comment.indexOf("@return")) >= 0) {
				comment = comment.substring(0, pos) + argComments + comment.substring(pos);
			} else {
				comment = comment + '\n' + argComments.subSequence(0, argComments.length() - 1); // trailing \n to leading
			}
		}

		handleComment(comment, n);
	}

	private static final VoidVisitorAdapter<TypeResolver> remapVisitor = new VoidVisitorAdapter<TypeResolver>() {
		@Override
		public void visit(CompilationUnit n, TypeResolver resolver) {
			n.getTypes().forEach(p -> p.accept(this, resolver));
		}

		@Override
		public void visit(ClassOrInterfaceDeclaration n, TypeResolver resolver) {
			visitCls(n, resolver);
		}

		@Override
		public void visit(EnumDeclaration n, TypeResolver resolver) {
			visitCls(n, resolver);
		}

		private void visitCls(TypeDeclaration<?> n, TypeResolver resolver) {
			ClassInstance cls = resolver.getCls(n);
			//System.out.println("cls "+n.getName().getIdentifier()+" = "+cls+" at "+n.getRange());

			if (cls != null) {
				handleComment(cls.getMappedComment(), n);
			}

			n.getMembers().forEach(p -> p.accept(this, resolver));
		}

		@Override
		public void visit(ConstructorDeclaration n, TypeResolver resolver) {
			MethodInstance m = resolver.getMethod(n);
			//System.out.println("ctor "+n.getName().getIdentifier()+" = "+m+" at "+n.getRange());

			if (m != null) {
				handleMethodComment(m, n, resolver);
			}

			n.getBody().accept(this, resolver);
			/*n.getName().accept(this, arg);
	        n.getParameters().forEach(p -> p.accept(this, arg));
	        n.getThrownExceptions().forEach(p -> p.accept(this, arg));
	        n.getTypeParameters().forEach(p -> p.accept(this, arg));
	        n.getAnnotations().forEach(p -> p.accept(this, arg));
	        n.getComment().ifPresent(l -> l.accept(this, arg));*/
		}

		@Override
		public void visit(MethodDeclaration n, TypeResolver resolver) {
			MethodInstance m = resolver.getMethod(n);
			//System.out.println("mth "+n.getName().getIdentifier()+" = "+m+" at "+n.getRange());

			if (m != null) {
				handleMethodComment(m, n, resolver);
			}

			n.getBody().ifPresent(l -> l.accept(this, resolver));
			/*n.getType().accept(this, arg);
			n.getParameters().forEach(p -> p.accept(this, arg));
			n.getThrownExceptions().forEach(p -> p.accept(this, arg));
			n.getTypeParameters().forEach(p -> p.accept(this, arg));
			n.getAnnotations().forEach(p -> p.accept(this, arg));*/
		}

		@Override
		public void visit(FieldDeclaration n, TypeResolver resolver) {
			List<String> comments = null;

			for (VariableDeclarator var : n.getVariables()) {
				FieldInstance f = resolver.getField(var);
				//System.out.println("fld "+v.getName().getIdentifier()+" = "+f+" at "+v.getRange());

				if (f != null) {
					if (f.getMappedComment() != null) {
						if (comments == null) comments = new ArrayList<>();
						comments.add(f.getMappedComment());
					}
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
	};

	static {
		setup();
	}
}
