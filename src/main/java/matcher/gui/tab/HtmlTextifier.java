/*
 * Most of this file is copied from ASM's Textifier class,
 * tweaked to output HTML instead of plain text. Original license:
 *
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package matcher.gui.tab;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Pattern;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TextifierSupport;
import org.objectweb.asm.util.TraceSignatureVisitor;

import matcher.NameType;
import matcher.srcprocess.HtmlUtil;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MethodInstance;

final class HtmlTextifier extends Textifier {
	HtmlTextifier(ClassInstance cls, NameType nameType) {
		super(Opcodes.ASM9);

		this.cls = cls;
		this.nameType = nameType;

		tab = "   ";
		tab2 = "      ";
		tab3 = "         ";
		ltab = "    ";
	}

	// -----------------------------------------------------------------------------------------------
	// Classes
	// -----------------------------------------------------------------------------------------------

	@Override
	public void visit(
			final int version,
			final int access,
			final String name,
			final String signature,
			final String superName,
			final String[] interfaces) {
		if ((access & Opcodes.ACC_MODULE) != 0) {
			// Modules are printed in visitModule.
			return;
		}

		this.access = access;
		int majorVersion = version & 0xFFFF;
		int minorVersion = version >>> 16;
		stringBuilder.setLength(0);
		stringBuilder
				.append("<span class=\"comment\">")
				.append("// class version ")
				.append(majorVersion)
				.append('.')
				.append(minorVersion)
				.append(" (")
				.append(version)
				.append(')')
				.append("</span>");
		if ((access & Opcodes.ACC_DEPRECATED) != 0) {
			stringBuilder.append(DEPRECATED);
		}

		if ((access & Opcodes.ACC_RECORD) != 0) {
			stringBuilder.append(RECORD);
		}

		appendRawAccess(access);

		appendDescriptor(CLASS_SIGNATURE, signature);

		if (signature != null) {
			appendJavaDeclaration(name, signature);
		}

		appendAccess(access & ~(Opcodes.ACC_SUPER | Opcodes.ACC_MODULE));

		if ((access & Opcodes.ACC_ANNOTATION) != 0) {
			stringBuilder
					.append("<span class=\"keyword\">")
					.append("@interface")
					.append("</span> ");
		} else if ((access & Opcodes.ACC_INTERFACE) != 0) {
			stringBuilder
					.append("<span class=\"keyword\">")
					.append("interface")
					.append("</span> ");
		} else if ((access & Opcodes.ACC_ENUM) == 0) {
			stringBuilder
					.append("<span class=\"keyword\">")
					.append("class")
					.append("</span> ");
		}

		appendDescriptor(INTERNAL_NAME, name);

		if (superName != null && !"java/lang/Object".equals(superName)) {
			stringBuilder
					.append("<span class=\"keyword\">")
					.append(" extends")
					.append("</span> ");

			appendDescriptor(INTERNAL_NAME, superName);
		}

		if (interfaces != null && interfaces.length > 0) {
			stringBuilder
					.append("<span class=\"keyword\">")
					.append(" implements")
					.append("</span> ");

			for (int i = 0; i < interfaces.length; ++i) {
				appendDescriptor(INTERNAL_NAME, interfaces[i]);

				if (i != interfaces.length - 1) {
					stringBuilder.append(' ');
				}
			}
		}

		stringBuilder.append(" {\n\n");

		text.add(stringBuilder.toString());
	}

	@Override
	public void visitSource(final String file, final String debug) {
		stringBuilder.setLength(0);

		if (file != null) {
			stringBuilder
					.append(tab)
					.append("<span class=\"comment\">")
					.append("// compiled from: ")
					.append(file)
					.append("</span>\n");
		}

		if (debug != null) {
			stringBuilder
					.append(tab)
					.append("<span class=\"comment\">")
					.append("// debug info: ")
					.append(debug)
					.append("</span>\n");
		}

		if (stringBuilder.length() > 0) {
			text.add(stringBuilder.toString());
		}
	}

	@Override
	public Printer visitModule(final String name, final int access, final String version) {
		stringBuilder.setLength(0);

		if ((access & Opcodes.ACC_OPEN) != 0) {
			stringBuilder
					.append("<span class=\"keyword\">")
					.append("open")
					.append("</span> ");
		}

		stringBuilder
				.append("<span class=\"keyword\">")
				.append("module ")
				.append("</span>")
				.append("<span class=\"class-name\">")
				.append(name)
				.append("</span>")
				.append(" { ")
				.append(version == null ? "" : "<span class=\"comment\">// " + version)
				.append("</span>\n\n");
		text.add(stringBuilder.toString());
		return addNewTextifier(null);
	}

	@Override
	public void visitNestHost(final String nestHost) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab)
				.append("<span class=\"keyword\">")
				.append("NESTHOST")
				.append("</span> ");
		appendDescriptor(INTERNAL_NAME, nestHost);
		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitOuterClass(final String owner, final String name, final String descriptor) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab)
				.append("<span class=\"keyword\">")
				.append("OUTERCLASS")
				.append("</span> ");
		appendDescriptor(INTERNAL_NAME, owner);
		stringBuilder.append(' ');

		if (name != null) {
			stringBuilder
					.append("<span class=\"class-name\">")
					.append(name)
					.append("</span> ");
		}

		appendDescriptor(METHOD_DESCRIPTOR, descriptor);
		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitNestMember(final String nestMember) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab)
				.append("<span class=\"keyword\">")
				.append("NESTMEMBER")
				.append("</span> ");
		appendDescriptor(INTERNAL_NAME, nestMember);
		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitPermittedSubclass(final String permittedSubclass) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab)
				.append("<span class=\"keyword\">")
				.append("PERMITTEDSUBCLASS")
				.append("</span> ");
		appendDescriptor(INTERNAL_NAME, permittedSubclass);
		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitInnerClass(
			final String name, final String outerName, final String innerName, final int access) {
		stringBuilder.setLength(0);
		stringBuilder.append(tab);
		appendRawAccess(access & ~Opcodes.ACC_SUPER);
		stringBuilder.append(tab);
		appendAccess(access);
		stringBuilder
				.append("<span class=\"keyword\">")
				.append("INNERCLASS")
				.append("</span> ");
		appendDescriptor(INTERNAL_NAME, name);
		stringBuilder.append(' ');
		appendDescriptor(INTERNAL_NAME, outerName);
		stringBuilder.append(' ');
		appendDescriptor(INTERNAL_NAME, innerName);
		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public Printer visitRecordComponent(
			final String name, final String descriptor, final String signature) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab)
				.append("<span class=\"keyword\">")
				.append("RECORDCOMPONENT")
				.append("</span> ");
		if (signature != null) {
			stringBuilder.append(tab);
			appendDescriptor(FIELD_SIGNATURE, signature);
			stringBuilder.append(tab);
			appendJavaDeclaration(name, signature);
		}

		stringBuilder.append(tab);

		appendDescriptor(FIELD_DESCRIPTOR, descriptor);
		stringBuilder
				.append(" <span class=\"class-name\">")
				.append(name)
				.append("</span>\n");
		text.add(stringBuilder.toString());
		return addNewTextifier(null);
	}

	@Override
	public Textifier visitField(
			final int access,
			final String name,
			final String descriptor,
			final String signature,
			final Object value) {
		FieldInstance field = cls.getField(name, descriptor, nameType);
		if (field != null) text.add(String.format("<div id=\"%s\">", HtmlUtil.getId(field)));

		stringBuilder.setLength(0);
		stringBuilder.append('\n');

		if ((access & Opcodes.ACC_DEPRECATED) != 0) {
			stringBuilder
					.append(tab)
					.append(DEPRECATED);
		}

		stringBuilder.append(tab);
		appendRawAccess(access);

		if (signature != null) {
			stringBuilder.append(tab);
			appendDescriptor(FIELD_SIGNATURE, signature);
			stringBuilder.append(tab);
			appendJavaDeclaration(name, signature);
		}

		stringBuilder.append(tab);
		appendAccess(access);

		appendDescriptor(FIELD_DESCRIPTOR, descriptor);
		stringBuilder
				.append(" <span class=\"field\">")
				.append(name)
				.append("</span>");
		if (value != null) {
			stringBuilder.append(" = ");

			if (value instanceof String) {
				stringBuilder
						.append('\"')
						.append(value)
						.append('\"');
			} else {
				stringBuilder.append(value);
			}
		}

		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
		if (field != null) text.add("</div>");

		return addNewTextifier(null);
	}

	@Override
	public Textifier visitMethod(
			final int access,
			final String name,
			final String descriptor,
			final String signature,
			final String[] exceptions) {
		MethodInstance method = cls.getMethod(name, descriptor, nameType);
		if (method != null) text.add(String.format("<div id=\"%s\">", HtmlUtil.getId(method)));

		stringBuilder.setLength(0);
		stringBuilder.append('\n');

		if ((access & Opcodes.ACC_DEPRECATED) != 0) {
			stringBuilder
					.append(tab)
					.append(DEPRECATED);
		}

		stringBuilder.append(tab);
		appendRawAccess(access);

		if (signature != null) {
			stringBuilder.append(tab);
			appendDescriptor(METHOD_SIGNATURE, signature);
			stringBuilder.append(tab);
			appendJavaDeclaration(name, signature);
		}

		stringBuilder.append(tab);
		appendAccess(access & ~(Opcodes.ACC_VOLATILE | Opcodes.ACC_TRANSIENT));

		if ((access & Opcodes.ACC_NATIVE) != 0) {
			stringBuilder
					.append("<span class=\"keyword\">")
					.append("native")
					.append("</span> ");
		}

		if ((access & Opcodes.ACC_VARARGS) != 0) {
			stringBuilder
					.append("<span class=\"keyword\">")
					.append("varargs")
					.append("</span> ");
		}

		if ((access & Opcodes.ACC_BRIDGE) != 0) {
			stringBuilder
					.append("<span class=\"keyword\">")
					.append("bridge")
					.append("</span> ");
		}

		if ((this.access & Opcodes.ACC_INTERFACE) != 0
				&& (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC)) == 0) {
			stringBuilder
					.append("<span class=\"keyword\">")
					.append("default")
					.append("</span> ");
		}

		stringBuilder
				.append("<span class=\"method-name\">")
				.append(name)
				.append("</span>");
		appendDescriptor(METHOD_DESCRIPTOR, descriptor);

		if (exceptions != null && exceptions.length > 0) {
			stringBuilder
					.append(" <span class=\"keyword\">")
					.append("throws")
					.append("</span> ");
			for (String exception : exceptions) {
				appendDescriptor(INTERNAL_NAME, exception);
				stringBuilder.append(' ');
			}
		}

		stringBuilder.append("</span>");

		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
		if (method != null) text.add("</div>");

		return addNewTextifier(null);
	}

	@Override
	public void visitClassEnd() {
		text.add("}\n");
		escape(text);
	}

	// -----------------------------------------------------------------------------------------------
	// Modules
	// -----------------------------------------------------------------------------------------------

	@Override
	public void visitMainClass(final String mainClass) {
		stringBuilder.setLength(0);
		stringBuilder
				.append("  <span class=\"comment\">")
				.append("// main class ")
				.append(mainClass)
				.append("</span>\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitPackage(final String packaze) {
		stringBuilder.setLength(0);
		stringBuilder
				.append("  <span class=\"comment\">")
				.append("// package ")
				.append(packaze)
				.append("</span>\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitRequire(final String require, final int access, final String version) {
		stringBuilder.setLength(0);
		stringBuilder
				.append("<span class=\"keyword\">")
				.append(tab)
				.append("requires")
				.append("</span> ");
		if ((access & Opcodes.ACC_TRANSITIVE) != 0) {
			stringBuilder
					.append("<span class=\"keyword\">")
					.append("transitive")
					.append("</span> ");
		}

		if ((access & Opcodes.ACC_STATIC_PHASE) != 0) {
			stringBuilder
					.append("<span class=\"keyword\">")
					.append("static")
					.append("</span> ");
		}

		stringBuilder.append(require).append(';');
		appendRawAccess(access);

		if (version != null) {
			stringBuilder
					.append("  <span class=\"comment\">")
					.append("// version ")
					.append(version)
					.append("</span>\n");
		}

		text.add(stringBuilder.toString());
	}

	@Override
	public void visitExport(final String packaze, final int access, final String... modules) {
		visitExportOrOpen("exports", packaze, access, modules);
	}

	@Override
	public void visitOpen(final String packaze, final int access, final String... modules) {
		visitExportOrOpen("opens", packaze, access, modules);
	}

	private void visitExportOrOpen(
			final String method, final String packaze, final int access, final String... modules) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab)
				.append("<span class=\"keyword\">")
				.append(method)
				.append("</span>")
				.append("<span class=\"import-declaration-package\">")
				.append(packaze)
				.append("</span>");
		if (modules != null && modules.length > 0) {
			stringBuilder
					.append(" <span class=\"keyword\">")
					.append("to")
					.append("</span>");
		} else {
			stringBuilder.append(';');
		}

		appendRawAccess(access);

		if (modules != null && modules.length > 0) {
			for (int i = 0; i < modules.length; ++i) {
				stringBuilder
						.append(tab2)
						.append("<span class=\"import-declaration-package\">")
						.append(modules[i])
						.append("</span>")
						.append(i != modules.length - 1 ? ",\n" : ";\n");
			}
		}

		text.add(stringBuilder.toString());
	}

	@Override
	public void visitUse(final String use) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab)
				.append("<span class=\"keyword\">")
				.append("uses")
				.append("</span> ");
		appendDescriptor(INTERNAL_NAME, use);
		stringBuilder.append(";\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitProvide(final String provide, final String... providers) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab)
				.append("<span class=\"keyword\">")
				.append("provides")
				.append("</span> ");
		appendDescriptor(INTERNAL_NAME, provide);
		stringBuilder
				.append(" <span class=\"keyword\">")
				.append("with")
				.append("</span>\n");
		for (int i = 0; i < providers.length; ++i) {
			stringBuilder.append(tab2);
			appendDescriptor(INTERNAL_NAME, providers[i]);
			stringBuilder.append(i != providers.length - 1 ? ",\n" : ";\n");
		}

		text.add(stringBuilder.toString());
	}

	// -----------------------------------------------------------------------------------------------
	// Annotations
	// -----------------------------------------------------------------------------------------------

	// DontCheck(OverloadMethodsDeclarationOrder): overloads are semantically different.
	@Override
	public void visit(final String name, final Object value) {
		visitAnnotationValue(name);

		if (value instanceof String) {
			visitString((String) value);
		} else if (value instanceof Type) {
			visitType((Type) value);
		} else if (value instanceof Byte) {
			visitByte(((Byte) value).byteValue());
		} else if (value instanceof Boolean) {
			visitBoolean(((Boolean) value).booleanValue());
		} else if (value instanceof Short) {
			visitShort(((Short) value).shortValue());
		} else if (value instanceof Character) {
			visitChar(((Character) value).charValue());
		} else if (value instanceof Integer) {
			visitInt(((Integer) value).intValue());
		} else if (value instanceof Float) {
			visitFloat(((Float) value).floatValue());
		} else if (value instanceof Long) {
			visitLong(((Long) value).longValue());
		} else if (value instanceof Double) {
			visitDouble(((Double) value).doubleValue());
		} else if (value.getClass().isArray()) {
			stringBuilder.append('{');

			if (value instanceof byte[]) {
				byte[] byteArray = (byte[]) value;

				for (int i = 0; i < byteArray.length; i++) {
					maybeAppendComma(i);
					visitByte(byteArray[i]);
				}
			} else if (value instanceof boolean[]) {
				boolean[] booleanArray = (boolean[]) value;

				for (int i = 0; i < booleanArray.length; i++) {
					maybeAppendComma(i);
					visitBoolean(booleanArray[i]);
				}
			} else if (value instanceof short[]) {
				short[] shortArray = (short[]) value;

				for (int i = 0; i < shortArray.length; i++) {
					maybeAppendComma(i);
					visitShort(shortArray[i]);
				}
			} else if (value instanceof char[]) {
				char[] charArray = (char[]) value;

				for (int i = 0; i < charArray.length; i++) {
					maybeAppendComma(i);
					visitChar(charArray[i]);
				}
			} else if (value instanceof int[]) {
				int[] intArray = (int[]) value;

				for (int i = 0; i < intArray.length; i++) {
					maybeAppendComma(i);
					visitInt(intArray[i]);
				}
			} else if (value instanceof long[]) {
				long[] longArray = (long[]) value;

				for (int i = 0; i < longArray.length; i++) {
					maybeAppendComma(i);
					visitLong(longArray[i]);
				}
			} else if (value instanceof float[]) {
				float[] floatArray = (float[]) value;

				for (int i = 0; i < floatArray.length; i++) {
					maybeAppendComma(i);
					visitFloat(floatArray[i]);
				}
			} else if (value instanceof double[]) {
				double[] doubleArray = (double[]) value;

				for (int i = 0; i < doubleArray.length; i++) {
					maybeAppendComma(i);
					visitDouble(doubleArray[i]);
				}
			}

			stringBuilder.append('}');
		}

		text.add(stringBuilder.toString());
	}

	private void visitInt(final int value) {
		stringBuilder.append(value);
	}

	private void visitLong(final long value) {
		stringBuilder
				.append(value)
				.append("<span class=\"keyword\">")
				.append('L')
				.append("</span>");
	}

	private void visitFloat(final float value) {
		stringBuilder
				.append(value)
				.append("<span class=\"keyword\">")
				.append('F')
				.append("</span>");
	}

	private void visitDouble(final double value) {
		stringBuilder
				.append(value)
				.append("<span class=\"keyword\">")
				.append('D')
				.append("</span>");
	}

	private void visitChar(final char value) {
		stringBuilder
				.append('(')
				.append("<span class=\"keyword\">")
				.append("char")
				.append("</span>")
				.append(')')
				.append((int) value);
	}

	private void visitShort(final short value) {
		stringBuilder
				.append('(')
				.append("<span class=\"keyword\">")
				.append("short")
				.append("</span>")
				.append(')')
				.append(value);
	}

	private void visitByte(final byte value) {
		stringBuilder
				.append('(')
				.append("<span class=\"keyword\">")
				.append("byte")
				.append("</span>")
				.append(')')
				.append(value);
	}

	private void visitBoolean(final boolean value) {
		stringBuilder
				.append("<span class=\"keyword\">")
				.append(value)
				.append("</span>");
	}

	private void visitString(final String value) {
		stringBuilder.append("<span class=\"string\">");
		appendString(stringBuilder, value);
		stringBuilder.append("</span>");
	}

	private void visitType(final Type value) {
		stringBuilder
				.append("<span class=\"class-name\">")
				.append(value.getClassName())
				.append("</span>")
				.append("<span class=\"field\">")
				.append(CLASS_SUFFIX)
				.append("</span>");
	}

	@Override
	public void visitEnum(final String name, final String descriptor, final String value) {
		visitAnnotationValue(name);
		appendDescriptor(FIELD_DESCRIPTOR, descriptor);
		stringBuilder
				.append('.')
				.append("<span class=\"enum-constant\">")
				.append(value)
				.append("</span>");
		text.add(stringBuilder.toString());
	}

	@Override
	public Textifier visitAnnotation(final String name, final String descriptor) {
		annotation = true;
		stringBuilder.append("<span class=\"annotation\">");
		visitAnnotationValue(name);
		stringBuilder.append('@');
		appendDescriptor(FIELD_DESCRIPTOR, descriptor);
		stringBuilder.append('(');
		text.add(stringBuilder.toString());
		annotation = false;
		return addNewTextifier(")</span>");
	}

	@Override
	public Textifier visitArray(final String name) {
		visitAnnotationValue(name);
		stringBuilder.append('{');
		text.add(stringBuilder.toString());
		return addNewTextifier("}");
	}

	private void visitAnnotationValue(final String name) {
		stringBuilder.setLength(0);
		maybeAppendComma(numAnnotationValues++);

		if (name != null) {
			stringBuilder
					.append("<span class=\"variable\">")
					.append(name)
					.append("</span>")
					.append('=');
		}
	}

	// -----------------------------------------------------------------------------------------------
	// Methods
	// -----------------------------------------------------------------------------------------------

	@Override
	public void visitParameter(final String name, final int access) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"comment\">")
				.append("// parameter ");
		appendAccess(access);
		stringBuilder
				.append(' ')
				.append((name == null) ? "<no name>" : name)
				.append("</span>\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public Textifier visitAnnotationDefault() {
		text.add(tab2 + "default=");
		return addNewTextifier("\n");
	}

	@Override
	public Textifier visitAnnotableParameterCount(final int parameterCount, final boolean visible) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"comment\">")
				.append("// annotable parameter count: ")
				.append(parameterCount)
				.append(visible ? " (visible)" : " (invisible)")
				.append("</span>\n");
		text.add(stringBuilder.toString());
		return this;
	}

	@Override
	public Textifier visitParameterAnnotation(
			final int parameter, final String descriptor, final boolean visible) {
		annotation = true;
		stringBuilder.append("<span class=\"annotation\">");
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append('@');
		appendDescriptor(FIELD_DESCRIPTOR, descriptor);
		annotation = false;
		stringBuilder.append('(');
		text.add(stringBuilder.toString());

		stringBuilder.setLength(0);
		stringBuilder
				.append(")")
				.append("</span> ")
				.append("<span class=\"comment\">")
				.append(visible ? "// parameter " : "// invisible, parameter ")
				.append(parameter)
				.append("</span>\n");
		return addNewTextifier(stringBuilder.toString());
	}

	@Override
	public void visitFrame(
			final int type,
			final int numLocal,
			final Object[] local,
			final int numStack,
			final Object[] stack) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(ltab)
				.append("<span class=\"keyword\">")
				.append("FRAME ");
		switch (type) {
		case Opcodes.F_NEW:
		case Opcodes.F_FULL:
			stringBuilder
					.append("FULL")
					.append("</span> ")
					.append('[');
			appendFrameTypes(numLocal, local);
			stringBuilder.append("] [");
			appendFrameTypes(numStack, stack);
			stringBuilder.append(']');
			break;
		case Opcodes.F_APPEND:
			stringBuilder
					.append("APPEND")
					.append("</span> ")
					.append('[');
			appendFrameTypes(numLocal, local);
			stringBuilder.append(']');
			break;
		case Opcodes.F_CHOP:
			stringBuilder
					.append("CHOP")
					.append("</span> ")
					.append(numLocal);
			break;
		case Opcodes.F_SAME:
			stringBuilder
					.append("SAME")
					.append("</span>");
			break;
		case Opcodes.F_SAME1:
			stringBuilder
					.append("SAME1")
					.append("</span> ");
			appendFrameTypes(1, stack);
			break;
		default:
			throw new IllegalArgumentException();
		}

		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitInsn(final int opcode) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append(OPCODES[opcode])
				.append("</span>\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitIntInsn(final int opcode, final int operand) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append(OPCODES[opcode])
				.append(' ');

		if (opcode == Opcodes.NEWARRAY) {
			stringBuilder
					.append(TYPES[operand])
					.append("</span>");
		} else {
			stringBuilder
					.append("</span>")
					.append(Integer.toString(operand));
		}

		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitVarInsn(final int opcode, final int varIndex) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append(OPCODES[opcode])
				.append("</span>")
				.append(' ')
				.append("<span class=\"number\">")
				.append(varIndex)
				.append("</span>")
				.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitTypeInsn(final int opcode, final String type) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append(OPCODES[opcode])
				.append("</span>")
				.append(' ');
		appendDescriptor(INTERNAL_NAME, type);
		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitFieldInsn(
			final int opcode, final String owner, final String name, final String descriptor) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append(OPCODES[opcode])
				.append("</span>")
				.append(' ');
		appendDescriptor(INTERNAL_NAME, owner);
		stringBuilder
				.append('.')
				.append("<span class=\"field\">")
				.append(name)
				.append("</span>")
				.append(" : ");
		appendDescriptor(FIELD_DESCRIPTOR, descriptor);
		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitMethodInsn(
			final int opcode,
			final String owner,
			final String name,
			final String descriptor,
			final boolean isInterface) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append(OPCODES[opcode])
				.append("</span>")
				.append(' ');
		appendDescriptor(INTERNAL_NAME, owner);
		stringBuilder
				.append('.')
				.append("<span class=\"method-name\">")
				.append(name)
				.append("</span> ");
		appendDescriptor(METHOD_DESCRIPTOR, descriptor);

		if (isInterface) {
			stringBuilder.append(" (itf)");
		}

		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitInvokeDynamicInsn(
			final String name,
			final String descriptor,
			final Handle bootstrapMethodHandle,
			final Object... bootstrapMethodArguments) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append("INVOKEDYNAMIC")
				.append("</span> ")
				.append("<span class=\"method-name\">")
				.append(name)
				.append("</span>");
		appendDescriptor(METHOD_DESCRIPTOR, descriptor);
		stringBuilder
				.append(" [")
				.append('\n')
				.append(tab3);
		appendHandle(bootstrapMethodHandle);
		stringBuilder
				.append('\n')
				.append(tab3)
				.append("<span class=\"comment\">")
				.append("// arguments:");
		if (bootstrapMethodArguments.length == 0) {
			stringBuilder
					.append(" none")
					.append("</span>");
		} else {
			stringBuilder.append("</span>\n");

			for (Object value : bootstrapMethodArguments) {
				stringBuilder.append(tab3);

				if (value instanceof String) {
					stringBuilder.append("<span class=\"string\">");
					Printer.appendString(stringBuilder, (String) value);
					stringBuilder.append("</span>");
				} else if (value instanceof Type) {
					Type type = (Type) value;

					if (type.getSort() == Type.METHOD) {
						appendDescriptor(METHOD_DESCRIPTOR, type.getDescriptor());
					} else {
						visitType(type);
					}
				} else if (value instanceof Handle) {
					appendHandle((Handle) value);
				} else {
					stringBuilder.append(value);
				}

				stringBuilder.append(", \n");
			}

			stringBuilder.setLength(stringBuilder.length() - 3);
		}

		stringBuilder
				.append('\n')
				.append(tab2)
				.append("]\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitJumpInsn(final int opcode, final Label label) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append(OPCODES[opcode])
				.append("</span>")
				.append(' ');
		appendLabel(label);
		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitLdcInsn(final Object value) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append("LDC")
				.append("</span> ");
		if (value instanceof String) {
			stringBuilder.append("<span class=\"string\">");
			Printer.appendString(stringBuilder, (String) value);
			stringBuilder.append("</span>");
		} else if (value instanceof Type) {
			stringBuilder
					.append("<span class=\"class-name\">")
					.append(((Type) value).getDescriptor())
					.append("</span>")
					.append("<span class=\"field\">")
					.append(CLASS_SUFFIX)
					.append("</span> ");
		} else {
			stringBuilder
					.append("<span class=\"number\">")
					.append(value)
					.append("</span>");
		}

		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitIincInsn(final int varIndex, final int increment) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append("IINC")
				.append("</span> ")
				.append(varIndex)
				.append(' ')
				.append(increment)
				.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitTableSwitchInsn(
			final int min, final int max, final Label dflt, final Label... labels) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append("TABLESWITCH")
				.append("</span>\n");
		for (int i = 0; i < labels.length; ++i) {
			stringBuilder
					.append(tab3)
					.append(min + i)
					.append(": ");
			appendLabel(labels[i]);
			stringBuilder.append('\n');
		}

		stringBuilder
				.append(tab3)
				.append("default: ");
		appendLabel(dflt);
		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitLookupSwitchInsn(final Label dflt, final int[] keys, final Label[] labels) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append("LOOKUPSWITCH")
				.append("</span>\n");
		for (int i = 0; i < labels.length; ++i) {
			stringBuilder
					.append(tab3)
					.append(keys[i])
					.append(": ");
			appendLabel(labels[i]);
			stringBuilder.append('\n');
		}

		stringBuilder
				.append(tab3)
				.append("default: ");
		appendLabel(dflt);
		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitMultiANewArrayInsn(final String descriptor, final int numDimensions) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append("MULTIANEWARRAY")
				.append("</span> ");
		appendDescriptor(FIELD_DESCRIPTOR, descriptor);
		stringBuilder
				.append(' ')
				.append(numDimensions)
				.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitTryCatchBlock(
			final Label start, final Label end, final Label handler, final String type) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append("TRYCATCHBLOCK")
				.append("</span> ");
		appendLabel(start);
		stringBuilder.append(' ');
		appendLabel(end);
		stringBuilder.append(' ');
		appendLabel(handler);
		stringBuilder.append(' ');
		appendDescriptor(INTERNAL_NAME, type);
		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public Printer visitTryCatchAnnotation(
			final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append("TRYCATCHBLOCK @")
				.append("</span>");
		appendDescriptor(FIELD_DESCRIPTOR, descriptor);
		stringBuilder.append('(');
		text.add(stringBuilder.toString());

		stringBuilder.setLength(0);
		stringBuilder.append(") : ");
		appendTypeReference(typeRef);
		stringBuilder
				.append(", ")
				.append(typePath)
				.append(visible ? "\n" : INVISIBLE);
		return addNewTextifier(stringBuilder.toString());
	}

	@Override
	public void visitLocalVariable(
			final String name,
			final String descriptor,
			final String signature,
			final Label start,
			final Label end,
			final int index) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append("LOCALVARIABLE")
				.append("</span> ")
				.append("<span class=\"variable\">")
				.append(name)
				.append("</span> ");
		appendDescriptor(FIELD_DESCRIPTOR, descriptor);
		stringBuilder.append(' ');
		appendLabel(start);
		stringBuilder.append(' ');
		appendLabel(end);
		stringBuilder
				.append(' ')
				.append("<span class=\"number\">")
				.append(index)
				.append("</span>")
				.append('\n');

		if (signature != null) {
			stringBuilder.append(tab2);
			appendDescriptor(FIELD_SIGNATURE, signature);
			stringBuilder.append(tab2);
			appendJavaDeclaration(name, signature);
		}

		text.add(stringBuilder.toString());
	}

	@Override
	public Printer visitLocalVariableAnnotation(
			final int typeRef,
			final TypePath typePath,
			final Label[] start,
			final Label[] end,
			final int[] index,
			final String descriptor,
			final boolean visible) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"keyword\">")
				.append("LOCALVARIABLE @")
				.append("</span>");
		appendDescriptor(FIELD_DESCRIPTOR, descriptor);
		stringBuilder.append('(');
		text.add(stringBuilder.toString());

		stringBuilder.setLength(0);
		stringBuilder.append(") : ");
		appendTypeReference(typeRef);
		stringBuilder
				.append(", ")
				.append(typePath);
		for (int i = 0; i < start.length; ++i) {
			stringBuilder.append(" [ ");
			appendLabel(start[i]);
			stringBuilder.append(" - ");
			appendLabel(end[i]);
			stringBuilder
					.append(" - ")
					.append(index[i])
					.append(" ]");
		}

		stringBuilder.append(visible ? "\n" : INVISIBLE);
		return addNewTextifier(stringBuilder.toString());
	}

	@Override
	public void visitLineNumber(final int line, final Label start) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"variable\">")
				.append("LINENUMBER")
				.append("</span> ")
				.append("<span class=\"number\">")
				.append(line)
				.append("</span> ")
				.append(' ');
		appendLabel(start);
		stringBuilder.append('\n');
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitMaxs(final int maxStack, final int maxLocals) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"variable\">")
				.append("MAXSTACK")
				.append("</span>")
				.append(" = ")
				.append("<span class=\"number\">")
				.append(maxStack)
				.append("</span>")
				.append('\n');
		text.add(stringBuilder.toString());

		stringBuilder.setLength(0);
		stringBuilder
				.append(tab2)
				.append("<span class=\"variable\">")
				.append("MAXLOCALS")
				.append("</span>")
				.append(" = ")
				.append("<span class=\"number\">")
				.append(maxLocals)
				.append("</span>")
				.append('\n');
		text.add(stringBuilder.toString());
	}

	// -----------------------------------------------------------------------------------------------
	// Common methods
	// -----------------------------------------------------------------------------------------------

	@Override
	public Textifier visitAnnotation(final String descriptor, final boolean visible) {
		annotation = true;
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab)
				.append("<span class=\"annotation\">")
				.append('@');
		appendDescriptor(FIELD_DESCRIPTOR, descriptor);
		stringBuilder
				.append("</span>")
				.append('(');
		text.add(stringBuilder.toString());
		annotation = false;
		return addNewTextifier(visible ? ")\n" : ")" + INVISIBLE);
	}

	@Override
	public Textifier visitTypeAnnotation(
			final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
		annotation = true;
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab)
				.append("<span class=\"annotation\">")
				.append('@');

		appendDescriptor(FIELD_DESCRIPTOR, descriptor);
		stringBuilder
				.append("</span>")
				.append('(');
		text.add(stringBuilder.toString());

		stringBuilder.setLength(0);
		stringBuilder.append(") : ");
		appendTypeReference(typeRef);
		stringBuilder
				.append(", ")
				.append(typePath)
				.append(visible ? "\n" : INVISIBLE);
		annotation = false;
		return addNewTextifier(stringBuilder.toString());
	}

	@Override
	public void visitAttribute(final Attribute attribute) {
		stringBuilder.setLength(0);
		stringBuilder
				.append(tab)
				.append("<span class=\"keyword\">")
				.append("ATTRIBUTE")
				.append("</span> ");
		appendDescriptor(-1, attribute.type);

		if (attribute instanceof TextifierSupport) {
			if (labelNames == null) {
				labelNames = new HashMap<>();
			}

			((TextifierSupport) attribute).textify(stringBuilder, labelNames);
		} else {
			stringBuilder.append(" : unknown\n");
		}

		text.add(stringBuilder.toString());
	}

	// -----------------------------------------------------------------------------------------------
	// Utility methods
	// -----------------------------------------------------------------------------------------------

	/**
	 * Appends a string representation of the given access flags to {@link #stringBuilder}.
	 *
	 * @param accessFlags some access flags.
	 */
	private void appendAccess(final int accessFlags) {
		stringBuilder.append("<span class=\"keyword\">");

		if ((accessFlags & Opcodes.ACC_PUBLIC) != 0) {
			stringBuilder.append("public ");
		}

		if ((accessFlags & Opcodes.ACC_PRIVATE) != 0) {
			stringBuilder.append("private ");
		}

		if ((accessFlags & Opcodes.ACC_PROTECTED) != 0) {
			stringBuilder.append("protected ");
		}

		if ((accessFlags & Opcodes.ACC_FINAL) != 0) {
			stringBuilder.append("final ");
		}

		if ((accessFlags & Opcodes.ACC_STATIC) != 0) {
			stringBuilder.append("static ");
		}

		if ((accessFlags & Opcodes.ACC_SYNCHRONIZED) != 0) {
			stringBuilder.append("synchronized ");
		}

		if ((accessFlags & Opcodes.ACC_VOLATILE) != 0) {
			stringBuilder.append("volatile ");
		}

		if ((accessFlags & Opcodes.ACC_TRANSIENT) != 0) {
			stringBuilder.append("transient ");
		}

		if ((accessFlags & Opcodes.ACC_ABSTRACT) != 0) {
			stringBuilder.append("abstract ");
		}

		if ((accessFlags & Opcodes.ACC_STRICT) != 0) {
			stringBuilder.append("strictfp ");
		}

		if ((accessFlags & Opcodes.ACC_SYNTHETIC) != 0) {
			stringBuilder.append("synthetic ");
		}

		if ((accessFlags & Opcodes.ACC_MANDATED) != 0) {
			stringBuilder.append("mandated ");
		}

		if ((accessFlags & Opcodes.ACC_ENUM) != 0) {
			stringBuilder.append("enum ");
		}

		stringBuilder.append("</span>");
	}

	/**
	 * Appends the hexadecimal value of the given access flags to {@link #stringBuilder}.
	 *
	 * @param accessFlags some access flags.
	 */
	private void appendRawAccess(final int accessFlags) {
		stringBuilder
				.append("<span class=\"comment\">")
				.append("// access flags 0x")
				.append(Integer.toHexString(accessFlags).toUpperCase())
				.append("</span>\n");
	}

	@Override
	protected void appendDescriptor(final int type, final String value) {
		if (type == CLASS_SIGNATURE || type == FIELD_SIGNATURE || type == METHOD_SIGNATURE) {
			if (value != null) {
				stringBuilder
						.append("<span class=\"comment\">")
						.append("// signature ")
						.append(value)
						.append("</span>\n");
			}
		} else {
			stringBuilder
					.append(annotation ? "" : "<span class=\"class-name\">")
					.append(value)
					.append(annotation ? "" : "</span>");
		}
	}

	/**
	 * Appends the Java generic type declaration corresponding to the given signature.
	 *
	 * @param name a class, field or method name.
	 * @param signature a class, field or method signature.
	 */
	private void appendJavaDeclaration(final String name, final String signature) {
		TraceSignatureVisitor traceSignatureVisitor = new TraceSignatureVisitor(access);
		new SignatureReader(signature).accept(traceSignatureVisitor);
		stringBuilder
				.append("<span class=\"comment\">")
				.append("// declaration: ");
		if (traceSignatureVisitor.getReturnType() != null) {
			stringBuilder.append(traceSignatureVisitor.getReturnType());
			stringBuilder.append(' ');
		}

		stringBuilder.append(name);
		stringBuilder.append(traceSignatureVisitor.getDeclaration());

		if (traceSignatureVisitor.getExceptions() != null) {
			stringBuilder
					.append(" throws ")
					.append(traceSignatureVisitor.getExceptions());
		}

		stringBuilder.append("</span>\n");
	}

	@Override
	protected void appendLabel(final Label label) {
		if (labelNames == null) {
			labelNames = new HashMap<>();
		}

		String name = labelNames.get(label);

		if (name == null) {
			name = "L" + labelNames.size();
			labelNames.put(label, name);
		}

		boolean number = numberPattern.matcher(name).matches();

		stringBuilder
				.append(number ? "<span class=\"number\">" : "<span class=\"variable\">")
				.append(name)
				.append("</span>");
	}

	@Override
	protected void appendHandle(final Handle handle) {
		int tag = handle.getTag();
		stringBuilder
				.append("<span class=\"comment\">")
				.append("// handle kind 0x")
				.append(Integer.toHexString(tag))
				.append(" : ");
		boolean isMethodHandle = false;
		switch (tag) {
		case Opcodes.H_GETFIELD:
			stringBuilder.append("GETFIELD");
			break;
		case Opcodes.H_GETSTATIC:
			stringBuilder.append("GETSTATIC");
			break;
		case Opcodes.H_PUTFIELD:
			stringBuilder.append("PUTFIELD");
			break;
		case Opcodes.H_PUTSTATIC:
			stringBuilder.append("PUTSTATIC");
			break;
		case Opcodes.H_INVOKEINTERFACE:
			stringBuilder.append("INVOKEINTERFACE");
			isMethodHandle = true;
			break;
		case Opcodes.H_INVOKESPECIAL:
			stringBuilder.append("INVOKESPECIAL");
			isMethodHandle = true;
			break;
		case Opcodes.H_INVOKESTATIC:
			stringBuilder.append("INVOKESTATIC");
			isMethodHandle = true;
			break;
		case Opcodes.H_INVOKEVIRTUAL:
			stringBuilder.append("INVOKEVIRTUAL");
			isMethodHandle = true;
			break;
		case Opcodes.H_NEWINVOKESPECIAL:
			stringBuilder.append("NEWINVOKESPECIAL");
			isMethodHandle = true;
			break;
		default:
			throw new IllegalArgumentException();
		}

		stringBuilder.append("</span>\n");
		stringBuilder.append(tab3);
		appendDescriptor(INTERNAL_NAME, handle.getOwner());
		stringBuilder.append('.');
		stringBuilder.append(handle.getName());

		if (!isMethodHandle) {
			stringBuilder.append('(');
		}

		appendDescriptor(HANDLE_DESCRIPTOR, handle.getDesc());

		if (!isMethodHandle) {
			stringBuilder.append(')');
		}

		if (handle.isInterface()) {
			stringBuilder.append("itf");
		}
	}

	/**
	 * Appends a comma to {@link #stringBuilder} if the given number is strictly positive.
	 *
	 * @param numValues a number of 'values visited so far', for instance the number of annotation
	 *     values visited so far in an annotation visitor.
	 */
	private void maybeAppendComma(final int numValues) {
		if (numValues > 0) {
			stringBuilder.append(", ");
		}
	}

	/**
	 * Appends a string representation of the given type reference to {@link #stringBuilder}.
	 *
	 * @param typeRef a type reference. See {@link TypeReference}.
	 */
	private void appendTypeReference(final int typeRef) {
		TypeReference typeReference = new TypeReference(typeRef);

		stringBuilder.append("<span class=\"keyword\">");

		switch (typeReference.getSort()) {
		case TypeReference.CLASS_TYPE_PARAMETER:
			stringBuilder
					.append("CLASS_TYPE_PARAMETER")
					.append("</span> ")
					.append(typeReference.getTypeParameterIndex());
			break;
		case TypeReference.METHOD_TYPE_PARAMETER:
			stringBuilder
					.append("METHOD_TYPE_PARAMETER")
					.append("</span> ")
					.append(typeReference.getTypeParameterIndex());
			break;
		case TypeReference.CLASS_EXTENDS:
			stringBuilder
					.append("CLASS_EXTENDS")
					.append("</span> ")
					.append(typeReference.getSuperTypeIndex());
			break;
		case TypeReference.CLASS_TYPE_PARAMETER_BOUND:
			stringBuilder
					.append("CLASS_TYPE_PARAMETER_BOUND")
					.append("</span> ")
					.append(typeReference.getTypeParameterIndex())
					.append(", ")
					.append(typeReference.getTypeParameterBoundIndex());
			break;
		case TypeReference.METHOD_TYPE_PARAMETER_BOUND:
			stringBuilder
					.append("METHOD_TYPE_PARAMETER_BOUND")
					.append("</span> ")
					.append(typeReference.getTypeParameterIndex())
					.append(", ")
					.append(typeReference.getTypeParameterBoundIndex());
			break;
		case TypeReference.FIELD:
			stringBuilder
					.append("FIELD")
					.append("</span>");
			break;
		case TypeReference.METHOD_RETURN:
			stringBuilder
					.append("METHOD_RETURN")
					.append("</span>");
			break;
		case TypeReference.METHOD_RECEIVER:
			stringBuilder
					.append("METHOD_RECEIVER")
					.append("</span>");
			break;
		case TypeReference.METHOD_FORMAL_PARAMETER:
			stringBuilder
					.append("METHOD_FORMAL_PARAMETER")
					.append("</span> ")
					.append(typeReference.getFormalParameterIndex());
			break;
		case TypeReference.THROWS:
			stringBuilder
					.append("THROWS")
					.append("</span> ")
					.append(typeReference.getExceptionIndex());
			break;
		case TypeReference.LOCAL_VARIABLE:
			stringBuilder
					.append("LOCAL_VARIABLE")
					.append("</span>");
			break;
		case TypeReference.RESOURCE_VARIABLE:
			stringBuilder
					.append("RESOURCE_VARIABLE")
					.append("</span>");
			break;
		case TypeReference.EXCEPTION_PARAMETER:
			stringBuilder
					.append("EXCEPTION_PARAMETER")
					.append("</span> ")
					.append(typeReference.getTryCatchBlockIndex());
			break;
		case TypeReference.INSTANCEOF:
			stringBuilder
					.append("INSTANCEOF")
					.append("</span>");
			break;
		case TypeReference.NEW:
			stringBuilder
					.append("NEW")
					.append("</span>");
			break;
		case TypeReference.CONSTRUCTOR_REFERENCE:
			stringBuilder
					.append("CONSTRUCTOR_REFERENCE")
					.append("</span>");
			break;
		case TypeReference.METHOD_REFERENCE:
			stringBuilder
					.append("METHOD_REFERENCE")
					.append("</span>");
			break;
		case TypeReference.CAST:
			stringBuilder
					.append("CAST")
					.append("</span> ")
					.append(typeReference.getTypeArgumentIndex());
			break;
		case TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
			stringBuilder
					.append("CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT")
					.append("</span> ")
					.append(typeReference.getTypeArgumentIndex());
			break;
		case TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT:
			stringBuilder
					.append("METHOD_INVOCATION_TYPE_ARGUMENT")
					.append("</span> ")
					.append(typeReference.getTypeArgumentIndex());
			break;
		case TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
			stringBuilder
					.append("CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT")
					.append("</span> ")
					.append(typeReference.getTypeArgumentIndex());
			break;
		case TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT:
			stringBuilder
					.append("METHOD_REFERENCE_TYPE_ARGUMENT")
					.append("</span> ")
					.append(typeReference.getTypeArgumentIndex());
			break;
		default:
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Appends the given stack map frame types to {@link #stringBuilder}.
	 *
	 * @param numTypes the number of stack map frame types in 'frameTypes'.
	 * @param frameTypes an array of stack map frame types, in the format described in {@link
	 *     org.objectweb.asm.MethodVisitor#visitFrame}.
	 */
	private void appendFrameTypes(final int numTypes, final Object[] frameTypes) {
		for (int i = 0; i < numTypes; ++i) {
			if (i > 0) {
				stringBuilder.append(' ');
			}

			if (frameTypes[i] instanceof String) {
				String descriptor = (String) frameTypes[i];

				if (descriptor.charAt(0) == '[') {
					appendDescriptor(FIELD_DESCRIPTOR, descriptor);
				} else {
					appendDescriptor(INTERNAL_NAME, descriptor);
				}
			} else if (frameTypes[i] instanceof Integer) {
				stringBuilder
						.append("<span class=\"class-name\">")
						.append(FRAME_TYPES.get(((Integer) frameTypes[i]).intValue()))
						.append("</span>");
			} else {
				appendLabel((Label) frameTypes[i]);
			}
		}
	}

	/**
	 * Creates and adds to {@link #text} a new {@link Textifier}, followed by the given string.
	 *
	 * @param endText the text to add to {@link #text} after the textifier. May be {@literal null}.
	 * @return the newly created {@link Textifier}.
	 */
	private Textifier addNewTextifier(final String endText) {
		Textifier textifier = createTextifier();
		text.add(textifier.getText());

		if (endText != null) {
			text.add(endText);
		}

		return textifier;
	}

	@Override
	protected Textifier createTextifier() {
		return new HtmlTextifier(cls, nameType);
	}

	@SuppressWarnings("unchecked")
	private static Object escape(Object o) {
		if (o instanceof List) {
			for (ListIterator<Object> it = ((List<Object>) o).listIterator(); it.hasNext(); ) {
				it.set(escape(it.next()));
			}
		} else if (o instanceof String) {
			String str = (String) o;
			o = HtmlUtil.escape(str, "div", "span");
		} else {
			throw new IllegalStateException("unexpected object type: "+o.getClass());
		}

		return o;
	}

	/**
	 * The type of internal names (see {@link Type#getInternalName()}). See {@link #appendDescriptor}.
	 */
	public static final int INTERNAL_NAME = 0;

	/** The type of field descriptors. See {@link #appendDescriptor}. */
	public static final int FIELD_DESCRIPTOR = 1;

	/** The type of field signatures. See {@link #appendDescriptor}. */
	public static final int FIELD_SIGNATURE = 2;

	/** The type of method descriptors. See {@link #appendDescriptor}. */
	public static final int METHOD_DESCRIPTOR = 3;

	/** The type of method signatures. See {@link #appendDescriptor}. */
	public static final int METHOD_SIGNATURE = 4;

	/** The type of class signatures. See {@link #appendDescriptor}. */
	public static final int CLASS_SIGNATURE = 5;

	/** The type of method handle descriptors. See {@link #appendDescriptor}. */
	public static final int HANDLE_DESCRIPTOR = 9;

	private static final String CLASS_SUFFIX = ".class";
	private static final String DEPRECATED = "<span class=\"comment\">// DEPRECATED</span>\n";
	private static final String RECORD = "<span class=\"comment\">// RECORD</span>\n";
	private static final String INVISIBLE = " <span class=\"comment\">// invisible</span>\n";

	private static final List<String> FRAME_TYPES =
			Collections.unmodifiableList(Arrays.asList("T", "I", "F", "D", "J", "N", "U"));

	private static final Pattern numberPattern = Pattern.compile("-?\\d+(\\.\\d+)?");

	/** The access flags of the visited class. */
	private int access;

	/** The number of annotation values visited so far. */
	private int numAnnotationValues;

	/** Is the current element an annotation? */
	private boolean annotation;

	private final ClassInstance cls;
	private final NameType nameType;
}
