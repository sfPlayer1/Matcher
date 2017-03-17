package matcher;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;

public class AsmClassRemapper extends ClassRemapper {
	public AsmClassRemapper(ClassVisitor cv, AsmRemapper remapper) {
		super(cv, remapper);
	}

	@Override
	protected MethodVisitor createMethodRemapper(MethodVisitor mv) {
		return new AsmMethodRemapper(mv, remapper);
	}

	private static class AsmMethodRemapper extends MethodRemapper {
		public AsmMethodRemapper(MethodVisitor mv, Remapper remapper) {
			super(mv, remapper);
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
			String owner = Type.getType(desc).getReturnType().getInternalName();
			AsmRemapper remapper = (AsmRemapper) this.remapper;

			if (bsm.getTag() == Opcodes.H_INVOKESTATIC
					&& bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
					&& bsm.getName().equals("metafactory")
					&& bsm.getDesc().equals(lambdaMetaFactoryDesc)
					&& !bsm.isInterface()) {
				// bsmArgs are the "static arguments in the call site specifier" as defined in https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-6.html#jvms-6.5.invokedynamic
				// The first 3 args for LambdaMetaFactory.metafactory are being supplied by INVOKEDYNAMIC, so bsmArgs[0] is the 4rd arg, MethodType samMethodType.
				// https://docs.oracle.com/javase/8/docs/api/java/lang/invoke/LambdaMetafactory.html#metafactory-java.lang.invoke.MethodHandles.Lookup-java.lang.String-java.lang.invoke.MethodType-java.lang.invoke.MethodType-java.lang.invoke.MethodHandle-java.lang.invoke.MethodType-
				// the ASM const representation for MethodType is the Type class.

				Type samMethodType = (Type) bsmArgs[0];

				name = remapper.mapLambdaInvokeDynamicMethodName(owner, name, samMethodType.getDescriptor());
			} else {
				name = remapper.mapArbitraryInvokeDynamicMethodName(owner, name);
			}

			super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
		}
	}

	private static final String lambdaMetaFactoryDesc = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
}
