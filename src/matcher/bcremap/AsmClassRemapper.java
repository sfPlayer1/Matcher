package matcher.bcremap;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.tree.ClassNode;

import matcher.Util;

public class AsmClassRemapper extends ClassRemapper {
	public static void process(ClassNode source, AsmRemapper remapper, ClassVisitor sink) {
		source.accept(new AsmClassRemapper(sink, remapper));
	}

	private AsmClassRemapper(ClassVisitor cv, AsmRemapper remapper) {
		super(cv, remapper);

		this.remapper = remapper;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		if (methodName != null) throw new IllegalStateException("already vising a method");

		methodName = name;
		methodDesc = desc;

		return super.visitMethod(access, name, desc, signature, exceptions);
	}

	@Override
	protected MethodVisitor createMethodRemapper(MethodVisitor mv) {
		return new AsmMethodRemapper(mv, remapper);
	}

	private class AsmMethodRemapper extends MethodRemapper {
		public AsmMethodRemapper(MethodVisitor mv, AsmRemapper remapper) {
			super(mv, remapper);

			this.remapper = remapper;
		}

		@Override
		public void visitParameter(String name, int access) {
			checkState();
			name = remapper.mapArgName(className, methodName, methodDesc, name, argsVisited);
			argsVisited++;
			super.visitParameter(name, access);
		}

		private void checkParameters() {
			if (argsVisited > 0 || methodDesc.startsWith("()")) return;

			int argCount = Type.getArgumentTypes(methodDesc).length;

			for (int i = 0; i < argCount; i++) {
				visitParameter(null, 0);
			}
		}

		@Override
		public AnnotationVisitor visitAnnotationDefault() {
			checkParameters();
			return super.visitAnnotationDefault();
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			checkParameters();
			return super.visitAnnotation(descriptor, visible);
		}

		@Override
		public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
			checkParameters();
			super.visitAnnotableParameterCount(parameterCount, visible);
		}

		@Override
		public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
			checkParameters();
			return super.visitParameterAnnotation(parameter, descriptor, visible);
		}

		@Override
		public void visitAttribute(Attribute attribute) {
			checkParameters();
			super.visitAttribute(attribute);
		}

		@Override
		public void visitCode() {
			checkParameters();
			super.visitCode();
		}

		@Override
		public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc, final boolean itf) {
			checkState();
			insnIndex++;

			if (mv != null) {
				mv.visitMethodInsn(opcode, remapper.mapType(owner),
						remapper.mapMethodName(owner, name, desc, itf),
						remapper.mapMethodDesc(desc), itf);
			}
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
			checkState();
			insnIndex++;

			if (mv == null) return;

			Handle target = Util.getTargetHandle(bsm, bsmArgs);

			if (target != null) {
				name = remapper.mapMethodName(target.getOwner(), target.getName(), target.getDesc(), target.isInterface());
			} else {
				String owner = Type.getType(desc).getReturnType().getInternalName();

				name = remapper.mapArbitraryInvokeDynamicMethodName(owner, name);
			}

			boolean copied = false;

			for (int i = 0; i < bsmArgs.length; i++) {
				Object oldArg = bsmArgs[i];
				Object newArg = remapper.mapValue(oldArg);

				if (newArg != oldArg) {
					if (!copied) {
						bsmArgs = Arrays.copyOf(bsmArgs, bsmArgs.length);
						copied = true;
					}

					bsmArgs[i] = newArg;
				}
			}

			mv.visitInvokeDynamicInsn(name, remapper.mapMethodDesc(desc), (Handle) remapper.mapValue(bsm), bsmArgs);
		}

		@Override
		public void visitFrame(final int type, final int nLocal, final Object[] local, final int nStack, final Object[] stack) {
			checkState();
			insnIndex++;

			super.visitFrame(type, nLocal, local, nStack, stack);
		}

		@Override
		public void visitInsn(final int opcode) {
			checkState();
			insnIndex++;

			super.visitInsn(opcode);
		}

		@Override
		public void visitIntInsn(final int opcode, final int operand) {
			checkState();
			insnIndex++;

			super.visitIntInsn(opcode, operand);
		}

		@Override
		public void visitVarInsn(final int opcode, final int var) {
			checkState();
			insnIndex++;

			super.visitVarInsn(opcode, var);
		}

		@Override
		public void visitTypeInsn(final int opcode, final String type) {
			checkState();
			insnIndex++;

			super.visitTypeInsn(opcode, type);
		}

		@Override
		public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
			checkState();
			insnIndex++;

			super.visitFieldInsn(opcode, owner, name, desc);
		}

		@Override
		public void visitJumpInsn(final int opcode, final Label label) {
			checkState();
			insnIndex++;
			super.visitJumpInsn(opcode, label);
		}

		@Override
		public void visitLabel(final Label label) {
			checkState();
			if (insnIndex == 0 && !labels.isEmpty()) throw new IllegalStateException();

			labels.put(label, insnIndex);
			insnIndex++;

			super.visitLabel(label);
		}

		@Override
		public void visitLdcInsn(final Object cst) {
			checkState();
			insnIndex++;

			super.visitLdcInsn(cst);
		}

		@Override
		public void visitIincInsn(final int var, final int increment) {
			checkState();
			insnIndex++;

			super.visitIincInsn(var, increment);
		}

		@Override
		public void visitTableSwitchInsn(final int min, final int max, final Label dflt, final Label... labels) {
			checkState();
			insnIndex++;

			super.visitTableSwitchInsn(min, max, dflt, labels);
		}

		@Override
		public void visitLookupSwitchInsn(final Label dflt, final int[] keys, final Label[] labels) {
			checkState();
			insnIndex++;

			super.visitLookupSwitchInsn(dflt, keys, labels);
		}

		@Override
		public void visitMultiANewArrayInsn(final String desc, final int dims) {
			checkState();
			insnIndex++;

			super.visitMultiANewArrayInsn(desc, dims);
		}

		@Override
		public void visitLineNumber(final int line, final Label start) {
			checkState();
			insnIndex++;

			super.visitLineNumber(line, start);
		}

		@Override
		public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
			checkState();

			int startInsn = labels.get(start);
			int endInsn = labels.get(end);

			name = remapper.mapLocalVariableName(className, methodName, methodDesc, name, desc, index, startInsn, endInsn);

			super.visitLocalVariable(name, desc, signature, start, end, index);
		}

		@Override
		public void visitEnd() {
			checkState();
			checkParameters();

			insnIndex = 0;
			labels.clear();
			argsVisited = 0;
			methodName = methodDesc = null;

			super.visitEnd();
		}

		private void checkState() {
			if (methodName == null) throw new IllegalStateException("not visiting a method");
		}

		protected final AsmRemapper remapper;

		protected int insnIndex;
		protected Map<Label, Integer> labels = new IdentityHashMap<>();
		private int argsVisited;
	}

	protected final AsmRemapper remapper;
	protected String methodName;
	protected String methodDesc;
}
