package matcher.srcprocess;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jadx.api.CommentsLevel;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.impl.NoOpCodeCache;
import jadx.core.Consts;
import jadx.core.utils.Utils;

import matcher.NameType;
import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;
import matcher.type.InputFile;

public class Jadx implements Decompiler {
	@Override
	public synchronized String decompile(ClassInstance cls, ClassFeatureExtractor env, NameType nameType) {
		String errorMessage = null;
		String fullClassName = cls.getName(NameType.PLAIN, true);

		if (fullClassName.contains("$")) {
			errorMessage = "JADX doesn't support decompiling inner classes!";
		} else {
			JadxArgs jadxArgs = new JadxArgs();
			jadxArgs.setInputFiles(toActualFiles(env.getInputFiles()));
			jadxArgs.setCodeCache(new NoOpCodeCache());
			jadxArgs.setShowInconsistentCode(true);
			jadxArgs.setInlineAnonymousClasses(false);
			jadxArgs.setInlineMethods(false);
			jadxArgs.setSkipResources(true);
			jadxArgs.setRespectBytecodeAccModifiers(true);
			jadxArgs.setCommentsLevel(CommentsLevel.INFO);

			try (JadxDecompiler jadx = new JadxDecompiler(jadxArgs)) {
				jadx.load();
				String defpackage = Consts.DEFAULT_PACKAGE_NAME + ".";
				String jadxFullClassName;

				for (JavaClass jadxCls : jadx.getClassesWithInners()) {
					jadxFullClassName = jadxCls.getFullName().replace(defpackage, "");
					fullClassName = fullClassName
							.replace('/', '.')
							.replace('$', '.');

					if (jadxFullClassName.equals(fullClassName)) {
						return jadxCls.getCode();
					}
				}
			} catch (Exception e) {
				errorMessage = Utils.getStackTrace(e);
			}
		}

		throw new RuntimeException(errorMessage != null ? errorMessage : "JADX couldn't find the requested class");
	}

	private List<File> toActualFiles(Collection<InputFile> inputFiles) {
		List<File> files = new ArrayList<>();

		for (InputFile inputFile : inputFiles) {
			if (inputFile.path != null && inputFile.path.toFile().exists()) {
				files.add(inputFile.path.toFile());
			}
		}

		return files;
	}
}
