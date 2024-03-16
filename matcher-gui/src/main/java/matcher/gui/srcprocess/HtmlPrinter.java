/*
 * Most of this file is copied from DefaultPrettyPrinterVisitor (commit 19e0559),
 * tweaked to output HTML instead of plain text. Original license:
 *
 * Copyright (C) 2011, 2013-2021 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */

package matcher.gui.srcprocess;

import static com.github.javaparser.utils.PositionUtils.sortByBeginPosition;
import static com.github.javaparser.utils.Utils.isNullOrEmpty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.modules.ModuleDeclaration;
import com.github.javaparser.ast.modules.ModuleExportsDirective;
import com.github.javaparser.ast.modules.ModuleOpensDirective;
import com.github.javaparser.ast.modules.ModuleProvidesDirective;
import com.github.javaparser.ast.modules.ModuleRequiresDirective;
import com.github.javaparser.ast.modules.ModuleUsesDirective;
import com.github.javaparser.ast.nodeTypes.NodeWithTraversableScope;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeArguments;
import com.github.javaparser.ast.nodeTypes.NodeWithVariables;
import com.github.javaparser.ast.nodeTypes.SwitchNode;
import com.github.javaparser.ast.stmt.AssertStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.stmt.YieldStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.type.VarType;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.type.WildcardType;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.ConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption;
import com.github.javaparser.printer.configuration.Indentation;
import com.github.javaparser.printer.configuration.Indentation.IndentType;
import com.github.javaparser.utils.Utils;

import matcher.model.type.FieldInstance;
import matcher.model.type.MethodInstance;

public class HtmlPrinter extends DefaultPrettyPrinterVisitor {
	public HtmlPrinter(TypeResolver typeResolver) {
		super(new DefaultPrinterConfiguration()
				.addOption(new DefaultConfigurationOption(ConfigOption.INDENTATION, new Indentation(IndentType.TABS, 1)))
				.addOption(new DefaultConfigurationOption(ConfigOption.END_OF_LINE_CHARACTER, "\n"))
				.addOption(new DefaultConfigurationOption(ConfigOption.MAX_ENUM_CONSTANTS_TO_ALIGN_HORIZONTALLY, 1)));
		this.typeResolver = typeResolver;
	}

	@Override
	protected void printModifiers(final NodeList<Modifier> modifiers) {
		for (Modifier m : modifiers) {
			printer.print("<span class=\"keyword\">");
			printer.print(m.getKeyword().asString());
			printer.print("</span> ");
		}
	}

	@Override
	protected void printMembers(final NodeList<BodyDeclaration<?>> members, final Void arg) {
		List<BodyDeclaration<?>> sortedMembers = new ArrayList<>(members);
		sortedMembers.sort(Comparator
				.comparingInt(HtmlPrinter::getTypeIdx)
				.thenComparing((a, b) -> {
					// move instance methods after constructors
					if (a.isConstructorDeclaration() && b.isMethodDeclaration() && !((MethodDeclaration) b).hasModifier(Keyword.STATIC)) {
						return -1;
					} else if (b.isConstructorDeclaration() && a.isMethodDeclaration() && !((MethodDeclaration) a).hasModifier(Keyword.STATIC)) {
						return 1;
					} else {
						return 0;
					}
				}));

		BodyDeclaration<?> prev = null;

		for (final BodyDeclaration<?> member : sortedMembers) {
			if (prev != null
					&& (!prev.isFieldDeclaration()
							|| !member.isFieldDeclaration()
							|| ((FieldDeclaration) prev).hasModifier(Keyword.STATIC) && !((FieldDeclaration) member).hasModifier(Keyword.STATIC))) {
				printer.println();
			}

			member.accept(this, arg);
			printer.println();

			prev = member;
		}
	}

	private static int getTypeIdx(BodyDeclaration<?> decl) {
		// order: enum-cst mth/ctor/annotation static-init/static-field inst-init/inst-field inst-type static-type
		// note: static-init <-> fields and inst-init <-> inst-field can't be reordered with each other as their order determines execution order

		if (decl.isEnumConstantDeclaration()) {
			return 0;
		} else if (decl.isAnnotationMemberDeclaration() || decl.isCallableDeclaration() || decl.isCompactConstructorDeclaration()) {
			return 1;
		} else if (decl.isFieldDeclaration()) {
			return ((FieldDeclaration) decl).hasModifier(Keyword.STATIC) ? 2 : 3;
		} else if (decl.isInitializerDeclaration()) {
			return ((InitializerDeclaration) decl).isStatic() ? 2 : 3;
		} else if (decl.isTypeDeclaration()) {
			if (decl.isClassOrInterfaceDeclaration() && ((ClassOrInterfaceDeclaration) decl).isInterface() || ((TypeDeclaration<?>) decl).isStatic()) {
				return 5;
			} else {
				return 4;
			}
		} else {
			throw new RuntimeException("unknown body decl type: "+decl.getClass().getName());
		}
	}

	@Override
	protected void printTypeArgs(final NodeWithTypeArguments<?> nodeWithTypeArguments, final Void arg) {
		NodeList<Type> typeArguments = nodeWithTypeArguments.getTypeArguments().orElse(null);

		if (!isNullOrEmpty(typeArguments)) {
			printer.print("&lt;"); // <

			for (final Iterator<Type> i = typeArguments.iterator(); i.hasNext(); ) {
				final Type t = i.next();
				t.accept(this, arg);

				if (i.hasNext()) {
					printer.print(", ");
				}
			}

			printer.print("&gt;"); // >
		}
	}

