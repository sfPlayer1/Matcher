package matcher.srcprocess;

import java.io.IOException;
import java.util.function.Consumer;

import jadx.api.CommentsLevel;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.impl.NoOpCodeCache;
import jadx.api.plugins.input.data.IClassData;
import jadx.api.plugins.input.data.ILoadResult;
import jadx.api.plugins.input.data.IResourceData;
import jadx.plugins.input.java.JavaClassReader;
import jadx.plugins.input.java.data.JavaClassData;

import matcher.NameType;
import matcher.Util;
import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;

public class Jadx implements Decompiler {
	@Override
	public String decompile(ClassInstance cls, ClassFeatureExtractor env, NameType nameType) {
		String errorMessage = null;
		final String fullClassName = cls.getName(NameType.PLAIN, true);

		try (JadxDecompiler jadx = new JadxDecompiler(jadxArgs)) {
			jadx.addCustomLoad(new ILoadResult() {
				@Override
				public void close() throws IOException { }

				@Override
				public void visitClasses(Consumer<IClassData> consumer) {
					consumer.accept(new JavaClassData(new JavaClassReader(0,
							fullClassName + ".class", cls.serialize(nameType))));
				}

				@Override
				public void visitResources(Consumer<IResourceData> consumer) { }

				@Override
				public boolean isEmpty() {
					return false;
				}
			});
			jadx.load();

			assert jadx.getClassesWithInners().size() == 1;
			return jadx.getClassesWithInners().get(0).getCode();
		} catch (Exception e) {
			errorMessage = Util.getStackTrace(e);
		}

		throw new RuntimeException(errorMessage != null ? errorMessage : "JADX couldn't find the requested class");
	}

	private static final JadxArgs jadxArgs;

	static {
		jadxArgs = new JadxArgs() {
			@Override
			public void close() {
				return;
			}
		};
		jadxArgs.setCodeCache(NoOpCodeCache.INSTANCE);
		jadxArgs.setShowInconsistentCode(true);
		jadxArgs.setInlineAnonymousClasses(false);
		jadxArgs.setInlineMethods(false);
		jadxArgs.setSkipResources(true);
		jadxArgs.setRenameValid(false);
		jadxArgs.setRespectBytecodeAccModifiers(true);
		jadxArgs.setCommentsLevel(CommentsLevel.INFO);
	}
}
