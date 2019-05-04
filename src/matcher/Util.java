package matcher;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodInsnNode;

public class Util {
	public static <T> Set<T> newIdentityHashSet() {
		return Collections.newSetFromMap(new IdentityHashMap<>());//new IdentityHashSet<>();
	}

	public static <T> Set<T> newIdentityHashSet(Collection<? extends T> c) {
		Set<T> ret = Collections.newSetFromMap(new IdentityHashMap<>(c.size()));
		ret.addAll(c);

		return ret;//new IdentityHashSet<>(c);
	}

	public static <T> Set<T> copySet(Set<T> set) {
		if (set instanceof HashSet) {
			return new HashSet<>(set);
		} else {
			return newIdentityHashSet(set);
		}
	}

	public static FileSystem iterateJar(Path archive, boolean autoClose, Consumer<Path> handler) {
		FileSystem fs = null;

		try {
			fs = FileSystems.newFileSystem(new URI("jar:"+archive.toUri().toString()), Collections.emptyMap());

			Files.walkFileTree(fs.getPath("/"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.toString().endsWith(".class")) {
						handler.accept(file);
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			closeSilently(fs);
			throw new UncheckedIOException(e);
		} catch (URISyntaxException e) {
			closeSilently(fs);
			throw new RuntimeException(e);
		} catch (Throwable t) {
			closeSilently(fs);
			throw t;
		}

		if (autoClose) closeSilently(fs);

		return fs;
	}

	public static void closeSilently(Closeable c) {
		if (c == null) return;

		try {
			c.close();
		} catch (IOException e) { }
	}

	public static boolean isCallToInterface(MethodInsnNode insn) {
		assert insn.itf || insn.getOpcode() != Opcodes.INVOKEINTERFACE;

		return insn.itf;
		/*return insn.getOpcode() == Opcodes.INVOKEINTERFACE
				|| (insn.getOpcode() == Opcodes.INVOKESPECIAL || insn.getOpcode() == Opcodes.INVOKESTATIC) && insn.itf;*/
	}

	public static boolean isCallToInterface(Handle handle) {
		assert handle.isInterface() || handle.getTag() != Opcodes.H_INVOKEINTERFACE;

		return handle.isInterface();

		/*return handle.getTag() == Opcodes.H_INVOKEINTERFACE
				|| (handle.getTag() == Opcodes.H_INVOKESPECIAL || handle.getTag() == Opcodes.H_NEWINVOKESPECIAL || handle.getTag() == Opcodes.H_INVOKESTATIC) && handle.isInterface();*/
	}

	public static String formatAccessFlags(int access, AFElementType type) {
		int assoc = type.assoc;
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < accessFlags.length; i++) {
			if ((accessAssoc[i] & assoc) == 0) continue;
			if ((access & accessFlags[i]) == 0) continue;

			if (sb.length() != 0) sb.append(' ');
			sb.append(accessNames[i]);

			access &= ~accessFlags[i];
		}

		if (access != 0) {
			if (sb.length() != 0) sb.append(' ');
			sb.append("0x");
			sb.append(Integer.toHexString(access));
		}

		return sb.toString();
	}

	public enum AFElementType {
		Class(1), Method(2), Field(4), Parameter(8), InnerClass(16);

		private AFElementType(int assoc) {
			this.assoc = assoc;
		}

		final int assoc;
	}

	private static final int[] accessFlags = new int[] { Opcodes.ACC_PUBLIC, Opcodes.ACC_PRIVATE, Opcodes.ACC_PROTECTED, Opcodes.ACC_STATIC,
			Opcodes.ACC_FINAL, Opcodes.ACC_SUPER, Opcodes.ACC_SYNCHRONIZED, Opcodes.ACC_VOLATILE, Opcodes.ACC_BRIDGE, Opcodes.ACC_VARARGS,
			Opcodes.ACC_TRANSIENT, Opcodes.ACC_NATIVE, Opcodes.ACC_INTERFACE, Opcodes.ACC_ABSTRACT, Opcodes.ACC_STRICT, Opcodes.ACC_SYNTHETIC,
			Opcodes.ACC_ANNOTATION, Opcodes.ACC_ENUM, Opcodes.ACC_MANDATED };
	private static final String[] accessNames = new String[] { "public", "private", "protected", "static",
			"final", "super", "synchronized", "volatile", "bridge", "varargs",
			"transient", "native", "interface", "abstract", "strict", "synthetic",
			"annotation", "enum", "mandated" };
	private static final byte[] accessAssoc = new byte[] { 7, 7, 7, 6,
			15, 1, 2, 4, 2, 2,
			4, 2, 1, 3, 2, 15,
			1, 21, 8 };

	public static Handle getTargetHandle(Handle bsm, Object[] bsmArgs) {
		if (isJavaLambdaMetafactory(bsm)) {
			return (Handle) bsmArgs[1];
		} else {
			System.out.printf("unknown invokedynamic bsm: %s/%s%s (tag=%d iif=%b)%n", bsm.getOwner(), bsm.getName(), bsm.getDesc(), bsm.getTag(), bsm.isInterface());

			return null;
		}
	}

	public static boolean isJavaLambdaMetafactory(Handle bsm) {
		return bsm.getTag() == Opcodes.H_INVOKESTATIC
				&& bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
				&& (bsm.getName().equals("metafactory")
						&& bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")
						|| bsm.getName().equals("altMetafactory")
						&& bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;"))
				&& !bsm.isInterface();
	}

	public static boolean isValidJavaIdentifier(String s) {
		if (s.isEmpty()) return false;

		int cp = s.codePointAt(0);
		if (!Character.isJavaIdentifierStart(cp)) return false;

		for (int i = Character.charCount(cp), max = s.length(); i < max; i += Character.charCount(cp)) {
			cp = s.codePointAt(i);
			if (!Character.isJavaIdentifierPart(cp)) return false;
		}

		return true;
	}

	public static int compareNatural(String a, String b) {
		final int lenA = a.length();
		final int lenB = b.length();
		int posA = 0;
		int lastSize = 0;

		for (int max = Math.min(lenA, lenB); posA < max; ) {
			int cA = Character.codePointAt(a, posA);
			int cB = Character.codePointAt(b, posA);

			if (cA != cB) break;

			lastSize = Character.charCount(cA);
			posA += lastSize;
		}

		if (posA == lenA && lenA == lenB) return 0;

		int posB = posA = posA - lastSize;
		int endA = -1;
		int endB = -1;

		for (;;) {
			int startA = posA;
			boolean isNumA = false;

			while (posA < lenA) {
				int c = Character.codePointAt(a, posA);

				if ((c >= '0' && c <= '9') != isNumA) {
					if (posA == startA) {
						isNumA = !isNumA; // isNum had the wrong initial value
					} else {
						if (endA < posA) {
							endA = posA;

							do {
								endA += Character.charCount(c);
							} while (endA < lenA && (c = Character.codePointAt(a, endA)) != '.' && c != '/');
						}

						break;
					}
				} else if (c == '.' || c == '/') {
					endA = posA; // unconditionally mark end to handle 0-length segments (those won't be re-visited)
					if (posA == startA) posA++; // consume only if first to avoid polluting comparisons within segments, otherwise trigger revisit
					break;
				} else if (c == '$') {
					if (posA == startA) posA++;
					break;
				}

				posA += Character.charCount(c);
			}

			int startB = posB;
			boolean isNumB = false;

			while (posB < lenB) {
				int c = Character.codePointAt(b, posB);

				if ((c >= '0' && c <= '9') != isNumB) {
					if (posB == startB) {
						isNumB = !isNumB;
					} else {
						if (endB < posB) {
							endB = posB;

							do {
								endB += Character.charCount(c);
							} while (endB < lenB && (c = Character.codePointAt(b, endB)) != '.' && c != '/');
						}

						break;
					}
				} else if (c == '.' || c == '/') {
					endB = posB;
					if (posB == startB) posB++;
					break;
				} else if (c == '$') {
					if (posB == startB) posB++;
					break;
				}

				posB += Character.charCount(c);
			}

			boolean hasEndA = endA >= startA && endA < lenA; // segment separator exists after current region
			boolean hasEndB = endB >= startB && endB < lenB;

			if (hasEndA != hasEndB) {
				return hasEndA ? 1 : -1;
			}

			if (isNumA && isNumB) {
				int segLenA = posA - startA;
				int segLenB = posB - startB;

				if (segLenA != segLenB) {
					return segLenA < segLenB ? -1 : 1;
				}
			}

			while (startA < posA) {
				if (startB == posB) return 1;

				int cA = Character.codePointAt(a, startA);
				int cB = Character.codePointAt(b, startB);

				if (cA != cB) {
					return cA < cB ? -1 : 1;
				}

				startA += Character.charCount(cA);
				startB += Character.charCount(cB);
			}

			if (startB != posB) return -1;
		}
	}

	public static final Object asmNodeSync = new Object();
}
