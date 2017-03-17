package matcher.type;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Queue;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

class Analysis {
	static void checkInitializer(FieldInstance field, ClassFeatureExtractor context) {
		if (field.getType().isPrimitive()) return;

		MethodInstance method = field.writeRefs.iterator().next();
		InsnList il = method.asmNode.instructions;
		AbstractInsnNode fieldWrite = null;

		dump(method.asmNode);
		System.out.println("\n------------------------\n");

		for (Iterator<AbstractInsnNode> it = il.iterator(); it.hasNext(); ) {
			AbstractInsnNode aInsn = it.next();

			if (aInsn.getOpcode() == Opcodes.PUTFIELD || aInsn.getOpcode() == Opcodes.PUTSTATIC) {
				FieldInsnNode in = (FieldInsnNode) aInsn;
				ClassInstance cls;

				if (in.name.equals(field.origName)
						&& in.desc.equals(field.getDesc())
						&& (in.owner.equals(field.cls.getName()) || (cls = context.getClassInstance(in.owner)) != null && cls.resolveField(in.name, in.desc) == field)) {
					fieldWrite = in;
					break;
				}
			}
		}

		if (fieldWrite == null) throw new IllegalStateException("can't find field write insn for "+field);

		Interpreter<SourceValue> interpreter = new SourceInterpreter();
		Analyzer<SourceValue> analyzer = new Analyzer<>(interpreter);
		Frame<SourceValue>[] frames;

		try {
			frames = analyzer.analyze(method.cls.getName(), method.asmNode);
			if (frames.length != method.asmNode.instructions.size()) throw new RuntimeException("invalid frame count");
		} catch (AnalyzerException e) {
			throw new RuntimeException(e);
		}

		Textifier textifier = new Textifier();
		MethodVisitor visitor = new TraceMethodVisitor(textifier);

		BitSet tracedPositions = new BitSet();
		Queue<AbstractInsnNode> positionsToTrace = new ArrayDeque<>();

		tracedPositions.set(il.indexOf(fieldWrite));
		positionsToTrace.add(fieldWrite);
		AbstractInsnNode in;

		while ((in = positionsToTrace.poll()) != null) {
			int pos = il.indexOf(in);
			Frame<SourceValue> frame = frames[pos];
			int stackConsumed = getStackDemand(in, frame);

			for (int i = 0; i < stackConsumed; i++) {
				SourceValue value = frame.getStack(frame.getStackSize() - i - 1);

				for (AbstractInsnNode in2 : value.insns) {
					int pos2 = il.indexOf(in2);
					if (tracedPositions.get(pos2)) continue;

					tracedPositions.set(pos2);
					positionsToTrace.add(in2);
				}
			}

			if (in.getType() == AbstractInsnNode.VAR_INSN
					&& in.getOpcode() >= Opcodes.ILOAD && in.getOpcode() <= Opcodes.ALOAD) {
				VarInsnNode vin = (VarInsnNode) in;
				SourceValue value = frame.getLocal(vin.var);

				for (AbstractInsnNode in2 : value.insns) {
					int pos2 = il.indexOf(in2);
					if (tracedPositions.get(pos2)) continue;

					tracedPositions.set(pos2);
					positionsToTrace.add(in2);
				}
			} else if (in.getOpcode() == Opcodes.NEW) { // ensure we track the constructor call when running across a NEW insn
				TypeInsnNode tin = (TypeInsnNode) in;

				for (Iterator<AbstractInsnNode> it = il.iterator(pos + 1); it.hasNext(); ) {
					AbstractInsnNode ain = it.next();

					if (ain.getOpcode() == Opcodes.INVOKESPECIAL) {
						MethodInsnNode in2 = (MethodInsnNode) ain;

						if (in2.name.equals("<init>")
								&& in2.owner.equals(tin.desc)) {
							int pos2 = il.indexOf(in2);

							if (!tracedPositions.get(pos2)) {
								tracedPositions.set(pos2);
								positionsToTrace.add(in2);
							}

							break;
						}
					}
				}
			}
		}

		int pos = 0;

		while ((pos = tracedPositions.nextSetBit(pos)) != -1) {
			System.out.print(pos+": ");

			il.get(pos).accept(visitor);
			System.out.print(textifier.getText().get(0));
			textifier.getText().clear();

			pos++;
		}

		/*		int pos = fieldWritePos;

		for (int i = 0; i < 100; i++) {
			System.out.println(i+" ("+pos+"):");

			Frame<SourceValue> frame = frames[pos];
			Frame<SourceValue> nextFrame = frames[pos + 1];

			int stackConsumed = frame.getStackSize() - nextFrame.getStackSize();

			SourceValue value = frame.getStack(frame.getStackSize() - 1);

			if (value.insns.isEmpty()) {
				System.out.println("empty");
				break;
			}

			for (AbstractInsnNode ain : value.insns) {
				ain.accept(visitor);
				System.out.print(textifier.getText().get(0));
				textifier.getText().clear();
			}

			pos = method.asmNode.instructions.indexOf(value.insns.iterator().next());
		}*/

		/*System.out.println(frame);
		System.out.println("\n------------------------\n");

		dump(frame.getStack(frame.getStackSize() - 1).insns);*/
		System.out.println();
	}