	@Override
	protected void printTypeParameters(final NodeList<TypeParameter> args, final Void arg) {
		if (!isNullOrEmpty(args)) {
			printer.print("&lt;"); // <

			for (final Iterator<TypeParameter> i = args.iterator(); i.hasNext(); ) {
				final TypeParameter t = i.next();
				t.accept(this, arg);

				if (i.hasNext()) {
					printer.print(", ");
				}
			}

			printer.print("&gt;"); // >
		}
	}

	@Override
	public void visit(final PackageDeclaration n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printer.print("<span class=\"keyword\">package</span> ");

		printer.print("<span class=\"package-declaration\">");
		n.getName().accept(this, arg);
		printer.print("</span>");

		printer.println(";");
		printer.println();

		printOrphanCommentsEnding(n);
	}

	@Override
	public void visit(SimpleName n, Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print(HtmlUtil.escape(n.getIdentifier()));
	}

	@Override
	public void visit(final ClassOrInterfaceDeclaration n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());

		if (n.isInterface()) {
			printer.print("<span class=\"keyword\">interface</span> ");
		} else {
			printer.print("<span class=\"keyword\">class</span> ");
		}

		printer.print("<span class=\"class-name\">");
		n.getName().accept(this, arg);
		printer.print("</span>");

		printTypeParameters(n.getTypeParameters(), arg);

