package matcher.type;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import matcher.NameType;
import matcher.Util;

class Analysis {
	static void analyzeMethod(MethodInstance method, CommonClasses common) {
		MethodNode asmNode = method.getAsmNode();
		if (asmNode == null || (asmNode.access & Opcodes.ACC_ABSTRACT) != 0 || asmNode.instructions.size() == 0) return;

		System.out.println(method.getDisplayName(NameType.MAPPED_PLAIN, true));
		dump(asmNode);

		StateRecorder rec = new StateRecorder(method, common);
		InsnList il = asmNode.instructions;

		Map<AbstractInsnNode, int[]> exitPoints = new IdentityHashMap<>();
		exitPoints.put(null, new int[] { 0 });

		Queue<QueueElement> queue = new ArrayDeque<>();
		queue.add(new QueueElement(0, rec.getState()));
		Set<QueueElement> queued = new HashSet<>();
		queued.add(queue.peek());

		boolean first = true;
		QueueElement element;

		while ((element = queue.poll()) != null || queueTryCatchBlocks(method, rec, queue, queued) && (element = queue.poll()) != null) {
			if (!rec.jump(element.dstIndex, element.srcState) && !first) continue;
			first = false;

			insnLoop: for (int idx = element.dstIndex; idx < il.size(); idx++) {
				assert rec.idx == idx;

				AbstractInsnNode ain = il.get(idx);
				int inType = ain.getType();

				if (inType == AbstractInsnNode.LABEL || inType == AbstractInsnNode.FRAME || inType == AbstractInsnNode.LINE) {
					if (!rec.next()) break;
					continue;
				}

				int op = ain.getOpcode();

				switch (op) {
				// InsnNode
				case Opcodes.NOP:
					break;
				case Opcodes.ACONST_NULL:
					rec.push(common.NULL, rec.getNextVarId(VarSource.Constant));
					break;
				case Opcodes.ICONST_0:
				case Opcodes.ICONST_1:
					rec.push(common.BOOLEAN, rec.getNextVarId(VarSource.Constant));
					break;
				case Opcodes.ICONST_M1:
				case Opcodes.ICONST_2:
				case Opcodes.ICONST_3:
				case Opcodes.ICONST_4:
				case Opcodes.ICONST_5:
					rec.push(common.BYTE, rec.getNextVarId(VarSource.Constant));
					break;
				case Opcodes.LCONST_0:
				case Opcodes.LCONST_1:
					rec.push(common.LONG, rec.getNextVarId(VarSource.Constant));
					break;
				case Opcodes.FCONST_0:
				case Opcodes.FCONST_1:
				case Opcodes.FCONST_2:
					rec.push(common.FLOAT, rec.getNextVarId(VarSource.Constant));
					break;
				case Opcodes.DCONST_0:
				case Opcodes.DCONST_1:
					rec.push(common.DOUBLE, rec.getNextVarId(VarSource.Constant));
					break;
				case Opcodes.IALOAD:
				case Opcodes.BALOAD:
				case Opcodes.CALOAD:
				case Opcodes.SALOAD:
					rec.pop2();
					rec.push(common.INT, rec.getNextVarId(VarSource.ArrayElement));
					break;
				case Opcodes.LALOAD:
					rec.pop2();
					rec.push(common.LONG, rec.getNextVarId(VarSource.ArrayElement));
					break;
				case Opcodes.FALOAD:
					rec.pop2();
					rec.push(common.FLOAT, rec.getNextVarId(VarSource.ArrayElement));
					break;
				case Opcodes.DALOAD:
					rec.pop2();
					rec.push(common.DOUBLE, rec.getNextVarId(VarSource.ArrayElement));
					break;
				case Opcodes.AALOAD: {
					rec.pop(); // idx
					Variable array = rec.pop();
					rec.push(array.type.getElementClassShallow(true), rec.getNextVarId(VarSource.ArrayElement));
					break;
				}
				case Opcodes.IASTORE:
				case Opcodes.BASTORE:
				case Opcodes.CASTORE:
				case Opcodes.SASTORE:
				case Opcodes.FASTORE:
				case Opcodes.AASTORE:
					rec.pop();
					rec.pop2();
					break;
				case Opcodes.LASTORE:
				case Opcodes.DASTORE:
					rec.pop2();
					rec.pop2();
					break;
				case Opcodes.POP:
					rec.pop();
					break;
				case Opcodes.POP2:
					rec.pop2();
					break;
				case Opcodes.DUP:
					rec.push(rec.peek());
					break;
				case Opcodes.DUP_X1: {
					Variable a = rec.pop();
					Variable b = rec.pop();
					rec.push(a);
					rec.push(b);
					rec.push(a);
					break;
				}
				case Opcodes.DUP_X2: {
					Variable a = rec.pop();

					if (rec.isTopDoubleSlot()) {
						Variable b = rec.popDouble();
						rec.push(a);
						rec.push(b);
					} else {
						Variable b = rec.pop();
						Variable c = rec.pop();
						rec.push(a);
						rec.push(c);
						rec.push(b);
					}

					rec.push(a);
					break;
				}
				case Opcodes.DUP2:
					if (rec.isTopDoubleSlot()) {
						rec.push(rec.peekDouble());
					} else {
						Variable a = rec.pop();
						Variable b = rec.peek();
						rec.push(a);
						rec.push(b);
						rec.push(a);
					}

					break;
				case Opcodes.DUP2_X1:
					if (rec.isTopDoubleSlot()) {
						Variable a = rec.popDouble();
						Variable b = rec.pop();
						rec.push(a);
						rec.push(b);
						rec.push(a);
					} else {
						Variable a = rec.pop();
						Variable b = rec.pop();
						Variable c = rec.pop();
						rec.push(b);
						rec.push(a);
						rec.push(c);
						rec.push(b);
						rec.push(a);
					}

					break;
				case Opcodes.DUP2_X2:
					if (rec.isTopDoubleSlot()) {
						Variable a = rec.popDouble();

						if (rec.isTopDoubleSlot()) {
							Variable b = rec.popDouble();
							rec.push(a);
							rec.push(b);
						} else {
							Variable b = rec.pop();
							Variable c = rec.pop();
							rec.push(a);
							rec.push(c);
							rec.push(b);
						}

						rec.push(a);
					} else {
						Variable a = rec.pop();
						Variable b = rec.pop();

						if (rec.isTopDoubleSlot()) {
							Variable c = rec.popDouble();
							rec.push(b);
							rec.push(a);
							rec.push(c);
						} else {
							Variable c = rec.pop();
							Variable d = rec.pop();
							rec.push(b);
							rec.push(a);
							rec.push(d);
							rec.push(c);
						}

						rec.push(b);
						rec.push(a);
					}

					break;
				case Opcodes.SWAP: {
					Variable a = rec.pop();
					Variable b = rec.pop();
					rec.push(a);
					rec.push(b);
					break;
				}
				case Opcodes.IADD:
				case Opcodes.FADD:
				case Opcodes.ISUB:
				case Opcodes.FSUB:
				case Opcodes.IMUL:
				case Opcodes.FMUL:
				case Opcodes.IDIV:
				case Opcodes.FDIV:
				case Opcodes.IREM:
				case Opcodes.FREM:
				case Opcodes.ISHL:
				case Opcodes.ISHR:
				case Opcodes.IUSHR:
				case Opcodes.IAND:
				case Opcodes.IOR:
				case Opcodes.IXOR: {
					rec.pop();
					Variable arg1 = rec.pop();
					rec.push(arg1.type, rec.getNextVarId(VarSource.Computed));
					break;
				}
				case Opcodes.LADD:
				case Opcodes.DADD:
				case Opcodes.LSUB:
				case Opcodes.DSUB:
				case Opcodes.LMUL:
				case Opcodes.DMUL:
				case Opcodes.LDIV:
				case Opcodes.DDIV:
				case Opcodes.LREM:
				case Opcodes.DREM:
				case Opcodes.LAND:
				case Opcodes.LOR:
				case Opcodes.LXOR: {
					rec.popDouble();
					Variable arg1 = rec.popDouble();
					rec.push(arg1.type, rec.getNextVarId(VarSource.Computed));
					break;
				}
				case Opcodes.LSHL:
				case Opcodes.LSHR:
				case Opcodes.LUSHR: {
					rec.pop();
					Variable var = rec.popDouble();
					rec.push(var.type, rec.getNextVarId(VarSource.Computed));
					break;
				}
				case Opcodes.INEG:
				case Opcodes.FNEG:
					rec.push(rec.pop().type, rec.getNextVarId(VarSource.Computed));
					break;
				case Opcodes.LNEG:
				case Opcodes.DNEG:
					rec.push(rec.popDouble().type, rec.getNextVarId(VarSource.Computed));
					break;
				case Opcodes.I2L:
				case Opcodes.F2L:
					rec.pop();
					rec.push(common.LONG, rec.getNextVarId(VarSource.Cast));
					break;
				case Opcodes.I2F:
					rec.pop();
					rec.push(common.FLOAT, rec.getNextVarId(VarSource.Cast));
					break;
				case Opcodes.I2D:
				case Opcodes.F2D:
					rec.pop();
					rec.push(common.DOUBLE, rec.getNextVarId(VarSource.Cast));
					break;
				case Opcodes.L2I:
				case Opcodes.D2I:
					rec.pop2();
					rec.push(common.INT, rec.getNextVarId(VarSource.Cast));
					break;
				case Opcodes.L2F:
				case Opcodes.D2F:
					rec.pop2();
					rec.push(common.FLOAT, rec.getNextVarId(VarSource.Cast));
					break;
				case Opcodes.L2D:
					rec.pop2();
					rec.push(common.DOUBLE, rec.getNextVarId(VarSource.Cast));
					break;
				case Opcodes.F2I:
					rec.pop();
					rec.push(common.INT, rec.getNextVarId(VarSource.Cast));
					break;
				case Opcodes.D2L:
					rec.pop2();
					rec.push(common.LONG, rec.getNextVarId(VarSource.Cast));
					break;
				case Opcodes.I2B:
					rec.pop();
					rec.push(common.BYTE, rec.getNextVarId(VarSource.Cast));
					break;
				case Opcodes.I2C:
					rec.pop();
					rec.push(common.CHAR, rec.getNextVarId(VarSource.Cast));
					break;
				case Opcodes.I2S:
					rec.pop();
					rec.push(common.SHORT, rec.getNextVarId(VarSource.Cast));
					break;
				case Opcodes.LCMP:
				case Opcodes.DCMPL:
				case Opcodes.DCMPG:
					rec.pop2();
					rec.pop2();
					rec.push(common.INT, rec.getNextVarId(VarSource.Computed));
					break;
				case Opcodes.FCMPL:
				case Opcodes.FCMPG:
					rec.pop2();
					rec.push(common.INT, rec.getNextVarId(VarSource.Computed));
					break;
				case Opcodes.IRETURN:
				case Opcodes.LRETURN:
				case Opcodes.FRETURN:
				case Opcodes.DRETURN:
				case Opcodes.ARETURN:
				case Opcodes.RETURN:
					if (!exitPoints.containsKey(ain)) exitPoints.put(ain, null);
					break insnLoop;
				case Opcodes.ARRAYLENGTH:
					rec.pop();
					rec.push(common.INT, rec.getNextVarId(VarSource.Computed));
					break;
				case Opcodes.ATHROW: {
					Variable ex = rec.pop();
					rec.clearStack();
					rec.push(ex.type, rec.getNextVarId(VarSource.IntException)); // same object, but new scope
					LabelNode handler = null;

					for (TryCatchBlockNode n : asmNode.tryCatchBlocks) {
						if (il.indexOf(n.start) <= idx && il.indexOf(n.end) > idx && (n.type == null || method.getEnv().getClsByName(n.type).isAssignableFrom(ex.type))) {
							handler = n.handler;
							break;
						}
					}

					if (handler != null) {
						int dstIdx = il.indexOf(handler);
						if (!exitPoints.containsKey(ain)) exitPoints.put(ain, new int[] { dstIdx });
						rec.jump(dstIdx);
						idx = dstIdx;
					} else {
						if (!exitPoints.containsKey(ain)) exitPoints.put(ain, null);
						break insnLoop;
					}

					break;
				}
				case Opcodes.MONITORENTER:
				case Opcodes.MONITOREXIT: {
					rec.pop();
					break;
				}

				// IntInsnNode
				case Opcodes.BIPUSH:
					rec.push(common.BYTE, rec.getNextVarId(VarSource.Constant));
					break;
				case Opcodes.SIPUSH:
					rec.push(common.SHORT, rec.getNextVarId(VarSource.Constant));
					break;
				case Opcodes.NEWARRAY: {
					rec.pop();
					String arrayType;

					switch (((IntInsnNode) ain).operand) {
					case Opcodes.T_BOOLEAN: arrayType = "[Z"; break;
					case Opcodes.T_CHAR: arrayType = "[C"; break;
					case Opcodes.T_FLOAT: arrayType = "[F"; break;
					case Opcodes.T_DOUBLE: arrayType = "[D"; break;
					case Opcodes.T_BYTE: arrayType = "[B"; break;
					case Opcodes.T_SHORT: arrayType = "[S"; break;
					case Opcodes.T_INT: arrayType = "[I"; break;
					case Opcodes.T_LONG: arrayType = "[J"; break;
					default:
						throw new UnsupportedOperationException("unknown NEWARRAY operand: "+((IntInsnNode) ain).operand);
					}

					rec.push(method.getEnv().getCreateClassInstance(arrayType), rec.getNextVarId(VarSource.New));
					break;
				}

				// VarInsnNode
				case Opcodes.ILOAD:
				case Opcodes.LLOAD:
				case Opcodes.FLOAD:
				case Opcodes.DLOAD:
				case Opcodes.ALOAD: {
					int lvtIdx = ((VarInsnNode) ain).var;
					rec.push(rec.get(lvtIdx), rec.getId(lvtIdx));
					break;
				}
				case Opcodes.ISTORE:
				case Opcodes.FSTORE:
				case Opcodes.ASTORE:
					rec.set(((VarInsnNode) ain).var, rec.pop());
					break;
				case Opcodes.LSTORE:
				case Opcodes.DSTORE:
					rec.set(((VarInsnNode) ain).var, rec.popDouble());
					break;
				case Opcodes.RET: {
					throw new UnsupportedOperationException("RET is not supported");
				}
				// TypeInsnNode
				case Opcodes.NEW:
					rec.push(method.getEnv().getCreateClassInstance(ClassInstance.getId(((TypeInsnNode) ain).desc)), rec.getNextVarId(VarSource.New));
					break;
				case Opcodes.ANEWARRAY: {
					String desc = ((TypeInsnNode) ain).desc;

					if (desc.startsWith("[")) {
						desc = "["+desc;
					} else {
						assert !desc.startsWith("L");
						desc = "[L"+desc+";";
					}

					rec.pop();
					rec.push(method.getEnv().getCreateClassInstance(desc), rec.getNextVarId(VarSource.New));
					break;
				}
				case Opcodes.CHECKCAST:
					rec.pop();
					rec.push(method.getEnv().getCreateClassInstance(ClassInstance.getId(((TypeInsnNode) ain).desc)), rec.getNextVarId(VarSource.Cast)); // TODO: ignore if widening cast?
					break;
				case Opcodes.INSTANCEOF: {
					rec.pop();
					rec.push(common.INT, rec.getNextVarId(VarSource.Computed));
					break;
				}
				// FieldInsnNode
				case Opcodes.GETSTATIC:
				case Opcodes.PUTSTATIC:
				case Opcodes.GETFIELD:
				case Opcodes.PUTFIELD: {
					FieldInsnNode in = (FieldInsnNode) ain;
					FieldInstance field = method.getEnv().getClsByName(in.owner).resolveField(in.name, in.desc);
					boolean isWrite = (op == Opcodes.PUTFIELD || op == Opcodes.PUTSTATIC);

					if (isWrite) { // put*
						if (field.getType().getSlotSize() == 1) {
							rec.pop();
						} else {
							rec.popDouble();
						}
					}

					if (op == Opcodes.GETFIELD || op == Opcodes.PUTFIELD) { // obj ref
						rec.pop();
					}

					if (!isWrite) rec.push(field.getType(), rec.getNextVarId(VarSource.Field)); // get*

					break;
				}
				// MethodInsnNode
				case Opcodes.INVOKEVIRTUAL:
				case Opcodes.INVOKESPECIAL:
				case Opcodes.INVOKESTATIC:
				case Opcodes.INVOKEINTERFACE: {
					MethodInsnNode in = (MethodInsnNode) ain;
					handleMethodInvocation(method.getEnv(), in.owner, in.name, in.desc, in.itf, op == Opcodes.INVOKESTATIC, rec);
					break;
				}
				// InvokeDynamicInsnNode:
				case Opcodes.INVOKEDYNAMIC: {
					InvokeDynamicInsnNode in = (InvokeDynamicInsnNode) ain;

					int pos = in.desc.lastIndexOf(')');
					if (pos == -1 || pos == in.desc.length() - 1) throw new RuntimeException("invalid invokedynamic desc");

					if (pos != 1) { // not just ()*
						Type[] args = Type.getArgumentTypes(in.desc);

						for (int i = args.length - 1; i >= 0; i--) {
							ClassInstance argType = method.getEnv().getCreateClassInstance(args[i].getDescriptor());

							if (argType.getSlotSize() == 1) {
								rec.pop();
							} else {
								rec.popDouble();
							}
						}
					}

					if (pos != in.desc.length() - 2 || in.desc.charAt(pos + 1) != 'V') { // not *)V
						ClassInstance retType = method.getEnv().getCreateClassInstance(in.desc.substring(pos + 1));
						rec.push(retType, rec.getNextVarId(VarSource.MethodRet));
					}

					break;
				}
				// JumpInsnNode
				case Opcodes.IF_ICMPEQ:
				case Opcodes.IF_ICMPNE:
				case Opcodes.IF_ICMPLT:
				case Opcodes.IF_ICMPGE:
				case Opcodes.IF_ICMPGT:
				case Opcodes.IF_ICMPLE:
				case Opcodes.IF_ACMPEQ:
				case Opcodes.IF_ACMPNE:
					rec.pop();
					// fall-through w. 2nd pop
				case Opcodes.IFEQ:
				case Opcodes.IFNE:
				case Opcodes.IFLT:
				case Opcodes.IFGE:
				case Opcodes.IFGT:
				case Opcodes.IFLE:
				case Opcodes.IFNULL:
				case Opcodes.IFNONNULL:
					rec.pop();
					// fall-through
				case Opcodes.GOTO: {
					JumpInsnNode in = (JumpInsnNode) ain;
					int dstIdx = il.indexOf(in.label);

					if (dstIdx != idx + 1) {
						if (op == Opcodes.GOTO) {
							if (!exitPoints.containsKey(ain)) exitPoints.put(ain, new int[] { dstIdx });
							if (!rec.jump(dstIdx)) break insnLoop;
							idx = dstIdx;
						} else {
							if (!exitPoints.containsKey(ain)) exitPoints.put(ain, new int[] { dstIdx, idx + 1 });

							QueueElement e = new QueueElement(dstIdx, rec.getState());
							if (queued.add(e)) queue.add(e);
						}
					} else { // no-op jump
						if (!exitPoints.containsKey(ain)) exitPoints.put(ain, new int[] { dstIdx });
					}

					break;
				}
				case Opcodes.JSR: {
					throw new UnsupportedOperationException("JSR is not supported");
				}
				// LdcInsnNode
				case Opcodes.LDC: {
					LdcInsnNode in = (LdcInsnNode) ain;
					Object val = in.cst;

					if (val instanceof Integer) {
						rec.push(common.INT, rec.getNextVarId(VarSource.Constant));
					} else if (val instanceof Float) {
						rec.push(common.FLOAT, rec.getNextVarId(VarSource.Constant));
					} else if (val instanceof Long) {
						rec.push(common.LONG, rec.getNextVarId(VarSource.Constant));
					} else if (val instanceof Double) {
						rec.push(common.DOUBLE, rec.getNextVarId(VarSource.Constant));
					} else if (val instanceof String) {
						rec.push(common.STRING, rec.getNextVarId(VarSource.Constant));
					} else if (val instanceof Type) {
						Type type = (Type) val;

						switch (type.getSort()) {
						case Type.OBJECT:
						case Type.ARRAY:
							rec.push(method.getEnv().getCreateClassInstance("Ljava/lang/Class;"), rec.getNextVarId(VarSource.Constant));
							break;
						case Type.METHOD:
							rec.push(method.getEnv().getCreateClassInstance("Ljava/lang/invoke/MethodType;"), rec.getNextVarId(VarSource.Constant));
							break;
						default:
							throw new UnsupportedOperationException("unsupported type sort: "+type.getSort());
						}
					} else {
						throw new UnsupportedOperationException("unknown ldc constant type: "+val.getClass());
					}

					break;
				}
				// IincInsnNode
				case Opcodes.IINC: {
					IincInsnNode in = (IincInsnNode) ain;
					rec.set(in.var, rec.get(in.var), rec.getId(in.var));
					break;
				}
				// TableSwitchInsnNode
				case Opcodes.TABLESWITCH: {
					TableSwitchInsnNode in = (TableSwitchInsnNode) ain;
					rec.pop();

					if (!exitPoints.containsKey(ain)) {
						Set<LabelNode> dsts = Util.newIdentityHashSet();
						dsts.addAll(in.labels);
						dsts.add(in.dflt);

						exitPoints.put(ain, dsts.stream().mapToInt(il::indexOf).toArray());
					}

					ExecState state = rec.getState();

					for (LabelNode label : in.labels) {
						QueueElement e = new QueueElement(il.indexOf(label), state);
						if (queued.add(e)) queue.add(e);
					}

					int dstIdx = il.indexOf(in.dflt);
					if (!rec.jump(dstIdx)) break insnLoop;
					idx = dstIdx;
					break;
				}
				// LookupSwitchInsnNode
				case Opcodes.LOOKUPSWITCH: {
					LookupSwitchInsnNode in = (LookupSwitchInsnNode) ain;
					rec.pop();

					if (!exitPoints.containsKey(ain)) {
						Set<LabelNode> dsts = Util.newIdentityHashSet();
						dsts.addAll(in.labels);
						dsts.add(in.dflt);

						exitPoints.put(ain, dsts.stream().mapToInt(il::indexOf).toArray());
					}

					ExecState state = rec.getState();

					for (LabelNode label : in.labels) {
						QueueElement e = new QueueElement(il.indexOf(label), state);
						if (queued.add(e)) queue.add(e);
					}

					int dstIdx = il.indexOf(in.dflt);
					if (!rec.jump(dstIdx)) break insnLoop;
					idx = dstIdx;
					break;
				}
				// MultiANewArrayInsnNode
				case Opcodes.MULTIANEWARRAY: {
					MultiANewArrayInsnNode in = (MultiANewArrayInsnNode) ain;
					ClassInstance cls = method.getEnv().getCreateClassInstance(in.desc);
					assert in.dims == cls.getArrayDimensions();

					for (int i = 0; i < in.dims; i++) {
						rec.pop();
					}

					rec.push(cls, rec.getNextVarId(VarSource.New));
					break;
				}
				default:
					throw new UnsupportedOperationException("unknown opcode: "+ain.getOpcode()+" (type "+ain.getType()+")");
				}

				if (!rec.next()) break;
			}
		}

		rec.dump(il, System.out);

		BitSet entryPoints = getEntryPoints(asmNode, exitPoints);
		applyTryCatchExits(asmNode, entryPoints, exitPoints);
		addDirectExits(il, entryPoints, exitPoints);
		purgeLocals(il, rec, entryPoints, exitPoints);

		rec.dump(il, System.out);

		createLocalVariables(il, rec, entryPoints, exitPoints, asmNode.localVariables);
	}

