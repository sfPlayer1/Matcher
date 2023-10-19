package matcher.gui.srcprocess;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import com.github.javaparser.JavaToken.Kind;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.Position;
import com.github.javaparser.Problem;
import com.github.javaparser.Range;
import com.github.javaparser.TokenRange;
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

import matcher.Matcher;
import matcher.model.NameType;
import matcher.model.type.ClassInstance;
import matcher.model.type.FieldInstance;
import matcher.model.type.MethodInstance;
import matcher.model.type.MethodVarInstance;

public class SrcDecorator {
	public static String decorate(String src, ClassInstance cls, NameType nameType) {
		String name = cls.getName(nameType);

		if (cls.getOuterClass() != null && name.contains("$")) {
			// replace <outer>.<inner> with <outer>$<inner> since . is not a legal identifier within class names and thus gets rejected by JavaParser

			int nameStartPos = Math.max(0, name.lastIndexOf('/') + 1);
			List<String> classNames = new ArrayList<>();
			boolean firstDollar = true;

			name = name.substring(nameStartPos, name.length());

			for (int i = 0; i < name.length(); i++) {
				char ch = name.charAt(i);

				if (ch == '$') {
					if (firstDollar) {
						firstDollar = false;
						continue;
					}

					classNames.add(name.substring(0, i));
				}
			}

			classNames.add(name.substring(0, name.length()));

			for (int i = classNames.size() - 1; i >= 0; i--) {
				src = src.replace(classNames.get(i).replace('$', '.'), classNames.get(i));
			}
		}

		JavaParser parser = new JavaParser(new ParserConfiguration()
				.setLanguageLevel(LanguageLevel.RAW));

		ParseResult<CompilationUnit> result = parser.parse(src);
		CompilationUnit cu;

		if (result.isSuccessful()) {
			cu = result.getResult().orElseThrow();
		} else {
			String fixedSrc = tryFixCodeFormat(src, result.getProblems());

			if (fixedSrc == null) {
				throw new SrcParseException(result.getProblems(), src);
			}

			ParseResult<CompilationUnit> fixedResult = parser.parse(fixedSrc);

			if (!fixedResult.isSuccessful()) {
				throw new SrcParseException(fixedResult.getProblems(), fixedSrc);
			} else {
				cu = fixedResult.getResult().orElseThrow();
			}
		}

		TypeResolver resolver = new TypeResolver();
		resolver.setup(cls, nameType, cu);

		cu.accept(remapVisitor, resolver);

		HtmlPrinter printer = new HtmlPrinter(resolver);
		cu.accept(printer, null);

		return printer.toString();
	}

	public static class SrcParseException extends RuntimeException {
		SrcParseException(List<Problem> problems, String source) {
			super("Parsing failed: "+problems);

			this.problems = problems.stream().map(Problem::toString).collect(Collectors.joining(System.lineSeparator()));
			this.source = source;
		}

		private static final long serialVersionUID = 6164216517595646716L;

		public final String problems;
		public final String source;
	}

	private static String tryFixCodeFormat(String src, List<Problem> problems) {
		for (Problem problem : problems) {
			// CFR will insert super statements in inner classes after any captured locals, which crashes JavaParser
			// We can move the super statements around so there's no crash, so long as we can find what to move where
			if (!problem.getMessage().startsWith("Parse error. Found \"super\"")
					|| !problem.getLocation().isPresent()) {
				return null;
			}

			TokenRange range = problem.getLocation().get();
			JavaToken start = range.getBegin();

			while (start.getKind() != Kind.SUPER.getKind()) {
				// If we can't find the super token for whatever reason no fixing can be done
				if (!start.getNextToken().isPresent()) {
					return null;
				}

				start = start.getNextToken().get();
			}

			JavaToken end = start;

			do {
				// If we can't find the end of the super statement
				if (!end.getNextToken().isPresent()) {
					return null;
				}

				end = end.getNextToken().get();
			} while (end.getKind() != Kind.SEMICOLON.getKind());

			JavaToken to = range.getBegin();

			while (to.getKind() != Kind.LBRACE.getKind()) {
				// If we can't find the method header the statement is in
				if (!to.getPreviousToken().isPresent()) {
					return null;
				}

				to = to.getPreviousToken().get();
			}

			// Unpack the limits of each statement so it's clear what needs to move in the source
			if (!to.getRange().isPresent()
					|| !start.getRange().isPresent()
					|| !end.getRange().isPresent()) {
				return null;
			}

			src = moveStatement(src, Range.range(start.getRange().get().begin, end.getRange().get().end), to.getRange().get().end);
		}

		return src;
	}