	private static int getStackDemand(AbstractInsnNode ain, Frame<?> frame) {
		switch (ain.getType()) {
		case AbstractInsnNode.INSN:
			switch (ain.getOpcode()) {
			case Opcodes.NOP:
				return 0;
			case Opcodes.ACONST_NULL:
			case Opcodes.ICONST_M1:
			case Opcodes.ICONST_0:
			case Opcodes.ICONST_1:
			case Opcodes.ICONST_2:
			case Opcodes.ICONST_3:
			case Opcodes.ICONST_4:
			case Opcodes.ICONST_5:
			case Opcodes.LCONST_0:
			case Opcodes.LCONST_1:
			case Opcodes.FCONST_0:
			case Opcodes.FCONST_1:
			case Opcodes.FCONST_2:
			case Opcodes.DCONST_0:
			case Opcodes.DCONST_1:
				return 0; // +1
			case Opcodes.IALOAD:
			case Opcodes.LALOAD:
			case Opcodes.FALOAD:
			case Opcodes.DALOAD:
			case Opcodes.AALOAD:
			case Opcodes.BALOAD:
			case Opcodes.CALOAD:
			case Opcodes.SALOAD:
				return 2; // +2
			case Opcodes.IASTORE:
			case Opcodes.LASTORE:
			case Opcodes.FASTORE:
			case Opcodes.DASTORE:
			case Opcodes.AASTORE:
			case Opcodes.BASTORE:
			case Opcodes.CASTORE:
			case Opcodes.SASTORE:
				return 3;
			case Opcodes.POP:
				return 1;
			case Opcodes.POP2:
				return frame.getStack(frame.getStackSize() - 1).getSize() == 1 ? 2 : 1;
			case Opcodes.DUP:
				return 1; // +2
			case Opcodes.DUP_X1:
				return 2; // +3
			case Opcodes.DUP_X2:
				return frame.getStack(frame.getStackSize() - 2).getSize() == 1 ? 3 : 2; // +4/3
			case Opcodes.DUP2:
				return frame.getStack(frame.getStackSize() - 1).getSize() == 1 ? 2 : 1; // +4/2
			case Opcodes.DUP2_X1:
				return frame.getStack(frame.getStackSize() - 1).getSize() == 1 ? 3 : 2; // +5/3
			case Opcodes.DUP2_X2:
				if (frame.getStack(frame.getStackSize() - 1).getSize() == 1) {
					if (frame.getStack(frame.getStackSize() - 3).getSize() == 1) { // 4 single slots
						return 4; // +6
					} else { // single at top, then double
						return 3; // +5
					}
				} else if (frame.getStack(frame.getStackSize() - 3).getSize() == 1) { // double at top, then 2 single
					return 3; // +4
				} else { // 2 double slots
					return 2; // +3
				}
			case Opcodes.SWAP:
				return 2; // +2
			case Opcodes.IADD:
			case Opcodes.LADD:
			case Opcodes.FADD:
			case Opcodes.DADD:
			case Opcodes.ISUB:
			case Opcodes.LSUB:
			case Opcodes.FSUB:
			case Opcodes.DSUB:
			case Opcodes.IMUL:
			case Opcodes.LMUL:
			case Opcodes.FMUL:
			case Opcodes.DMUL:
			case Opcodes.IDIV:
			case Opcodes.LDIV:
			case Opcodes.FDIV:
			case Opcodes.DDIV:
			case Opcodes.IREM:
			case Opcodes.LREM:
			case Opcodes.FREM:
			case Opcodes.DREM:
				return 2; // +1
			case Opcodes.INEG:
			case Opcodes.LNEG:
			case Opcodes.FNEG:
			case Opcodes.DNEG:
				return 1; // +1
			case Opcodes.ISHL:
			case Opcodes.LSHL:
			case Opcodes.ISHR:
			case Opcodes.LSHR:
			case Opcodes.IUSHR:
			case Opcodes.LUSHR:
			case Opcodes.IAND:
			case Opcodes.LAND:
			case Opcodes.IOR:
			case Opcodes.LOR:
			case Opcodes.IXOR:
			case Opcodes.LXOR:
				return 2; // +1
			case Opcodes.I2L:
			case Opcodes.I2F:
			case Opcodes.I2D:
			case Opcodes.L2I:
			case Opcodes.L2F:
			case Opcodes.L2D:
			case Opcodes.F2I:
			case Opcodes.F2L:
			case Opcodes.F2D:
			case Opcodes.D2I:
			case Opcodes.D2L:
			case Opcodes.D2F:
			case Opcodes.I2B:
			case Opcodes.I2C:
			case Opcodes.I2S:
				return 1; // +1
			case Opcodes.LCMP:
			case Opcodes.FCMPL:
			case Opcodes.FCMPG:
			case Opcodes.DCMPL:
			case Opcodes.DCMPG:
				return 2; // +1
			case Opcodes.IRETURN:
			case Opcodes.LRETURN:
			case Opcodes.FRETURN:
			case Opcodes.DRETURN:
			case Opcodes.ARETURN:
				return 1;
			case Opcodes.RETURN:
				return 0;
			case Opcodes.ARRAYLENGTH:
				return 1; // +1
			case Opcodes.ATHROW:
				return 1; // ->1
			case Opcodes.MONITORENTER:
			case Opcodes.MONITOREXIT:
				return 1;
			default:
				throw new IllegalArgumentException("unknown insn opcode "+ain.getOpcode());
			}
		case AbstractInsnNode.INT_INSN:
			switch (ain.getOpcode()) {
			case Opcodes.BIPUSH:
			case Opcodes.SIPUSH:
				return 0; // +1
			case Opcodes.NEWARRAY:
				return 1; // +1
			default:
				throw new IllegalArgumentException("unknown int insn opcode "+ain.getOpcode());
			}
		case AbstractInsnNode.VAR_INSN:
			switch (ain.getOpcode()) {
			case Opcodes.ILOAD:
			case Opcodes.LLOAD:
			case Opcodes.FLOAD:
			case Opcodes.DLOAD:
			case Opcodes.ALOAD:
				return 0; // +1
			case Opcodes.ISTORE:
			case Opcodes.LSTORE:
			case Opcodes.FSTORE:
			case Opcodes.DSTORE:
			case Opcodes.ASTORE:
				return 1;
			case Opcodes.RET:
				return 0;
			default:
				throw new IllegalArgumentException("unknown var insn opcode "+ain.getOpcode());
			}
		case AbstractInsnNode.TYPE_INSN:
			switch (ain.getOpcode()) {
			case Opcodes.NEW:
				return 0; // +1
			case Opcodes.ANEWARRAY:
				return 1; // +1
			case Opcodes.CHECKCAST:
			case Opcodes.INSTANCEOF:
				return 1; // +1
			default:
				throw new IllegalArgumentException("unknown type insn opcode "+ain.getOpcode());
			}
		case AbstractInsnNode.FIELD_INSN:
			switch (ain.getOpcode()) {
			case Opcodes.GETSTATIC:
				return 0; // +1
			case Opcodes.PUTSTATIC:
				return 1;
			case Opcodes.GETFIELD:
				return 1; // +1
			case Opcodes.PUTFIELD:
				return 2;
			default:
				throw new IllegalArgumentException("unknown field insn opcode "+ain.getOpcode());
			}
		case AbstractInsnNode.METHOD_INSN:
			return Type.getArgumentTypes(((MethodInsnNode) ain).desc).length + (ain.getOpcode() != Opcodes.INVOKESTATIC ? 1 : 0); // +1 if ret type != void
		case AbstractInsnNode.INVOKE_DYNAMIC_INSN:
			return Type.getArgumentTypes(((InvokeDynamicInsnNode) ain).desc).length; // +1 if ret type != void
		case AbstractInsnNode.JUMP_INSN:
			switch (ain.getOpcode()) {
			case Opcodes.IFEQ:
			case Opcodes.IFNE:
			case Opcodes.IFLT:
			case Opcodes.IFGE:
			case Opcodes.IFGT:
			case Opcodes.IFLE:
				return 1;
			case Opcodes.IF_ICMPEQ:
			case Opcodes.IF_ICMPNE:
			case Opcodes.IF_ICMPLT:
			case Opcodes.IF_ICMPGE:
			case Opcodes.IF_ICMPGT:
			case Opcodes.IF_ICMPLE:
			case Opcodes.IF_ACMPEQ:
			case Opcodes.IF_ACMPNE:
				return 2;
			case Opcodes.GOTO:
				return 0;
			case Opcodes.JSR:
				return 0; // +1
			case Opcodes.IFNULL:
			case Opcodes.IFNONNULL:
				return 1;
			default:
				throw new IllegalArgumentException("unknown jump insn opcode "+ain.getOpcode());
			}
		case AbstractInsnNode.LABEL:
			return 0;
		case AbstractInsnNode.LDC_INSN:
			return 0; // +1
		case AbstractInsnNode.IINC_INSN:
			return 0;
		case AbstractInsnNode.TABLESWITCH_INSN:
			return 1;
		case AbstractInsnNode.LOOKUPSWITCH_INSN:
			return 1;
		case AbstractInsnNode.MULTIANEWARRAY_INSN:
			return ((MultiANewArrayInsnNode) ain).dims; // +1
		case AbstractInsnNode.FRAME:
			return 0;
		case AbstractInsnNode.LINE:
			return 0;
		default:
			throw new IllegalArgumentException("unknown insn type "+ain.getType()+" for opcode "+ain.getOpcode()+", in "+ain.getClass().getName());
		}
	}

	private static void dump(MethodNode method) {
		Textifier textifier = new Textifier();
		method.accept(new TraceMethodVisitor(textifier));

		StringWriter writer = new StringWriter();

		try (PrintWriter pw = new PrintWriter(writer)) {
			textifier.print(pw);
		}

		System.out.println(writer.toString());
	}

	private static void dump(Iterable<AbstractInsnNode> il) {
		Textifier textifier = new Textifier();
		MethodVisitor visitor = new TraceMethodVisitor(textifier);

		for (Iterator<AbstractInsnNode> it = il.iterator(); it.hasNext(); ) {
			AbstractInsnNode in = it.next();
			in.accept(visitor);
		}

		StringWriter writer = new StringWriter();

		try (PrintWriter pw = new PrintWriter(writer)) {
			textifier.print(pw);
		}

		System.out.println(writer.toString());
	}
}