	private static void handleMethodInvocation(ClassEnv env, String owner, String name, String desc, boolean itf, boolean isStatic, StateRecorder rec) {
		MethodInstance target = env.getClsByName(owner).resolveMethod(name, desc, itf);

		for (int i = target.args.length - 1; i >= 0; i--) {
			if (target.args[i].getType().getSlotSize() == 1) {
				rec.pop();
			} else {
				rec.popDouble();
			}
		}

		if (!isStatic) rec.pop();

		if (target.getRetType() != rec.common.VOID) {
			rec.push(target.getRetType(), rec.getNextVarId(VarSource.MethodRet));
		}
	}

	private static boolean queueTryCatchBlocks(MethodInstance method, StateRecorder rec, Queue<QueueElement> queue, Set<QueueElement> queued) {
		if (method.getAsmNode().tryCatchBlocks.isEmpty()) return false;

		InsnList il = method.getAsmNode().instructions;
		Set<ExecState> states = new HashSet<>();
		boolean ret = false;

		for (TryCatchBlockNode n : method.getAsmNode().tryCatchBlocks) {
			ClassInstance type = n.type != null ? method.getEnv().getCreateClassInstance(ClassInstance.getId(n.type)) : method.getEnv().getCreateClassInstance("Ljava/lang/Throwable;");
			ClassInstance[] stack = new ClassInstance[] { type };
			int[] stackVarIds = new int[] { rec.getNextVarId(VarSource.ExtException) };

			for (int idx = il.indexOf(n.start), max = il.indexOf(n.end); idx < max; idx++) {
				ExecState state = rec.getState(idx);

				if (state != null) {
					states.add(new ExecState(Arrays.copyOf(state.locals, state.locals.length), Arrays.copyOf(state.localVarIds, state.locals.length), stack, stackVarIds));
				}
			}

			if (states.isEmpty()) continue;

			int dstIndex = il.indexOf(n.handler);

			for (ExecState state : states) {
				QueueElement e = new QueueElement(dstIndex, state);

				if (queued.add(e)) {
					queue.add(e);
					ret = true;
				}
			}

			states.clear();
		}

		return ret;
	}