	private static String moveStatement(String source, Range slice, Position to) {
		Matcher.LOGGER.debug("Shifting " + slice + " to " + to);

		//Remember that lines are counted from 1 not 0, so the indexes have to be offset backwards
		List<String> lines = new BufferedReader(new StringReader(source)).lines().collect(Collectors.toList());
		String sliceLine;

		if (slice.begin.line != slice.end.line) {
			String sliceStart = lines.get(slice.begin.line - 1);
			StringBuilder insert = new StringBuilder(sliceStart.substring(slice.begin.column - 1));

			for (int i = slice.begin.line, end = slice.end.line - 1; i < end; i++) {
				insert.append(lines.get(i)).append(System.lineSeparator());
			}

			String sliceEnd = lines.get(slice.end.line - 1);
			sliceLine = insert.append(sliceEnd.substring(0, slice.end.column)).toString();
		} else {
			sliceLine = lines.get(slice.begin.line - 1).substring(slice.begin.column - 1, slice.end.column);
		}

		sliceLine = sliceLine.trim();
		StringBuilder rebuiltSource = new StringBuilder();

		for (int i = 0, end = to.line; i < end; i++) {
			rebuiltSource.append(lines.get(i)).append(System.lineSeparator());
		}

		rebuiltSource.append(sliceLine).append(" //Matcher moved").append(System.lineSeparator());

		for (int i = to.line, end = slice.begin.line - 1; i < end; i++) {
			rebuiltSource.append(lines.get(i)).append(System.lineSeparator());
		}

		rebuiltSource.append("/* ").append(sliceLine).append(" */");

		for (int i = slice.end.line, end = lines.size(); i < end; i++) {
			rebuiltSource.append(System.lineSeparator()).append(lines.get(i));
		}

		return rebuiltSource.toString();
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
			// Matcher.LOGGER.debug("cls {} = {} at {}", n.getName().getIdentifier(), cls, n.getRange());

			if (cls != null) {
				handleComment(cls.getMappedComment(), n);
			}

			n.getMembers().forEach(p -> p.accept(this, resolver));
		}

		@Override
		public void visit(ConstructorDeclaration n, TypeResolver resolver) {
			MethodInstance m = resolver.getMethod(n);
			// Matcher.LOGGER.debug("ctor {} = {} at {}", n.getName().getIdentifier(), m, n.getRange());

			if (m != null) {
				handleMethodComment(m, n, resolver);
			}

			n.getBody().accept(this, resolver);

			/*	n.getName().accept(this, arg);
				n.getParameters().forEach(p -> p.accept(this, arg));
				n.getThrownExceptions().forEach(p -> p.accept(this, arg));
				n.getTypeParameters().forEach(p -> p.accept(this, arg));
				n.getAnnotations().forEach(p -> p.accept(this, arg));
				n.getComment().ifPresent(l -> l.accept(this, arg));
			*/
		}

		@Override
		public void visit(MethodDeclaration n, TypeResolver resolver) {
			MethodInstance m = resolver.getMethod(n);
			// Matcher.LOGGER.debug("mth {}, = {} at {}", n.getName().getIdentifier(), m, n.getRange());

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
				// Matcher.LOGGER.debug("fld {} = {} at {}", v.getName().getIdentifier(), f, v.getRange());

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
}
