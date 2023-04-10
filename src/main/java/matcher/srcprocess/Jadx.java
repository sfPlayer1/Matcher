package matcher.srcprocess;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import jadx.api.CommentsLevel;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.impl.NoOpCodeCache;
import jadx.api.plugins.input.data.IClassData;
import jadx.api.plugins.input.data.ILoadResult;
import jadx.api.plugins.input.data.IResourceData;
import jadx.core.utils.Utils;
import jadx.plugins.input.java.JavaClassReader;
import jadx.plugins.input.java.data.JavaClassData;

import matcher.NameType;
import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;

public class Jadx implements Decompiler {
	@Override
	public String decompile(ClassInstance cls, ClassFeatureExtractor env, NameType nameType) {
		String errorMessage = null;
		final String fullClassName = cls.getName(NameType.PLAIN, true);

		if (fullClassName.contains("$")) {
			errorMessage = "JADX doesn't support decompiling inner classes!";
		} else {
			try (JadxDecompiler jadx = new JadxDecompiler(jadxArgs)) {
				jadx.addCustomLoad(new ILoadResult() {
					@Override
					public void close() throws IOException {
						return;
					}

					@Override
					public void visitClasses(Consumer<IClassData> consumer) {
						consumer.accept(new JavaClassData(new JavaClassReader(idGenerator.getAndIncrement(),
								fullClassName + ".class", cls.serialize(nameType))));
					}

					@Override
					public void visitResources(Consumer<IResourceData> consumer) {
						return;
					}

					@Override
					public boolean isEmpty() {
						return false;
					}
				});
				jadx.load();

				assert jadx.getClassesWithInners().size() == 1;
				return jadx.getClassesWithInners().get(0).getCode();
			} catch (Exception e) {
				errorMessage = Utils.getStackTrace(e);
			}
		}

		throw new RuntimeException(errorMessage != null ? errorMessage : "JADX couldn't find the requested class");
	}

	private static final JadxArgs jadxArgs;
	private static final AtomicInteger idGenerator = new AtomicInteger();

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
		jadxArgs.setRespectBytecodeAccModifiers(true);
		jadxArgs.setCommentsLevel(CommentsLevel.INFO);
	}
}