	private static ClassInstance normalizeVarType(ClassInstance cls, CommonClasses common) {
		if (!cls.isPrimitive()) return cls;

		char id = cls.getId().charAt(0);

		if (id == 'Z' || id == 'C' || id == 'B' || id == 'S') {
			return common.INT;
		} else {
			return cls;
		}
	}

	private static class QueueElement {
		public QueueElement(int dstIndex, ExecState srcState) {
			this.dstIndex = dstIndex;
			this.srcState = srcState;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof QueueElement)) return false;

			QueueElement o = (QueueElement) obj;

			return dstIndex == o.dstIndex && srcState.equals(o.srcState);
		}

		@Override
		public int hashCode() {
			return dstIndex ^ srcState.hashCode();
		}

		final int dstIndex;
		final ExecState srcState;
	}

	private static BitSet getEntryPoints(MethodNode asmNode, Map<AbstractInsnNode, int[]> exitPoints) {
		InsnList il = asmNode.instructions;
		BitSet entryPoints = new BitSet(il.size());

		for (int[] eps : exitPoints.values()) {
			if (eps != null) {
				for (int ep : eps) entryPoints.set(ep);
			}
		}

		for (TryCatchBlockNode n : asmNode.tryCatchBlocks) {
			entryPoints.set(il.indexOf(n.handler));
		}

		return entryPoints;
	}

	private static void applyTryCatchExits(MethodNode asmNode, BitSet entryPoints, Map<AbstractInsnNode, int[]> exitPoints) {
		InsnList il = asmNode.instructions;

		for (TryCatchBlockNode n : asmNode.tryCatchBlocks) {
			boolean first = true;
			int max = Math.min(il.indexOf(n.end), il.size() - 1);
			entryPoints.set(max);

			for (int start = il.indexOf(n.start), idx = start; idx < max; idx++) {
				AbstractInsnNode ain = il.get(idx);
				boolean last = idx + 1 == max;
				if (ain.getOpcode() == -1 && !last) continue;

				int[] exits = exitPoints.get(ain);

				if (!first && exits == null && !last) { // limit sources to the first store, any with preceding stores, any existing exits or the last location since any others don't change the locals in the exc handler
					boolean found = false;

					for (int i = idx - 1; i > start; i--) {
						int op = il.get(i).getOpcode();

						if (op >= Opcodes.ISTORE && op <= Opcodes.ASTORE) {
							found = true;
						} else if (op != -1) { // allow opcode-less insns since they were ignored before
							break;
						}
					}

					if (!found) continue;
				}

				first = false;
				int dst = il.indexOf(n.handler);

				if (exits == null) {
					exitPoints.put(ain, new int[] { dst, idx + 1 });
				} else {
					boolean found = false;

					for (int exit : exits) {
						if (exit == dst) {
							found = true;
							break;
						}
					}

					if (!found) {
						exits = Arrays.copyOf(exits, exits.length + 1);
						exits[exits.length - 1] = dst;
						exitPoints.put(ain, exits);
					}
				}
			}
		}
	}

	private static void addDirectExits(InsnList il, BitSet entryPoints, Map<AbstractInsnNode, int[]> exitPoints) {
		int idx = 0; // ignore 0 since it has no preceding instruction

		while ((idx = entryPoints.nextSetBit(idx + 1)) != -1) {
			AbstractInsnNode prev = il.get(idx - 1);
			if (exitPoints.containsKey(prev)) continue;
			int type = prev.getType();

			if (prev.getOpcode() != Opcodes.ATHROW
					&& (prev.getOpcode() < Opcodes.IRETURN || prev.getOpcode() > Opcodes.RETURN)
					&& type != AbstractInsnNode.JUMP_INSN
					&& type != AbstractInsnNode.TABLESWITCH_INSN
					&& type != AbstractInsnNode.LOOKUPSWITCH_INSN) {
				exitPoints.put(prev, new int[] { idx });
			}
		}
	}

	private static void purgeLocals(InsnList il, StateRecorder rec, BitSet entryPoints, Map<AbstractInsnNode, int[]> exitPoints) {
		BitSet localsSupplied = new BitSet(rec.locals.length);
		BitSet localsUsed = new BitSet(rec.locals.length);
		boolean first = true;
		boolean changed = true;

		while (changed) {
			changed = false;

			for (Map.Entry<AbstractInsnNode, int[]> entry : exitPoints.entrySet()) {
				AbstractInsnNode exitNode = entry.getKey();
				if (exitNode == null) continue; // = method exit

				int exitNodeIdx = il.indexOf(exitNode);
				int[] dstIndices = entry.getValue();
				ExecState state = rec.getState(exitNodeIdx);

				// determine locals at the end of the current trace = all locals used by following traces and the last instruction

				markAvailableLocals(state, localsSupplied);

				int op = exitNode.getOpcode();

				if (exitNode.getType() == AbstractInsnNode.VAR_INSN) {
					int var = ((VarInsnNode) exitNode).var;

					if (op < Opcodes.ISTORE) { // *load
						localsUsed.set(var);
						if (op == Opcodes.LLOAD || op == Opcodes.DLOAD) localsUsed.set(var + 1);
					}
				}

				if (dstIndices != null) {
					for (int dstIdx : dstIndices) {
						markAvailableLocals(rec.getState(dstIdx), localsUsed);
					}
				}

				localsUsed.xor(localsSupplied);
				localsUsed.and(localsSupplied);

				if (!localsUsed.isEmpty()) { // not all used (xor -> 1 = not used)
					changed = true;
					int newLocalsSize = localsUsed.previousClearBit(state.locals.length - 1) + 1;
					ClassInstance[] newLocals = newLocalsSize == 0 ? ExecState.empty : Arrays.copyOf(state.locals, newLocalsSize);
					int[] newLocalVarIds = newLocalsSize == 0 ? ExecState.emptyIds : Arrays.copyOf(state.localVarIds, newLocalsSize);
					int idx = -1;

					while ((idx = localsUsed.nextSetBit(idx + 1)) != -1 && idx < newLocalsSize) {
						newLocals[idx] = null;
						newLocalVarIds[idx] = 0;
					}

					rec.setState(exitNodeIdx, new ExecState(newLocals, newLocalVarIds, state.stack, state.stackVarIds));
					localsSupplied.andNot(localsUsed);
				}

				// update trace body back to front, if potentially changed

				if (!localsUsed.isEmpty() || first) {
					for (int idx = exitNodeIdx; idx >= 0; idx--) {
						AbstractInsnNode ain = il.get(idx);
						state = rec.getState(idx);

						if (ain.getType() == AbstractInsnNode.VAR_INSN) {
							VarInsnNode in = (VarInsnNode) ain;
							op = in.getOpcode();

							if (op < Opcodes.ISTORE) { // load -> mark preceding local as required
								localsSupplied.set(in.var);
								if (op == Opcodes.LLOAD || op == Opcodes.DLOAD) localsSupplied.set(in.var + 1);
							} else { // store -> mark preceding local as unused
								localsSupplied.clear(in.var);
								if (op == Opcodes.LSTORE || op == Opcodes.DSTORE) localsSupplied.clear(in.var + 1);
							}
						} else if (ain.getType() == AbstractInsnNode.IINC_INSN) {
							localsSupplied.set(((IincInsnNode) ain).var); // ~load
						}

						boolean foundMismatch = false;

						for (int i = 0; i < state.locals.length; i++) {
							if ((state.locals[i] != null) != localsSupplied.get(i)) {
								if (state.locals[i] == null) {
									throw new IllegalStateException("missing local "+i);
								}

								foundMismatch = true;
								break;
							}
						}

						if (foundMismatch) {
							changed = true;

							int newLocalsSize = localsSupplied.previousSetBit(state.locals.length - 1) + 1;
							ClassInstance[] newLocals = newLocalsSize == 0 ? ExecState.empty : Arrays.copyOf(state.locals, newLocalsSize);
							int[] newLocalVarIds = newLocalsSize == 0 ? ExecState.emptyIds : Arrays.copyOf(state.localVarIds, newLocalsSize);

							for (int i = 0; i < newLocals.length - 1; i++) {
								if (!localsSupplied.get(i)) {
									newLocals[i] = null;
									newLocalVarIds[i] = 0;
								}
							}

							rec.setState(idx, new ExecState(newLocals, newLocalVarIds, state.stack, state.stackVarIds));
						}

						if (entryPoints.get(idx)) break;
					}
				}

				localsUsed.clear();
				localsSupplied.clear();
			}

			first = false;
		}
	}

	private static void markAvailableLocals(ExecState state, BitSet out) {
		ClassInstance[] locals = state.locals;

		for (int i = 0; i < locals.length; i++) {
			if (locals[i] != null) out.set(i);
		}
	}

	private static List<LocalVariableNode> createLocalVariables(InsnList il, StateRecorder rec, BitSet entryPoints, Map<AbstractInsnNode, int[]> exitPoints, List<LocalVariableNode> orig) {
		if (rec.locals.length == 0) return Collections.emptyList();

		int[] lvToVar = new int[rec.locals.length];
		int[] varToLv = new int[rec.locals.length];
		int[] startIndices = new int[rec.locals.length];
		int[] endIndices = new int[rec.locals.length];
		int varCount = 0;
		int firstNonContiguous = Integer.MAX_VALUE;
		int idx = 0;

		// determine variables for each linear control flow block
		while ((idx = entryPoints.nextSetBit(idx)) != -1) {
			Arrays.fill(lvToVar, -1);

			if (varCount != 0 && firstNonContiguous == Integer.MAX_VALUE) { // not the first iteration and we branched, vars may need merging
				firstNonContiguous = varCount;
			}

			// determine variables for the current linear control flow block
			boolean cont;

			do {
				ExecState state = rec.getState(idx);

				for (int lvi = 0; lvi < state.locals.length; lvi++) {
					ClassInstance vt = state.locals[lvi];

					if (vt == null || vt == rec.common.TOP) {
						lvToVar[lvi] = -1; // var left scope
						continue;
					}

					int vi = lvToVar[lvi];

					if (vi == -1 || rec.getState(startIndices[vi]).locals[lvi] != vt) { // no existing variable or incompatible type -> create new variable
						if (varCount >= startIndices.length) {
							varToLv = Arrays.copyOf(varToLv, varToLv.length * 2);
							startIndices = Arrays.copyOf(startIndices, startIndices.length * 2);
							endIndices = Arrays.copyOf(endIndices, endIndices.length * 2);
						}

						vi = varCount++;
						lvToVar[lvi] = vi;
						varToLv[vi] = lvi;
						startIndices[vi] = idx;
					}

					endIndices[vi] = idx;
				}

				AbstractInsnNode ain = il.get(idx);
				idx++;

				if (!exitPoints.containsKey(ain)) { // linear control flow
					cont = true;
				} else { // check for linear-like control flow (can continue at the next instruction, same locals at all branch targets)
					cont = false;
					int[] exits = exitPoints.get(ain);

					if (exits != null && exits.length > 0) {
						if (exits.length == 1) {
							if (exits[0] == idx) cont = true;
						} else {
							ClassInstance[] locals = rec.getState(exits[0]).locals;

							for (int i = 1; i < exits.length; i++) {
								if (!Arrays.equals(locals, rec.getState(exits[i]).locals)) {
									cont = false;
									break;
								} else if (exits[i] == idx) {
									cont = true;
								}
							}
						}
					}
				}
			} while (cont);
		}

		lvToVar = null;

		System.out.println("Local vars raw:");

		for (int i = 0; i < varCount; i++) {
			ExecState state = rec.getState(startIndices[i]);

			System.out.printf("  %d: LV %d @ %d - %d: %s\t\t(%s)%n",
					i, varToLv[i], startIndices[i], endIndices[i], state.locals[varToLv[i]].toString(), rec.varSources[state.localVarIds[varToLv[i]] - 1].name());
		}

		// merge variables if they are adjacent and reachable without interruption, TODO: this currently only merges blocks that are reachable by the preceding block, the other way is also possible
		if (varCount > firstNonContiguous) {
			BitSet exits = new BitSet(il.size()); // for finding the next exit quickly

			for (AbstractInsnNode exitPoint : exitPoints.keySet()) {
				if (exitPoint != null) exits.set(il.indexOf(exitPoint));
			}

			BitSet queue = new BitSet(il.size());
			BitSet processed = new BitSet(il.size());

			for (int vi = firstNonContiguous; vi < varCount; vi++) {
				final int lvi = varToLv[vi];
				final int dstStart = startIndices[vi];
				final int dstEnd = endIndices[vi];
				ClassInstance type = rec.getState(startIndices[vi]).locals[lvi];
				int vi2 = -1;

				// find adjacent compatible variable that isn't within the same linear control flow block
				for (int i = varCount - 1; i >= 0; i--) {
					if (varToLv[i] == lvi) {
						int start = startIndices[i];
						int end = endIndices[i];

						if (i < vi && vi2 == -1) {
							ClassInstance[] locals;
							int exit = exits.nextSetBit(start);
							assert exit != -1;

							if (end >= exit
									&& rec.getState(start).locals[lvi] == type
									&& (locals = rec.getState(exit).locals).length > lvi && locals[lvi] == type
									&& exit < exits.nextSetBit(dstStart)) {
								vi2 = i;
							} else {
								break;
							}
						}

						if (i != vi) processed.set(start, end + 1);
					}
				}

				if (vi2 == -1) { // nothing found
					processed.clear();
					continue;
				}

				int srcStart = startIndices[vi2] - 1;
				int srcEnd = endIndices[vi2];

				// queue all locations potentially jumping to vi
				while ((srcStart = exits.nextSetBit(srcStart + 1)) != -1 && srcStart <= srcEnd) {
					queue.set(srcStart);
				}

				while ((srcStart = queue.nextSetBit(0)) != -1) {
					srcEnd = exits.nextSetBit(srcStart);
					assert srcEnd != -1;

					if (srcStart <= dstEnd && srcEnd >= dstStart) { // match
						assert startIndices[vi] > startIndices[vi2];
						assert endIndices[vi] > endIndices[vi2];

						// merge vi -> vi2
						endIndices[vi2] = endIndices[vi];
						// remove vi
						System.arraycopy(varToLv, vi + 1, varToLv, vi, varCount - vi - 1);
						System.arraycopy(startIndices, vi + 1, startIndices, vi, varCount - vi - 1);
						System.arraycopy(endIndices, vi + 1, endIndices, vi, varCount - vi - 1);
						varCount--;
						vi--;
						break;
					} else { // no match, queue all suitable jump targets
						int[] dsts = exitPoints.get(il.get(srcEnd));

						if (dsts != null) {
							for (int dst : dsts) {
								int nextSet = processed.nextSetBit(dst);
								int nextExit = exits.nextSetBit(dst);
								assert nextExit != -1;

								if (nextSet == -1 || nextSet > nextExit) {
									ClassInstance[] locals = rec.getState(dst).locals;

									if (locals.length > lvi && locals[lvi] == type) {
										processed.set(dst, nextExit + 1);
										queue.set(dst);
									}
								}
							}
						}
					}

					queue.clear(srcStart);
				}

				queue.clear();
				processed.clear();
			}
		}

		System.out.println("Local vars:");

		for (int i = 0; i < varCount; i++) {
			ExecState state = rec.getState(startIndices[i]);

			System.out.printf("  %d: LV %d @ %d - %d: %s\t\t(%s)%n",
					i, varToLv[i], startIndices[i], endIndices[i], state.locals[varToLv[i]].toString(), rec.varSources[state.localVarIds[varToLv[i]] - 1].name());
		}

		if (orig != null) {
			boolean mismatch = orig.size() != varCount;

			if (!mismatch) {
				for (int i = 0; i < varCount; i++) {
					LocalVariableNode lvn = orig.get(i);

					if (lvn.index != varToLv[i]
							|| !lvn.desc.equals(rec.getState(startIndices[i]).locals[varToLv[i]].getId())
							|| (il.indexOf(lvn.start) > startIndices[i])
							|| il.indexOf(lvn.end) <= endIndices[i]) {
						mismatch = true;
						break;
					}
				}
			}

			if (!mismatch) {
				System.out.println("Existing vars matched!");
			} else {
				System.out.println("Existing vars mismatch:");

				for (int i = 0; i < orig.size(); i++) {
					LocalVariableNode lvn = orig.get(i);

					System.out.printf("  %d: LV %d @ %d - %d: %s%n", i, lvn.index, il.indexOf(lvn.start), il.indexOf(lvn.end) - 1, lvn.desc);
				}
			}
		}

		return null;
	}

	public static class CommonClasses {
		CommonClasses(ClassEnv env) {
			this.INT = env.getCreateClassInstance("I");
			this.LONG = env.getCreateClassInstance("J");
			this.BOOLEAN = env.getCreateClassInstance("Z");
			this.BYTE = env.getCreateClassInstance("B");
			this.CHAR = env.getCreateClassInstance("C");
			this.SHORT = env.getCreateClassInstance("S");
			this.FLOAT = env.getCreateClassInstance("F");
			this.DOUBLE = env.getCreateClassInstance("D");
			this.NULL = new ClassInstance("Lmatcher/special/null;", env);
			this.TOP = this.VOID = env.getCreateClassInstance("V");
			this.STRING = env.getCreateClassInstance("Ljava/lang/String;");
		}

		final ClassInstance INT;
		final ClassInstance LONG;
		final ClassInstance BOOLEAN;
		final ClassInstance BYTE;
		final ClassInstance CHAR;
		final ClassInstance SHORT;
		final ClassInstance FLOAT;
		final ClassInstance DOUBLE;
		final ClassInstance NULL;
		final ClassInstance VOID;
		final ClassInstance TOP;
		final ClassInstance STRING;
	}

	private static class StateRecorder {
		StateRecorder(MethodInstance method, CommonClasses common) {
			MethodNode asmNode = method.getAsmNode();

			locals = new ClassInstance[asmNode.maxLocals];
			localVarIds = new int[locals.length];
			stack = new ClassInstance[asmNode.maxStack];
			stackVarIds = new int[stack.length];

			if (!method.isStatic()) {
				localVarIds[localsSize] = getNextVarId(VarSource.Arg);
				locals[localsSize++] = method.getCls();
			}

			for (MethodVarInstance arg : method.getArgs()) {
				localVarIds[localsSize] = getNextVarId(VarSource.Arg);
				locals[localsSize++] = arg.getType();
				if (arg.getType().getSlotSize() == 2) locals[localsSize++] = common.TOP;
			}

			this.states = new ExecState[asmNode.instructions.size()];
			this.common = common;

			updateState();
		}

		void push(Variable var) {
			push(var.type, var.id);
		}

		void push(ClassInstance cls, int varId) {
			stackVarIds[stackSize] = varId;
			stack[stackSize++] = cls;

			if (cls.getSlotSize() == 2) {
				stackVarIds[stackSize] = 0;
				stack[stackSize++] = common.TOP;
			}
		}

		Variable pop() {
			if (stackSize < 1) {
				throw new IllegalStateException("pop from empty stack");
			}

			ClassInstance ret = stack[--stackSize];
			stack[stackSize] = null;
			int id = stackVarIds[stackSize];
			stackVarIds[stackSize] = 0;

			if (ret != null && ret.getSlotSize() == 2) throw new IllegalStateException("pop for double element");

			return new Variable(ret, id);
		}

		void pop2() {
			if (stackSize < 2) {
				throw new IllegalStateException("pop2 from empty/single slot stack");
			}

			stack[--stackSize] = null;
			stackVarIds[stackSize] = 0;

			stack[--stackSize] = null;
			stackVarIds[stackSize] = 0;
		}

		Variable popDouble() {
			if (stackSize < 2) {
				throw new IllegalStateException("pop2 from empty/single slot stack");
			}

			stack[--stackSize] = null;
			stackVarIds[stackSize] = 0;

			ClassInstance ret = stack[--stackSize];
			stack[stackSize] = null;
			int id = stackVarIds[stackSize];
			stackVarIds[stackSize] = 0;

			if (ret == null || ret.getSlotSize() != 2) {
				throw new IllegalStateException("pop2Double for single element");
			}

			return new Variable(ret, id);
		}

		Variable peek() {
			if (stackSize < 1) {
				throw new IllegalStateException("peek at empty stack");
			}

			return new Variable(stack[stackSize - 1], stackVarIds[stackSize - 1]);
		}

		Variable peekDouble() {
			if (stackSize < 2) {
				throw new IllegalStateException("peekDouble at empty/single slot stack");
			}

			ClassInstance ret = stack[stackSize - 2];

			if (ret == null || ret.getSlotSize() != 2) {
				throw new IllegalStateException("peekDouble for single element");
			}

			return new Variable(ret, stackVarIds[stackSize - 2]);
		}

		void clearStack() {
			while (stackSize > 0) {
				stack[--stackSize] = null;
				stackVarIds[stackSize] = 0;
			}
		}

		boolean isTopDoubleSlot() {
			if (stackSize < 2) return false;

			ClassInstance cls = stack[stackSize - 2];

			return cls != null && cls.getSlotSize() == 2;
		}

		ClassInstance get(int lvtIdx) {
			ClassInstance ret = locals[lvtIdx];
			if (ret == null) throw new IllegalStateException("unassigned local var requested");

			return ret;
		}

		int getId(int lvtIdx) {
			return localVarIds[lvtIdx];
		}

		void set(int lvtIdx, Variable var) {
			set(lvtIdx, var.type, var.id);
		}

		void set(int lvtIdx, ClassInstance value, int varId) {
			locals[lvtIdx] = value;
			localVarIds[lvtIdx] = varId;
			if (lvtIdx >= localsSize) localsSize = lvtIdx + 1;

			if (value != null && value.getSlotSize() == 2) {
				locals[lvtIdx + 1] = common.TOP;
				localVarIds[lvtIdx + 1] = 0;
				if (lvtIdx + 1 >= localsSize) localsSize = lvtIdx + 2;
			}
		}

		boolean next() {
			idx++;

			return updateState();
		}

		boolean jump(int dstIdx) {
			idx = dstIdx;

			return updateState();
		}

		boolean jump(int dstIdx, ExecState srcState) {
			System.arraycopy(srcState.locals, 0, locals, 0, srcState.locals.length);
			System.arraycopy(srcState.localVarIds, 0, localVarIds, 0, srcState.locals.length);
			System.arraycopy(srcState.stack, 0, stack, 0, srcState.stack.length);
			System.arraycopy(srcState.stackVarIds, 0, stackVarIds, 0, srcState.stack.length);
			localsSize = srcState.locals.length;
			stackSize = srcState.stack.length;
			this.idx = dstIdx;

			return updateState();
		}

		private boolean updateState() {
			ExecState oldState = states[idx];

			if (oldState == null
					|| oldState.stack.length != stackSize
					|| oldState.locals.length > localsSize
					|| !compareVars(oldState.locals, locals, Math.min(oldState.locals.length, localsSize))
					|| !compareVars(oldState.stack, stack, stackSize)) {
				if (oldState != null) {
					ExecState newState = mergeStates(states[idx]);

					if (newState.equals(oldState)) {
						assert false;
						return false;
					}

					states[idx] = newState;
				} else {
					states[idx] = new ExecState(locals, localVarIds, localsSize, stack, stackVarIds, stackSize);
				}

				return true;
			} else {
				return false;
			}
		}

		private boolean compareVars(ClassInstance[] typesA, ClassInstance[] typesB, int size) {
			for (int i = 0; i < size; i++) {
				ClassInstance a = typesA[i];
				ClassInstance b = typesB[i];
				ClassInstance commonCls = getCommonSuperClass(a, b);

				if (commonCls != a) return false;
			}

			return true;
		}

		public ExecState getState() {
			return new ExecState(locals, localVarIds, localsSize, stack, stackVarIds, stackSize);
		}

		public ExecState getState(int idx) {
			return states[idx];
		}

		public void setState(int idx, ExecState state) {
			states[idx] = state;
		}

		private ExecState mergeStates(ExecState oldState) {
			int lastUsed = -1;
			ClassInstance[] newLocals = null;
			int[] newLocalVarIds = null;

			for (int i = 0, max = Math.min(oldState.locals.length, localsSize); i < max; i++) {
				ClassInstance a = oldState.locals[i];
				ClassInstance b = locals[i];
				ClassInstance commonCls = getCommonSuperClass(a, b);

				if (commonCls != a) {
					if (newLocals == null) newLocals = Arrays.copyOf(oldState.locals, max);

					newLocals[i] = commonCls;
				}

				if (commonCls != null) {
					lastUsed = i;
				} else if (oldState.localVarIds[i] != 0) {
					if (newLocalVarIds == null) newLocalVarIds = Arrays.copyOf(oldState.localVarIds, max);

					newLocalVarIds[i] = 0;
				}

				if (localVarIds[i] != oldState.localVarIds[i]) {
					recordVarIdMap(localVarIds[i], oldState.localVarIds[i]);
				}
			}

			if (newLocals == null) newLocals = oldState.locals;

			if (lastUsed + 1 != newLocals.length) {
				newLocals = Arrays.copyOf(newLocals, lastUsed + 1);
				newLocalVarIds = Arrays.copyOf(newLocalVarIds == null ? oldState.localVarIds : newLocalVarIds, lastUsed + 1);
			} else if (newLocalVarIds == null) {
				newLocalVarIds = oldState.localVarIds;
			}

			if (stackSize != oldState.stack.length) {
				throw new IllegalStateException("mismatched stack sizes");
			}

			ClassInstance[] newStack = null;

			for (int i = 0; i < stackSize; i++) {
				ClassInstance a = oldState.stack[i];
				ClassInstance b = stack[i];
				ClassInstance commonCls = getCommonSuperClass(a, b);

				if (commonCls == null) {
					throw new IllegalStateException("incompatible stack types: "+a+" "+b);
				}

				if (commonCls != a) {
					if (newStack == null) newStack = Arrays.copyOf(oldState.stack, stackSize);

					newStack[i] = commonCls;
				}

				if (stackVarIds[i] != oldState.stackVarIds[i]) {
					recordVarIdMap(stackVarIds[i], oldState.stackVarIds[i]);
				}
			}

			if (newStack == null) newStack = oldState.stack;

			return new ExecState(newLocals, newLocalVarIds, newStack, oldState.stackVarIds);
		}

		private ClassInstance getCommonSuperClass(ClassInstance a, ClassInstance b) {
			if (a == b) {
				return a;
			} else if (a == null || b == null) {
				return null;
			} else if (b == common.NULL && !a.isPrimitive()) {
				return a;
			} else if (a == common.NULL && !b.isPrimitive()) {
				return b;
			} else if (a.isPrimitive() && b.isPrimitive()) {
				char idA = a.id.charAt(0);
				char idB = b.id.charAt(0);

				if ((idA == 'I' || idA == 'Z' || idA == 'C' || idA == 'B' || idA == 'S')
						&& (idB == 'I' || idB == 'Z' || idB == 'C' || idB == 'B' || idB == 'S')) {
					return common.INT;
				} else {
					return null;
				}
			} else {
				return a.getCommonSuperClass(b);
			}
		}

		private void recordVarIdMap(int srcId, int dstId) {
			assert srcId != dstId;

			if (srcId < dstId) {
				int tmp = srcId;
				srcId = dstId;
				dstId = tmp;
			}

			if (srcId >= varIdMap.length) varIdMap = Arrays.copyOf(varIdMap, Math.max(varIdMap.length * 2, srcId + 1));

			for (;;) {
				int prev = varIdMap[srcId];

				if (prev == dstId) {
					break;
				} else if (prev == 0) {
					varIdMap[srcId] = dstId;
					break;
				} else if (prev < dstId) {
					varIdMap[srcId] = dstId;
					srcId = dstId;
					dstId = prev;
				} else {
					srcId = prev;
				}
			}
		}

		private boolean compareVarIds(int idA, int idB) {
			if (idA == idB) return true;

			if (idA < idB) {
				int tmp = idA;
				idA = idB;
				idB = tmp;
			}

			if (idA >= varIdMap.length) return false;

			int mapped;

			while ((mapped = varIdMap[idA]) >= idB) {
				if (mapped == idB) return true;

				idA = mapped;
			}

			return false;
		}

		private int getMappedVarId(int id) {
			if (id >= varIdMap.length) return id;

			int newId;

			while ((newId = varIdMap[id]) != 0) {
				id = newId;
			}

			return id;
		}

		public int getNextVarId(VarSource source) {
			if (source == null) throw new NullPointerException("null source");

			if (nextVarId == varSources.length) varSources = Arrays.copyOf(varSources, varSources.length * 2);
			varSources[nextVarId] = source;

			return ++nextVarId;
		}

		public void dump(InsnList il, PrintStream ps) {
			for (int i = 0; i < states.length; i++) {
				ExecState state = states[i];

				ps.print(i);
				ps.print(": ");

				if (state != null) {
					dumpVars(state.locals, state.localVarIds, ps);
					ps.print(" | ");
					dumpVars(state.stack, state.stackVarIds, ps);
				} else {
					ps.print("<no state>");
				}

				ps.print(" ");

				AbstractInsnNode ain = il.get(i);
				int op = ain.getOpcode();

				if (op != -1) {
					ps.print(Printer.OPCODES[ain.getOpcode()]);
				}

				switch (ain.getType()) {
				case AbstractInsnNode.INSN:
					break;
				case AbstractInsnNode.INT_INSN:
					ps.print(' ');

					if (op == Opcodes.BIPUSH || op == Opcodes.SIPUSH) {
						ps.print(((IntInsnNode) ain).operand);
					} else {
						ps.print(Printer.TYPES[((IntInsnNode) ain).operand]);
					}

					break;
				case AbstractInsnNode.VAR_INSN:
					ps.print(' ');
					ps.print(((VarInsnNode) ain).var);
					break;
				case AbstractInsnNode.TYPE_INSN:
					ps.print(' ');
					ps.print(((TypeInsnNode) ain).desc);
					break;
				case AbstractInsnNode.FIELD_INSN: {
					FieldInsnNode in = (FieldInsnNode) ain;
					ps.print(' ');
					ps.print(in.owner);
					ps.print('/');
					ps.print(in.name);
					ps.print(' ');
					ps.print(in.desc);
					break;
				}
				case AbstractInsnNode.METHOD_INSN: {
					MethodInsnNode in = (MethodInsnNode) ain;
					ps.print(' ');
					ps.print(in.owner);
					ps.print('/');
					ps.print(in.name);
					ps.print(in.desc);
					ps.print(" itf=");
					ps.print(in.itf);
					break;
				}
				case AbstractInsnNode.INVOKE_DYNAMIC_INSN: {
					InvokeDynamicInsnNode in = (InvokeDynamicInsnNode) ain;
					// TODO: implement
					break;
				}
				case AbstractInsnNode.JUMP_INSN:
					ps.print(' ');
					ps.print(il.indexOf(((JumpInsnNode) ain).label));
					break;
				case AbstractInsnNode.LDC_INSN:
					ps.print(' ');
					ps.print(((LdcInsnNode) ain).cst);
					break;
				case AbstractInsnNode.IINC_INSN:
					ps.print(' ');
					ps.print(((IincInsnNode) ain).var);
					ps.print(' ');
					ps.print(((IincInsnNode) ain).incr);
					break;
				case AbstractInsnNode.TABLESWITCH_INSN: {
					TableSwitchInsnNode in = (TableSwitchInsnNode) ain;
					ps.print(" min=");
					ps.print(in.min);
					ps.print(" max=");
					ps.print(in.max);
					ps.print(" def=");
					ps.print(il.indexOf(in.dflt));

					for (int j = 0; j < in.labels.size(); j++) {
						ps.print(' ');
						ps.print(il.indexOf(in.labels.get(j)));
					}

					break;
				}
				case AbstractInsnNode.LOOKUPSWITCH_INSN: {
					LookupSwitchInsnNode in = (LookupSwitchInsnNode) ain;
					ps.print(" def=");
					ps.print(il.indexOf(in.dflt));

					for (int j = 0; j < in.keys.size(); j++) {
						ps.print(' ');
						ps.print(in.keys.get(j));
						ps.print('=');
						ps.print(il.indexOf(in.labels.get(j)));
					}

					break;
				}
				case AbstractInsnNode.MULTIANEWARRAY_INSN: {
					MultiANewArrayInsnNode in = (MultiANewArrayInsnNode) ain;
					ps.print(' ');
					ps.print(in.desc);
					ps.print(" dims=");
					ps.print(in.dims);
					break;
				}
				case AbstractInsnNode.LABEL:
					ps.print("LABEL ");
					ps.print(i);
					break;
				case AbstractInsnNode.FRAME:
					ps.print("FRAME");
					break;
				case AbstractInsnNode.LINE:
					ps.print("LINE ");
					ps.print(((LineNumberNode) ain).line);
					break;
				default:
					throw new UnsupportedOperationException("unknown insn: "+ain);
				}

				ps.println();
			}
		}

		private void dumpVars(ClassInstance[] types, int[] ids, PrintStream ps) {
			ps.print('[');

			for (int i = 0; i < types.length; i++) {
				if (i != 0) ps.print(", ");

				ClassInstance type = types[i];
				int id = ids[i];

				if (id == 0) {
					if (type == common.TOP) {
						ps.print("TOP");
					} else {
						assert type == null;
						ps.print("X");
					}
				} else {
					assert type != null;
					assert type != common.TOP;

					ps.print(getMappedVarId(id));
					ps.print(':');

					if (type != common.NULL) {
						ps.print(type.toString());
					} else {
						ps.print("null");
					}
				}
			}

			ps.print(']');
		}

		final ExecState[] states; // state at the start of every instruction index
		final ClassInstance[] locals;
		final int[] localVarIds;
		int localsSize;
		final ClassInstance[] stack;
		final int[] stackVarIds;
		int stackSize;
		int idx;
		final CommonClasses common;
		private int nextVarId;
		VarSource[] varSources = new VarSource[10];
		int[] varIdMap = new int[10];
	}

	private static class Variable {
		public Variable(ClassInstance type, int id) {
			this.type = type;
			this.id = id;
		}

		final ClassInstance type;
		final int id;
	}

	private enum VarSource {
		Constant, Arg, Merge, ExtException, IntException, ArrayElement, Cast, Computed, New, Field, MethodRet;
	}

	private static class ExecState {
		public ExecState(ClassInstance[] locals, int[] localVarIds, int localsSize, ClassInstance[] stack, int[] stackVarIds, int stackSize) {
			this((localsSize != 0 ? Arrays.copyOf(locals, localsSize) : empty),
					(localsSize != 0 ? Arrays.copyOf(localVarIds, localsSize) : emptyIds),
					(stackSize != 0 ? Arrays.copyOf(stack, stackSize) : empty),
					(stackSize != 0 ? Arrays.copyOf(stackVarIds, stackSize) : emptyIds));
		}

		public ExecState(ClassInstance[] locals, int[] localVarIds, ClassInstance[] stack, int[] stackVarIds) {
			if (locals == null) throw new NullPointerException("null locals");
			if (localVarIds == null) throw new NullPointerException("null local var ids");
			if (stack == null) throw new NullPointerException("null stack");
			if (stackVarIds == null) throw new NullPointerException("null stack var ids");

			this.locals = locals;
			this.localVarIds = localVarIds;
			this.stack = stack;
			this.stackVarIds = stackVarIds;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof ExecState)) return false;

			ExecState o = (ExecState) obj;

			return Arrays.equals(locals, o.locals)
					&& Arrays.equals(stack, o.stack);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(locals) ^ Arrays.hashCode(stack);
		}

		private static final ClassInstance[] empty = new ClassInstance[0];
		private static final int[] emptyIds = new int[0];

		final ClassInstance[] locals;
		final int[] localVarIds;
		final ClassInstance[] stack;
		final int[] stackVarIds;
	}

	static void checkInitializer(FieldInstance field, ClassFeatureExtractor context) {
		if (field.getType().isPrimitive()) return;

		MethodInstance method = field.writeRefs.iterator().next();
		MethodNode asmNode = method.getAsmNode();
		InsnList il = asmNode.instructions;
		AbstractInsnNode fieldWrite = null;

		//dump(method.asmNode);
		//System.out.println("\n------------------------\n");

		for (Iterator<AbstractInsnNode> it = il.iterator(); it.hasNext(); ) {
			AbstractInsnNode aInsn = it.next();

			if (aInsn.getOpcode() == Opcodes.PUTFIELD || aInsn.getOpcode() == Opcodes.PUTSTATIC) {
				FieldInsnNode in = (FieldInsnNode) aInsn;
				ClassInstance cls;

				if (in.name.equals(field.origName)
						&& in.desc.equals(field.getDesc())
						&& (in.owner.equals(field.cls.getName()) || (cls = context.getLocalClsByName(in.owner)) != null && cls.resolveField(in.name, in.desc) == field)) {
					fieldWrite = in;
					break;
				}
			}
		}

		if (fieldWrite == null) {
			dump(asmNode);
			throw new IllegalStateException("can't find field write insn for "+field+" in "+method);
		}

		Interpreter<SourceValue> interpreter = new SourceInterpreter();
		Analyzer<SourceValue> analyzer = new Analyzer<>(interpreter);
		Frame<SourceValue>[] frames;

		try {
			frames = analyzer.analyze(method.cls.getName(), asmNode);
			if (frames.length != asmNode.instructions.size()) throw new RuntimeException("invalid frame count");
		} catch (AnalyzerException e) {
			throw new RuntimeException(e);
		}

		BitSet tracedPositions = new BitSet(il.size());
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

		//Textifier textifier = new Textifier();
		//MethodVisitor visitor = new TraceMethodVisitor(textifier);
		List<AbstractInsnNode> initIl = new ArrayList<AbstractInsnNode>(tracedPositions.cardinality());
		int pos = 0;

		while ((pos = tracedPositions.nextSetBit(pos)) != -1) {
			in = il.get(pos);
			initIl.add(in);

			/*System.out.print(pos+": ");

			il.get(pos).accept(visitor);
			System.out.print(textifier.getText().get(0));
			textifier.getText().clear();*/

			pos++;
		}

		field.initializer = initIl;

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
		//System.out.println();
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