		if (!n.getExtendedTypes().isEmpty()) {
			printer.print(" <span class=\"keyword\">extends</span> ");

			for (final Iterator<ClassOrInterfaceType> i = n.getExtendedTypes().iterator(); i.hasNext(); ) {
				final ClassOrInterfaceType c = i.next();
				c.accept(this, arg);

				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}

		if (!n.getImplementedTypes().isEmpty()) {
			printer.print(" <span class=\"keyword\">implements</span> ");

			for (final Iterator<ClassOrInterfaceType> i = n.getImplementedTypes().iterator(); i.hasNext(); ) {
				final ClassOrInterfaceType c = i.next();
				c.accept(this, arg);

				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}

		if (!n.getPermittedTypes().isEmpty()) {
			printer.print(" <span class=\"keyword\">permits</span> ");

			for (final Iterator<ClassOrInterfaceType> i = n.getPermittedTypes().iterator(); i.hasNext(); ) {
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
	public void visit(RecordDeclaration n, Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());

		printer.print("<span class=\"keyword\">record</span> ");

		printer.print("<span class=\"class-name\">");
		n.getName().accept(this, arg);
		printer.print("</span>");

		printTypeParameters(n.getTypeParameters(), arg);

		printer.print("(");

		if (!isNullOrEmpty(n.getParameters())) {
			for (final Iterator<Parameter> i = n.getParameters().iterator(); i.hasNext(); ) {
				final Parameter p = i.next();
				p.accept(this, arg);

				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}

		printer.print(")");

		if (!n.getImplementedTypes().isEmpty()) {
			printer.print(" <span class=\"keyword\">implements</span> ");

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
	public void visit(final JavadocComment n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);

		if (getOption(ConfigOption.PRINT_COMMENTS).isPresent() && getOption(ConfigOption.PRINT_JAVADOC).isPresent()) {
			printer.print("<span class=\"comment\">");
			printer.println(n.getHeader());
			final String commentContent = Utils.normalizeEolInTextBlock(HtmlUtil.escape(n.getContent()), getOption(ConfigOption.END_OF_LINE_CHARACTER).get().asString());
			String[] lines = commentContent.split("\\R");
			List<String> strippedLines = new ArrayList<>();

			for (String line : lines) {
				final String trimmedLine = line.trim();

				if (trimmedLine.startsWith("*")) {
					line = trimmedLine.substring(1);
				}

				line = Utils.trimTrailingSpaces(line);
				strippedLines.add(line);
			}

			boolean skippingLeadingEmptyLines = true;
			boolean prependEmptyLine = false;
			boolean prependSpace = strippedLines.stream().anyMatch(line -> !line.isEmpty() && !line.startsWith(" "));

			for (String line : strippedLines) {
				if (line.isEmpty()) {
					if (!skippingLeadingEmptyLines) {
						prependEmptyLine = true;
					}
				} else {
					skippingLeadingEmptyLines = false;

					if (prependEmptyLine) {
						printer.println(" *");
						prependEmptyLine = false;
					}

					printer.print(" *");

					if (prependSpace) {
						printer.print(" ");
					}

					printer.println(line);
				}
			}

			printer.print(" ")
					.print(n.getFooter())
					.println("</span>");
		}
	}

	@Override
	public void visit(final ClassOrInterfaceType n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);

		if (n.getScope().isPresent()) {
			recursionCounter++;
			n.getScope().get().accept(this, arg);
			printer.print(".");
			recursionCounter--;
		}

		printAnnotations(n.getAnnotations(), false, arg);

		if (recursionCounter == 0 && instantiationAhead) {
			printer.print("<span class=\"method-name\">");
			instantiationAhead = false;
		} else {
			printer.print("<span class=\"class-name\">");
		}

		n.getName().accept(this, arg);
		printer.print("</span>");

		if (n.isUsingDiamondOperator()) {
			printer.print("<>");
		} else {
			printTypeArgs(n, arg);
		}
	}

	@Override
	public void visit(final TypeParameter n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printAnnotations(n.getAnnotations(), false, arg);

		printer.print("<span class=\"class-name\">");
		n.getName().accept(this, arg);
		printer.print("</span>");

		if (!isNullOrEmpty(n.getTypeBound())) {
			printer.print(" <span class=\"keyword\">extends</span> ");

			for (final Iterator<ClassOrInterfaceType> i = n.getTypeBound().iterator(); i.hasNext(); ) {
				final ClassOrInterfaceType c = i.next();
				c.accept(this, arg);

				if (i.hasNext()) {
					printer.print(" & ");
				}
			}
		}
	}

	@Override
	public void visit(final PrimitiveType n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printAnnotations(n.getAnnotations(), true, arg);
		printer.print("<span class=\"keyword\">");
		printer.print(n.getType().asString());
		printer.print("</span>");
	}

	@Override
	public void visit(final WildcardType n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printAnnotations(n.getAnnotations(), false, arg);
		printer.print("?");

		if (n.getExtendedType().isPresent()) {
			printer.print(" <span class=\"keyword\">extends</span> ");
			n.getExtendedType().get().accept(this, arg);
		}

		if (n.getSuperType().isPresent()) {
			printer.print(" <span class=\"keyword\">super</span> ");
			n.getSuperType().get().accept(this, arg);
		}
	}

	@Override
	public void visit(final FieldDeclaration n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);

		boolean singleVar = n.getVariables().size() == 1;
		boolean hasSingleField = singleVar && fieldStart(n.getVariable(0));

		printComment(n.getComment(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());

		if (!n.getVariables().isEmpty()) {
			Optional<Type> maximumCommonType = n.getMaximumCommonType();
			maximumCommonType.ifPresent(t -> t.accept(this, arg));

			if (!maximumCommonType.isPresent()) {
				printer.print("???");
			}
		}

		printer.print(" ");

		for (final Iterator<VariableDeclarator> i = n.getVariables().iterator(); i.hasNext(); ) {
			final VariableDeclarator var = i.next();

			boolean hasField = !singleVar && fieldStart(var);

			var.accept(this, arg);

			if (hasField) printer.print("</span>");

			if (i.hasNext()) {
				printer.print(", ");
			}
		}

		printer.print(";");

		if (hasSingleField) printer.print("</span>");
	}

	private boolean fieldStart(VariableDeclarator var) {
		FieldInstance field = typeResolver.getField(var);

		if (field != null) {
			printer.print("<span id=\"");
			printer.print(HtmlUtil.getId(field));
			printer.print("\">");

			return true;
		} else {
			return false;
		}
	}

	@Override
	public void visit(final VariableDeclarator n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);

		boolean isField = n.getParentNode().orElse(null) instanceof FieldDeclaration;

		printer.print("<span class=\"");
		printer.print(isField ? "field" : "variable");
		printer.print("\">");
		n.getName().accept(this, arg);
		printer.print("</span>");

		n.findAncestor(NodeWithVariables.class).ifPresent(ancestor -> ((NodeWithVariables<?>) ancestor).getMaximumCommonType().ifPresent(commonType -> {
			final Type type = n.getType();
			ArrayType arrayType = null;

			for (int i = commonType.getArrayLevel(); i < type.getArrayLevel(); i++) {
				if (arrayType == null) {
					arrayType = (ArrayType) type;
				} else {
					arrayType = (ArrayType) arrayType.getComponentType();
				}

				printAnnotations(arrayType.getAnnotations(), true, arg);
				printer.print("[]");
			}
		}));

		if (n.getInitializer().isPresent()) {
			printer.print(" = ");
			n.getInitializer().get().accept(this, arg);
		}
	}

	@Override
	public void visit(final VoidType n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printAnnotations(n.getAnnotations(), false, arg);
		printer.print("<span class=\"keyword\">void</span>");
	}

	@Override
	public void visit(final VarType n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printAnnotations(n.getAnnotations(), false, arg);
		printer.print("<span class=\"keyword\">var</span>");
	}

	@Override
	public void visit(Modifier n, Void arg) {
		printer.print("<span class=\"keyword\">");
		printer.print(n.getKeyword().asString());
		printer.print("</span> ");
	}

	@Override
	public void visit(final ArrayCreationExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">new</span> ");
		n.getElementType().accept(this, arg);

		for (ArrayCreationLevel level : n.getLevels()) {
			level.accept(this, arg);
		}

		if (n.getInitializer().isPresent()) {
			printer.print(" ");
			n.getInitializer().get().accept(this, arg);
		}
	}

	@Override
	public void visit(final ClassExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		n.getType().accept(this, arg);
		printer.print(".");
		printer.print("<span class=\"field\">");
		printer.print("class");
		printer.print("</span>");
	}

	@Override
	public void visit(final FieldAccessExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"accessed-identifier\">");
		printer.print("<span class=\"class-name\">");
		n.getScope().accept(this, arg);
		printer.print("</span>");
		printer.print("</span>");
		printer.print(".");
		printer.print("<span class=\"field\">");
		n.getName().accept(this, arg);
		printer.print("</span>");
	}

	@Override
	public void visit(final InstanceOfExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		n.getExpression().accept(this, arg);
		printer.print(" <span class=\"keyword\">instanceof</span> ");

		if (n.getPattern().isPresent()) {
			n.getPattern().get().accept(this, arg);
		} else {
			n.getType().accept(this, arg);
		}
	}

	@Override
	public void visit(final CharLiteralExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"string\">'");
		printer.print(HtmlUtil.escape(n.getValue()));
		printer.print("'</span>");
	}

	@Override
	public void visit(final DoubleLiteralExpr n, final Void arg) {
		visitNumericLiteralExpr(n, arg);
	}

	@Override
	public void visit(final IntegerLiteralExpr n, final Void arg) {
		visitNumericLiteralExpr(n, arg);
	}

	@Override
	public void visit(final LongLiteralExpr n, final Void arg) {
		visitNumericLiteralExpr(n, arg);
	}

