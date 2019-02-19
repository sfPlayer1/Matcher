package matcher.srcprocess;

import java.util.HashSet;
import java.util.Set;

import com.strobel.assembler.metadata.Buffer;
import com.strobel.assembler.metadata.ClasspathTypeLoader;
import com.strobel.assembler.metadata.CompositeTypeLoader;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;

import matcher.NameType;
import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;

public class Procyon implements Decompiler {
	@Override
	public String decompile(ClassInstance cls, ClassFeatureExtractor env, NameType nameType) {
		DecompilerSettings settings = DecompilerSettings.javaDefaults();
		settings.setShowSyntheticMembers(true);
		settings.setTypeLoader(new CompositeTypeLoader(
				new TypeLoader(env, nameType),
				new ClasspathTypeLoader()));

		PlainTextOutput out = new PlainTextOutput();

		com.strobel.decompiler.Decompiler.decompile(cls.getName(nameType), out, settings);

		return out.toString();
	}

	private static class TypeLoader implements ITypeLoader {
		TypeLoader(ClassFeatureExtractor env, NameType nameType) {
			this.env = env;
			this.nameType = nameType;
		}

		@Override
		public boolean tryLoadType(String internalName, Buffer buffer) {
			ClassInstance cls = env.getClsByName(internalName, nameType);

			if (cls == null) {
				if (checkWarn(internalName)) {
					System.out.printf("missing cls: %s%n", internalName);
				}

				return false;
			}

			if (cls.getAsmNodes() == null) {
				if (checkWarn(internalName)) {
					System.out.printf("unknown cls: %s%n", internalName);
				}

				return false;
			}

			byte[] data = cls.serialize(nameType);

			buffer.reset(data.length);
			buffer.putByteArray(data, 0, data.length);
			buffer.position(0);

			return true;
		}

		private boolean checkWarn(String name) {
			if (name.startsWith("java/") || name.startsWith("sun/")) return false;

			return warnedClasses.add(name);
		}

		private final ClassFeatureExtractor env;
		private final NameType nameType;

		private final Set<String> warnedClasses = new HashSet<>();
	}
}