	private void visitNumericLiteralExpr(final LiteralStringValueExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"number\">");
		printer.print(n.getValue());
		printer.print("</span>");
	}

	@Override
	public void visit(final StringLiteralExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"string\">\"");
		printer.print(HtmlUtil.escape(n.getValue()));
		printer.print("\"</span>");
	}

	@Override
	public void visit(final TextBlockLiteralExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"string\">\"\"\"");
		printer.indent();
		n.stripIndentOfLines().forEach(line -> {
			printer.println();
			printer.print(HtmlUtil.escape(line));
		});
		printer.print("\"\"\"</span>");
		printer.unindent();
	}

	@Override
	public void visit(final BooleanLiteralExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);

		printer.print("<span class=\"keyword\">");
		printer.print(String.valueOf(n.getValue()));
		printer.print("</span>");
	}

	@Override
	public void visit(final NullLiteralExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">null</span>");
	}

	@Override
	public void visit(final ThisExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);

		if (n.getTypeName().isPresent()) {
			n.getTypeName().get().accept(this, arg);
			printer.print(".");
		}

		printer.print("<span class=\"keyword\">this</span>");
	}

	@Override
	public void visit(final SuperExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);

		if (n.getTypeName().isPresent()) {
			n.getTypeName().get().accept(this, arg);
			printer.print(".");
		}

		printer.print("<span class=\"keyword\">super</span>");
	}

	@Override
	public void visit(final MethodCallExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);

		// determine whether we do reindenting for aligmnent at all
		// - is it enabled?
		// - are we in a statement where we want the alignment?
		// - are we not directly in the argument list of a method call expression?
		AtomicBoolean columnAlignFirstMethodChain = new AtomicBoolean();

		if (getOption(ConfigOption.COLUMN_ALIGN_FIRST_METHOD_CHAIN).isPresent()) {
			// pick the kind of expressions where vertically aligning method calls is okay.
			if (n.findAncestor(Statement.class).map(p -> p.isReturnStmt()
					|| p.isThrowStmt()
					|| p.isAssertStmt()
					|| p.isExpressionStmt()).orElse(false)) {
				// search for first parent that does not have its child as scope
				Node c = n;
				Optional<Node> p = c.getParentNode();

				while (p.isPresent() && p.filter(NodeWithTraversableScope.class::isInstance)
						.map(NodeWithTraversableScope.class::cast)
						.flatMap(NodeWithTraversableScope::traverseScope)
						.map(c::equals)
						.orElse(false)) {
					c = p.get();
					p = c.getParentNode();
				}

				// check if the parent is a method call and thus we are in an argument list
				columnAlignFirstMethodChain.set(!p.filter(MethodCallExpr.class::isInstance).isPresent());
			}
		}

		// we are at the last method call of a call chain
		// this means we do not start reindenting for alignment or we undo it
		AtomicBoolean lastMethodInCallChain = new AtomicBoolean(true);

		if (columnAlignFirstMethodChain.get()) {
			Node node = n;

			while (node.getParentNode()
					.filter(NodeWithTraversableScope.class::isInstance)
					.map(NodeWithTraversableScope.class::cast)
					.flatMap(NodeWithTraversableScope::traverseScope)
					.map(node::equals)
					.orElse(false)) {
				node = node.getParentNode().orElseThrow(AssertionError::new);

				if (node instanceof MethodCallExpr) {
					lastMethodInCallChain.set(false);
					break;
				}
			}
		}

		// search whether there is a method call with scope in the scope already
		// this means that we probably started reindenting for alignment there
		AtomicBoolean methodCallWithScopeInScope = new AtomicBoolean();

		if (columnAlignFirstMethodChain.get()) {
			Optional<Expression> s = n.getScope();

			while (s.filter(NodeWithTraversableScope.class::isInstance).isPresent()) {
				Optional<Expression> parentScope = s.map(NodeWithTraversableScope.class::cast)
						.flatMap(NodeWithTraversableScope::traverseScope);

				if (s.filter(MethodCallExpr.class::isInstance).isPresent() && parentScope.isPresent()) {
					methodCallWithScopeInScope.set(true);
					break;
				}

				s = parentScope;
			}
		}

		// we have a scope
		// this means we are not the first method in the chain
		n.getScope().ifPresent(scope -> {
			NameExpr firstScopePart = null;
			NameExpr lastScopePart = null;

			if (scope.isNameExpr()) {
				firstScopePart = (NameExpr) scope.clone();
				String oldName = firstScopePart.getName().asString();
				int dotIndex = oldName.indexOf('.');

				if (dotIndex != -1) {
					firstScopePart.setName(oldName.substring(0, dotIndex));
					scope = firstScopePart;

					lastScopePart = (NameExpr) scope.clone();
					lastScopePart.setName(oldName.substring(dotIndex, oldName.length()));
				}
			}

			if (scope.isNameExpr()) printer.print("<span class=\"accessed-identifier\">");
			// TODO: Find a way to determine whether or not this is a class, and append span accordingly
			scope.accept(this, arg);
			if (scope.isNameExpr()) printer.print("</span>");

			if (lastScopePart != null) lastScopePart.accept(this, arg);

			if (columnAlignFirstMethodChain.get()) {
				if (methodCallWithScopeInScope.get()) {
					/* We're a method call on the result of something (method call, property access, ...) that is not stand alone,
						and not the first one with scope, like:
						we're x() in a.b().x(), or in a=b().c[15].d.e().x().
						That means that the "else" has been executed by one of the methods in the scope chain, so that the alignment
						is set to the "." of that method.
						That means we will align to that "." when we start a new line:
					*/
					printer.println();
				} else if (!lastMethodInCallChain.get()) {
					/* We're the first method call on the result of something in the chain (method call, property access, ...),
						but we are not at the same time the last method call in that chain, like:
						we're x() in a().x().y(), or in Long.x().y.z(). That means we get to dictate the indent of following method
						calls in this chain by setting the cursor to where we are now: just before the "."
						that start this method call.
					*/
					printer.reindentWithAlignToCursor();
				}
			}

			printer.print(".");
		});

		printTypeArgs(n, arg);

		printer.print("<span class=\"method-name\">");
		n.getName().accept(this, arg);
		printer.print("</span>");

		printer.duplicateIndent();
		printArguments(n.getArguments(), arg);
		printer.unindent();

		if (columnAlignFirstMethodChain.get() && methodCallWithScopeInScope.get() && lastMethodInCallChain.get()) {
			// undo the aligning after the arguments of the last method call are printed
			printer.reindentToPreviousLevel();
		}
	}

	@Override
	public void visit(final ObjectCreationExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);

		if (n.hasScope()) {
			n.getScope().get().accept(this, arg);
			printer.print(".");
		}

		printer.print("<span class=\"keyword\">new</span> ");
		printTypeArgs(n, arg);

		if (!isNullOrEmpty(n.getTypeArguments().orElse(null))) {
			printer.print(" ");
		}

		instantiationAhead = true;
		n.getType().accept(this, arg);
		printArguments(n.getArguments(), arg);

		if (n.getAnonymousClassBody().isPresent()) {
			printer.println(" {");
			printer.indent();
			printMembers(n.getAnonymousClassBody().get(), arg);
			printer.unindent();
			printer.print("}");
		}
	}

	@Override
	public void visit(final ConstructorDeclaration n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		MethodInstance method = typeResolver.getMethod(n);

		if (method != null) {
			printer.print("<span id=\"");
			printer.print(HtmlUtil.getId(method));
			printer.print("\">");
		}

		printComment(n.getComment(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());
		printTypeParameters(n.getTypeParameters(), arg);

		if (n.isGeneric()) {
			printer.print(" ");
		}

		printer.print("<span class=\"method-name\">");
		n.getName().accept(this, arg);
		printer.print("</span>");

		printer.print("(");
		n.getReceiverParameter().ifPresent(rp -> {
			rp.accept(this, arg);

			if (!isNullOrEmpty(n.getParameters())) {
				printer.print(", ");
			}
		});

		if (!n.getParameters().isEmpty()) {
			for (final Iterator<Parameter> i = n.getParameters().iterator(); i.hasNext(); ) {
				final Parameter p = i.next();
				p.accept(this, arg);

				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}

		printer.print(")");

		if (!isNullOrEmpty(n.getThrownExceptions())) {
			printer.print(" <span class=\"keyword\">throws</span> ");

			for (final Iterator<ReferenceType> i = n.getThrownExceptions().iterator(); i.hasNext(); ) {
				final ReferenceType name = i.next();
				name.accept(this, arg);

				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}

		printer.print(" ");
		n.getBody().accept(this, arg);

		if (method != null) {
			printer.print("</span>");
		}
	}

	@Override
	public void visit(final CompactConstructorDeclaration n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());
		printTypeParameters(n.getTypeParameters(), arg);

		if (n.isGeneric()) {
			printer.print(" ");
		}

		printer.print("<span class=\"method-name\">");
		n.getName().accept(this, arg);
		printer.print("</span>");

		if (!isNullOrEmpty(n.getThrownExceptions())) {
			printer.print(" <span class=\"keyword\">throws</span> ");

			for (final Iterator<ReferenceType> i = n.getThrownExceptions().iterator(); i.hasNext(); ) {
				final ReferenceType name = i.next();
				printer.print("<span class=\"variable\">");
				name.accept(this, arg);
				printer.print("</span>");

				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}

		printer.print(" ");
		n.getBody().accept(this, arg);
	}

	@Override
	public void visit(final MethodDeclaration n, final Void arg) {
		MethodInstance method = typeResolver.getMethod(n);

		if (method != null) {
			printer.print("<span id=\"");
			printer.print(HtmlUtil.getId(method));
			printer.print("\">");
		}

		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());
		printTypeParameters(n.getTypeParameters(), arg);

		if (!isNullOrEmpty(n.getTypeParameters())) {
			printer.print(" ");
		}

		n.getType().accept(this, arg);
		printer.print(" ");

		printer.print("<span class=\"method-name\">");
		n.getName().accept(this, arg);
		printer.print("</span>");

		printer.print("(");
		n.getReceiverParameter().ifPresent(rp -> {
			rp.accept(this, arg);

			if (!isNullOrEmpty(n.getParameters())) {
				printer.print(", ");
			}
		});

		if (!isNullOrEmpty(n.getParameters())) {
			for (final Iterator<Parameter> i = n.getParameters().iterator(); i.hasNext(); ) {
				final Parameter p = i.next();
				p.accept(this, arg);

				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}

		printer.print(")");

		if (!isNullOrEmpty(n.getThrownExceptions())) {
			printer.print(" <span class=\"keyword\">throws</span> ");

			for (final Iterator<ReferenceType> i = n.getThrownExceptions().iterator(); i.hasNext(); ) {
				final ReferenceType name = i.next();
				name.accept(this, arg);

				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}

		if (!n.getBody().isPresent()) {
			printer.print(";");
		} else {
			printer.print(" ");
			n.getBody().get().accept(this, arg);
		}

		if (method != null) {
			printer.print("</span>");
		}
	}

	@Override
	public void visit(final Parameter n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printAnnotations(n.getAnnotations(), false, arg);
		printModifiers(n.getModifiers());
		n.getType().accept(this, arg);

		if (n.isVarArgs()) {
			printAnnotations(n.getVarArgsAnnotations(), false, arg);
			printer.print("...");
		}

		if (!(n.getType() instanceof UnknownType)) {
			printer.print(" ");
		}

		printer.print("<span class=\"variable\">");
		n.getName().accept(this, arg);
		printer.print("</span>");
	}

	@Override
	public void visit(final ExplicitConstructorInvocationStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);

		if (n.isThis()) {
			printTypeArgs(n, arg);
			printer.print("<span class=\"keyword\">this</span>");
		} else {
			if (n.getExpression().isPresent()) {
				n.getExpression().get().accept(this, arg);
				printer.print(".");
			}

			printTypeArgs(n, arg);
			printer.print("<span class=\"keyword\">super</span>");
		}

		printArguments(n.getArguments(), arg);
		printer.print(";");
	}

	@Override
	public void visit(final AssertStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">assert</span> ");
		n.getCheck().accept(this, arg);

		if (n.getMessage().isPresent()) {
			printer.print(" : ");
			n.getMessage().get().accept(this, arg);
		}

		printer.print(";");
	}

	@Override
	public void visit(final SwitchStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printSwitchNode(n, arg);
	}

	@Override
	public void visit(SwitchExpr n, Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printSwitchNode(n, arg);
	}

	private void printSwitchNode(SwitchNode n, Void arg) {
		if (canAddNewLine((Node) n)) printer.println();

		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">switch</span> (");
		n.getSelector().accept(this, arg);
		printer.println(") {");

		if (n.getEntries() != null) {
			indentIf(getOption(ConfigOption.INDENT_CASE_IN_SWITCH).isPresent());

			for (final SwitchEntry e : n.getEntries()) {
				e.accept(this, arg);
			}

			unindentIf(getOption(ConfigOption.INDENT_CASE_IN_SWITCH).isPresent());
		}

		printer.print("}");

		if (getNext((Node) n) != null) printer.println();
	}

	@Override
	public void visit(final SwitchEntry n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);

		final String separator = (n.getType() == SwitchEntry.Type.STATEMENT_GROUP) ? ":" : " ->"; // old/new switch

		if (isNullOrEmpty(n.getLabels())) {
			printer.print("<span class=\"keyword\">default</span>" + separator);
		} else {
			printer.print("<span class=\"keyword\">case</span> ");

			for (final Iterator<Expression> i = n.getLabels().iterator(); i.hasNext(); ) {
				final Expression label = i.next();
				label.accept(this, arg);

				if (i.hasNext()) {
					printer.print(", ");
				}
			}

			printer.print(separator);
		}

		printer.println();
		printer.indent();

		if (n.getStatements() != null) {
			for (final Statement s : n.getStatements()) {
				s.accept(this, arg);
				printer.println();
			}
		}

		printer.unindent();
	}

	@Override
	public void visit(final BreakStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">break</span>");
		n.getLabel().ifPresent(l -> printer.print(" ").print(l.getIdentifier()));
		printer.print(";");
	}

	@Override
	public void visit(final YieldStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">yield</span> ");
		n.getExpression().accept(this, arg);
		printer.print(";");
	}

	@Override
	public void visit(final ReturnStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">return</span>");

		if (n.getExpression().isPresent()) {
			printer.print(" ");
			n.getExpression().get().accept(this, arg);
		}

		printer.print(";");
	}

	@Override
	public void visit(final EnumDeclaration n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());

		printer.print("<span class=\"keyword\">enum</span> ");

		printer.print("<span class=\"class-name\">");
		n.getName().accept(this, arg);
		printer.print("</span>");

		if (!n.getImplementedTypes().isEmpty()) {
			printer.print(" <span class=\"keyword\">implements</span> ");

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

		if (n.getEntries().isNonEmpty()) {
			final boolean alignVertically =
					// Either we hit the constant amount limit in the configurations, or...
					n.getEntries().size() > getOption(ConfigOption.MAX_ENUM_CONSTANTS_TO_ALIGN_HORIZONTALLY).get().asInteger()
							// any of the constants has a comment.
							|| n.getEntries().stream().anyMatch(e -> e.getComment().isPresent());

			for (final Iterator<EnumConstantDeclaration> i = n.getEntries().iterator(); i.hasNext(); ) {
				final EnumConstantDeclaration e = i.next();
				e.accept(this, arg);

				if (i.hasNext()) {
					if (alignVertically) {
						printer.println(",");
					} else {
						printer.print(", ");
					}
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
	public void visit(final EnumConstantDeclaration n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		FieldInstance field = typeResolver.getField(n);

		if (field != null) {
			printer.print("<span id=\"");
			printer.print(HtmlUtil.getId(field));
			printer.print("\">");
		}

		printComment(n.getComment(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);

		printer.print("<span class=\"enum-constant\">");
		n.getName().accept(this, arg);
		printer.print("</span>");

		if (!n.getArguments().isEmpty()) {
			printArguments(n.getArguments(), arg);
		}

		if (!n.getClassBody().isEmpty()) {
			printer.println(" {");
			printer.indent();
			printMembers(n.getClassBody(), arg);
			printer.unindent();
			printer.println("}");
		}

		if (field != null) printer.print("</span>");
	}

	@Override
	public void visit(final InitializerDeclaration n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);

		if (n.isStatic()) {
			printer.print("<span class=\"keyword\">static</span> ");
		}

		n.getBody().accept(this, arg);
	}

	@Override
	public void visit(final IfStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
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

		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">if</span> (");
		n.getCondition().accept(this, arg);
		printer.print(") ");
		n.getThenStmt().accept(this, arg);

		Node next = getNext(n);

		if (n.getElseStmt().isPresent()) {
			Statement elseStmt = n.getElseStmt().get();

			if (thenBlock) {
				printer.print(" ");
			} else {
				printer.println();
			}

			final boolean elseIf = n.getElseStmt().orElse(null) instanceof IfStmt;
			final boolean elseBlock = n.getElseStmt().orElse(null) instanceof BlockStmt;

			if (elseIf || elseBlock) { // put chained if and start of block statement on a same level
				printer.print("<span class=\"keyword\">else</span> ");
			} else {
				printer.println("<span class=\"keyword\">else</span>");
				printer.indent();
			}

			elseStmt.accept(this, arg);

			if (!(elseIf || elseBlock)) {
				printer.unindent();
			}

			if (next != null) printer.println();
		} else {
			if (next != null && (thenBlock || !(next instanceof IfStmt))) printer.println();
		}
	}

	@Override
	public void visit(final WhileStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		if (canAddNewLine(n)) printer.println();

		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">while</span> (");
		n.getCondition().accept(this, arg);
		printer.print(") ");
		n.getBody().accept(this, arg);

		if (getNext(n) != null) printer.println();
	}

	@Override
	public void visit(final ContinueStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">continue</span>");
		n.getLabel().ifPresent(l -> printer.print(" ").print(l.getIdentifier()));
		printer.print(";");
	}

	@Override
	public void visit(final DoStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		if (canAddNewLine(n)) printer.println();

		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">do</span> ");
		n.getBody().accept(this, arg);
		printer.print(" <span class=\"keyword\">while</span> (");
		n.getCondition().accept(this, arg);
		printer.print(");");

		if (getNext(n) != null) printer.println();
	}

	@Override
	public void visit(final ForEachStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		if (canAddNewLine(n)) printer.println();

		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">for</span> (");
		n.getVariable().accept(this, arg);
		printer.print(" : ");
		n.getIterable().accept(this, arg);
		printer.print(") ");
		n.getBody().accept(this, arg);

		if (getNext(n) != null) printer.println();
	}

	@Override
	public void visit(final ForStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		if (canAddNewLine(n)) printer.println();

		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">for</span> (");

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
	public void visit(final ThrowStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">throw</span> ");
		n.getExpression().accept(this, arg);
		printer.print(";");
	}

	@Override
	public void visit(final SynchronizedStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">synchronized</span> (");
		n.getExpression().accept(this, arg);
		printer.print(") ");
		n.getBody().accept(this, arg);
	}

	@Override
	public void visit(final TryStmt n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		if (canAddNewLine(n)) printer.println();

		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">try</span> ");

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
			printer.print(" <span class=\"keyword\">finally</span> ");
			n.getFinallyBlock().get().accept(this, arg);
		}

		if (getNext(n) != null) printer.println();
	}

	@Override
	public void visit(final CatchClause n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print(" <span class=\"keyword\">catch</span> (");
		n.getParameter().accept(this, arg);
		printer.print(") ");
		n.getBody().accept(this, arg);
	}

	@Override
	public void visit(final AnnotationDeclaration n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());

		printer.print("<span class=\"keyword\">@interface</span> <span class=\"annotation\">");
		n.getName().accept(this, arg);
		printer.println("</span> {");
		printer.indent();

		if (n.getMembers() != null) {
			printMembers(n.getMembers(), arg);
		}

		printer.unindent();
		printer.print("}");
	}

	@Override
	public void visit(final AnnotationMemberDeclaration n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());

		n.getType().accept(this, arg);
		printer.print(" ");
		printer.print("<span class=\"assigned-annotation-member\">");
		n.getName().accept(this, arg);
		printer.print("</span>");
		printer.print("()");

		if (n.getDefaultValue().isPresent()) {
			printer.print(" <span class=\"keyword\">default</span> ");
			n.getDefaultValue().get().accept(this, arg);
		}

		printer.print(";");
	}

	@Override
	public void visit(final MarkerAnnotationExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"annotation\">@");
		n.getName().accept(this, arg);
		printer.print("</span>");
	}

	@Override
	public void visit(final SingleMemberAnnotationExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"annotation\">@");
		n.getName().accept(this, arg);
		printer.print("</span>(");
		printer.print("<span class=\"standalone-annotation-member\">");
		n.getMemberValue().accept(this, arg);
		printer.print("</span>");
		printer.print(")");
	}

	@Override
	public void visit(final NormalAnnotationExpr n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"annotation\">@");
		n.getName().accept(this, arg);
		printer.print("</span>(");

		if (n.getPairs() != null) {
			for (final Iterator<MemberValuePair> i = n.getPairs().iterator(); i.hasNext(); ) {
				final MemberValuePair m = i.next();
				m.accept(this, arg);

				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}

		printer.print(")");
	}

	@Override
	public void visit(final MemberValuePair n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);

		boolean annotation = (n.getParentNode().get() instanceof NormalAnnotationExpr);

		if (annotation) printer.print("<span class=\"assigned-annotation-member\">");
		n.getName().accept(this, arg);
		if (annotation) printer.print("</span>");

		printer.print(" = ");
		n.getValue().accept(this, arg);
	}

	@Override
	public void visit(final LineComment n, final Void arg) {
		if (!getOption(ConfigOption.PRINT_COMMENTS).isPresent()) {
			return;
		}

		printer.print("<span class=\"comment\">");
		printer.print(n.getHeader())
				.print(" ")
				.println(Utils.normalizeEolInTextBlock(HtmlUtil.escape(RTRIM.matcher(n.getContent()).replaceAll("")), ""));
		printer.println("</span>");
	}

	@Override
	public void visit(final BlockComment n, final Void arg) {
		if (!getOption(ConfigOption.PRINT_COMMENTS).isPresent()) {
			return;
		}

		final String commentContent = Utils.normalizeEolInTextBlock(n.getContent(), getOption(ConfigOption.END_OF_LINE_CHARACTER).get().asString());
		String[] lines = commentContent.split("\\R", -1); // as BlockComment should not be formatted, -1 to preserve any trailing empty line if present
		printer.print("<span class=\"comment\">");
		printer.print(n.getHeader());

		for (int i = 0; i < (lines.length - 1); i++) {
			printer.print(lines[i]);
			printer.print(getOption(ConfigOption.END_OF_LINE_CHARACTER).get().asString()); // Avoids introducing indentation in blockcomments. ie: do not use println() as it would trigger indentation at the next print call.
		}

		printer.print(lines[lines.length - 1]) // last line is not followed by a newline, and simply terminated with `*/`
				.print(n.getFooter())
				.println("</span>");
	}

	@Override
	public void visit(final ImportDeclaration n, final Void arg) {
		printOrphanCommentsBeforeThisChildNode(n);
		printComment(n.getComment(), arg);
		printer.print("<span class=\"keyword\">import</span> ");

		if (n.isStatic()) {
			printer.print("<span class=\"keyword\">static</span> ");
		}

		printer.print("<span class=\"import-declaration-package\">");
		n.getName().accept(this, arg);

		if (n.isAsterisk()) {
			printer.print(".*");
		}

		printer.print("</span>");
		printer.println(";");

		printOrphanCommentsEnding(n);
	}

	@Override
	public void visit(ModuleDeclaration n, Void arg) {
		printMemberAnnotations(n.getAnnotations(), arg);
		printer.println();

		if (n.isOpen()) {
			printer.print("<span class=\"keyword\">open</span> ");
		}

		printer.print("<span class=\"keyword\">module</span> ");
		n.getName().accept(this, arg);
		printer.println(" {").indent();
		n.getDirectives().accept(this, arg);
		printer.unindent().println("}");
	}

	@Override
	public void visit(ModuleRequiresDirective n, Void arg) {
		printer.print("<span class=\"keyword\">requires</span> ");
		printModifiers(n.getModifiers());
		n.getName().accept(this, arg);
		printer.println(";");
	}

	@Override
	public void visit(ModuleExportsDirective n, Void arg) {
		printer.print("<span class=\"keyword\">exports</span> ");
		n.getName().accept(this, arg);
		printPrePostFixOptionalList(n.getModuleNames(), arg, " to ", ", ", "");
		printer.println(";");
	}

	@Override
	public void visit(ModuleProvidesDirective n, Void arg) {
		printer.print("<span class=\"keyword\">provides</span> ");
		n.getName().accept(this, arg);
		printPrePostFixRequiredList(n.getWith(), arg, " with ", ", ", "");
		printer.println(";");
	}

	@Override
	public void visit(ModuleUsesDirective n, Void arg) {
		printer.print("<span class=\"keyword\">uses</span> ");
		n.getName().accept(this, arg);
		printer.println(";");
	}

	@Override
	public void visit(ModuleOpensDirective n, Void arg) {
		printer.print("<span class=\"keyword\">opens</span> ");
		n.getName().accept(this, arg);
		printPrePostFixOptionalList(n.getModuleNames(), arg, " to ", ", ", "");
		printer.println(";");
	}

	private void printOrphanCommentsBeforeThisChildNode(final Node node) {
		if (!getOption(ConfigOption.PRINT_COMMENTS).isPresent()) return;
		if (node instanceof Comment) return;

		Node parent = node.getParentNode().orElse(null);
		if (parent == null) return;
		List<Node> everything = new ArrayList<>(parent.getChildNodes());
		sortByBeginPosition(everything);
		int positionOfTheChild = -1;

		for (int i = 0; i < everything.size(); ++i) { // indexOf is by equality, so this is used to index by identity
			if (everything.get(i) == node) {
				positionOfTheChild = i;
				break;
			}
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

			if (!(nodeToPrint instanceof Comment)) {
				throw new RuntimeException(
						"Expected comment, instead " + nodeToPrint.getClass() + ". Position of previous child: "
								+ positionOfPreviousChild + ", position of child " + positionOfTheChild);
			}

			nodeToPrint.accept(this, null);
		}
	}

	private void printOrphanCommentsEnding(final Node node) {
		if (!getOption(ConfigOption.PRINT_COMMENTS).isPresent()) return;

		List<Node> everything = new ArrayList<>(node.getChildNodes());
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

	private void indentIf(boolean expr) {
		if (expr) printer.indent();
	}

	private void unindentIf(boolean expr) {
		if (expr) printer.unindent();
	}

	private Optional<ConfigurationOption> getOption(ConfigOption option) {
		return configuration.get(new DefaultConfigurationOption(option));
	}

	// Matcher-introduced methods

	private static boolean canAddNewLine(Node n) {
		Node prev = getPrev(n);

		return prev != null && !isBlockStmt(prev);
	}

	private static boolean isBlockStmt(Node n) {
		return n instanceof BlockStmt
				|| n instanceof DoStmt
				|| n instanceof ForStmt
				|| n instanceof ForEachStmt
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

	private static Pattern RTRIM = Pattern.compile("\\s+$");
	protected final TypeResolver typeResolver;
	protected boolean instantiationAhead;
	protected int recursionCounter;
}
