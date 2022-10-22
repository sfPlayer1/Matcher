package matcher.srcprocess;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.jar.Manifest;

import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.modules.renamer.ConverterHelper;
import org.jetbrains.java.decompiler.modules.renamer.IdentifierConverter;
import org.jetbrains.java.decompiler.modules.renamer.PoolInterceptor;
import org.jetbrains.java.decompiler.struct.ContextUnit;
import org.jetbrains.java.decompiler.struct.IDecompiledData;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructContext;
import org.jetbrains.java.decompiler.struct.lazy.LazyLoader;
import org.jetbrains.java.decompiler.util.TextBuffer;

import matcher.NameType;
import matcher.type.ClassEnv;
import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;

public class Fernflower implements Decompiler {
	@Override
	public String decompile(ClassInstance cls, ClassFeatureExtractor env, NameType nameType) {
		// invoke ff with on-demand class lookup into matcher's state and string based output
		Map<String, Object> properties = new HashMap<>(IFernflowerPreferences.DEFAULTS);
		properties.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");

		ResultSaver resultSaver = new ResultSaver();
		DecompiledData data = new DecompiledData();
		BytecodeProvider bcProvider = new BytecodeProvider(env, nameType);
		MatcherStructContext structContext = new MatcherStructContext(resultSaver, data, new LazyLoader(bcProvider), env, nameType, bcProvider);
		ClassesProcessor classProcessor = new ClassesProcessor(structContext);
		PoolInterceptor interceptor = null;
		IIdentifierRenamer renamer;
		IdentifierConverter converter;

		if ("1".equals(properties.get(IFernflowerPreferences.RENAME_ENTITIES))) {
			renamer = new ConverterHelper();
			interceptor = new PoolInterceptor();
			converter = new IdentifierConverter(structContext, renamer, interceptor);
		} else {
			renamer = null;
			converter = null;
		}

		data.classProcessor = classProcessor;
		data.converter = converter;

		IFernflowerLogger logger = new PrintStreamLogger(System.out);
		DecompilerContext context = new DecompilerContext(properties, logger, structContext, classProcessor, interceptor);
		DecompilerContext.setCurrentContext(context);

		try {
			structContext.addSpace(cls, true);

			// queue inner classes as well
			if (!cls.getInnerClasses().isEmpty()) {
				Queue<ClassInstance> toAdd = new ArrayDeque<>(cls.getInnerClasses());
				ClassInstance innerCls;

				while ((innerCls = toAdd.poll()) != null) {
					structContext.addSpace(innerCls, true);
					toAdd.addAll(innerCls.getInnerClasses());
				}
			}

			if (converter != null) converter.rename();

			classProcessor.loadClasses(renamer);
			structContext.saveContext();
		} finally {
			DecompilerContext.setCurrentContext(null);
		}

		String ret = resultSaver.results.get(cls.getName(nameType));

		if (ret != null) {
			return ret;
		} else {
			throw new RuntimeException("decompiling "+cls+" didn't yield the expected result (available: "+resultSaver.results.keySet()+")");
		}
	}

	private static class MatcherStructContext extends StructContext {
		@SuppressWarnings("unchecked")
		MatcherStructContext(IResultSaver saver, IDecompiledData decompiledData, LazyLoader loader, ClassEnv env, NameType nameType, BytecodeProvider bcProvider) {
			super(saver, decompiledData, loader);

			this.loader = loader;
			this.env = env;
			this.nameType = nameType;
			this.bcProvider = bcProvider;

			try {
				Field f = StructContext.class.getDeclaredField("units");
				f.setAccessible(true);
				units = (Map<String, ContextUnit>) f.get(this);

				f = StructContext.class.getDeclaredField("classes");
				f.setAccessible(true);
				classes = (Map<String, StructClass>) f.get(this);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}

			ownedUnit = units.get("");
			unownedUnit = new ContextUnit(ContextUnit.TYPE_FOLDER, null, unownedUnitFilename, false, saver, decompiledData); // avoids producing superfluous output
			units.put(unownedUnitFilename, unownedUnit);
		}

		public void addSpace(ClassInstance cls, boolean isOwn) {
			try {
				addStructClass(cls, isOwn);
			} catch (IOException ex) {
				String message = "Corrupted class: " + cls.getName(nameType);
				DecompilerContext.getLogger().writeMessage(message, ex);
			}
		}

		@Override
		public StructClass getClass(String name) {
			if (DEBUG) System.out.printf("getClass(%s)%n", name);

			// use classes as a cache, load anything missing on demand
			StructClass ret = classes.get(name);
			if (ret != null) return ret;

			ClassInstance cls = env.getClsByName(name, nameType);
			if (cls == null) return null;

			try {
				return addStructClass(cls, false);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		private StructClass addStructClass(ClassInstance cls, boolean isOwn) throws IOException {
			String name = cls.getName(nameType);
			byte[] data = bcProvider.get(name); // BytecodeProvider has a name->byte[] cache to avoid redundant cls.serialize invocations
			if (data == null) throw new IllegalStateException();

			StructClass cl = new StructClass(data, isOwn, loader);
			classes.put(cl.qualifiedName, cl);

			ContextUnit unit = isOwn ? ownedUnit : unownedUnit;
			unit.addClass(cl, name.substring(name.lastIndexOf('/') + 1)+".class");
			loader.addClassLink(cl.qualifiedName, new LazyLoader.Link(pathPrefix+name+pathSuffix, null));

			return cl;
		}

		@Override
		public Map<String, StructClass> getClasses() {
			return emulatedClasses;
		}

		protected final LazyLoader loader;
		protected final ClassEnv env;
		protected final NameType nameType;
		protected final BytecodeProvider bcProvider;
		protected final Map<String, ContextUnit> units;
		protected final Map<String, StructClass> classes;
		protected final ContextUnit ownedUnit;
		protected final ContextUnit unownedUnit;

		private final Map<String, StructClass> emulatedClasses = new AbstractMap<String, StructClass>() {
			@Override
			public boolean containsKey(Object key) {
				return get(key) != null;
			}

			@Override
			public StructClass get(Object key) {
				if (!(key instanceof String)) return null;

				return MatcherStructContext.this.getClass((String) key);
			}

			@Override
			public int size() {
				return classes.size();
			}

			@Override
			public Set<Map.Entry<String, StructClass>> entrySet() {
				Set<Map.Entry<String, StructClass>> snapshot = new HashSet<>(classes.entrySet()); // copy to hide concurrent modifications from on-demand additions

				return snapshot;
			}
		};
	}

	private static class ResultSaver implements IResultSaver {
		@Override
		public void saveFolder(String path) { }
		@Override
		public void copyFile(String source, String path, String entryName) { }
		@Override
		public void createArchive(String path, String archiveName, Manifest manifest) { }
		@Override
		public void saveDirEntry(String path, String archiveName, String entryName) { }
		@Override
		public void copyEntry(String source, String path, String archiveName, String entry) { }
		@Override
		public void closeArchive(String path, String archiveName) { }
		@Override
		public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) { }

		@Override
		public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
			if (DEBUG) System.out.printf("saveClassFile(%s, %s, %s, %s, %s)%n", path, qualifiedName, entryName, content, Arrays.toString(mapping));

			results.put(qualifiedName, content);
		}

		Map<String, String> results = new HashMap<>();
	}

	private static class DecompiledData implements IDecompiledData {
		@Override
		public String getClassEntryName(StructClass cl, String entryname) {
			if (DEBUG) System.out.printf("getClassEntryName(%s, %s)%n", cl, entryname);

			ClassNode node = classProcessor.getMapRootClasses().get(cl.qualifiedName);

			if (node.type != ClassNode.CLASS_ROOT) {
				return null;
			} else if (converter != null) {
				String simpleClassName = cl.qualifiedName.substring(cl.qualifiedName.lastIndexOf('/') + 1);
				return entryname.substring(0, entryname.lastIndexOf('/') + 1) + simpleClassName + ".java";
			} else {
				return entryname.substring(0, entryname.lastIndexOf(".class")) + ".java";
			}
		}

		@Override
		public String getClassContent(StructClass cl) {
			if (DEBUG) System.out.printf("getClassContent(%s)%n", cl);

			try {
				TextBuffer buffer = new TextBuffer(ClassesProcessor.AVERAGE_CLASS_SIZE);
				buffer.append(DecompilerContext.getProperty(IFernflowerPreferences.BANNER).toString());
				classProcessor.writeClass(cl, buffer);
				return buffer.toString();
			} catch (Throwable t) {
				DecompilerContext.getLogger().writeMessage("Class " + cl.qualifiedName + " couldn't be fully decompiled.", t);
				return null;
			}
		}

		protected ClassesProcessor classProcessor;
		protected IdentifierConverter converter;
	}

	private static class BytecodeProvider implements IBytecodeProvider {
		BytecodeProvider(ClassEnv env, NameType nameType) {
			this.env = env;
			this.nameType = nameType;
		}

		@Override
		public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
			if (DEBUG) System.out.printf("getBytecode(%s, %s)%n", externalPath, internalPath);

			if (externalPath.startsWith(pathPrefix) && externalPath.endsWith(pathSuffix)) {
				String name = externalPath.substring(pathPrefix.length(), externalPath.length() - pathSuffix.length());
				byte[] ret = get(name);
				if (ret != null) return ret;
			}

			throw new FileNotFoundException("can't find class for "+externalPath);
		}

		public byte[] get(String name) {
			byte[] ret = cache.get(name);
			if (ret != null) return ret;

			ClassInstance cls = env.getClsByName(name, nameType);

			if (cls != null) {
				ret = cls.serialize(nameType);
				cache.put(name, ret);
			}

			return ret;
		}

		protected final ClassEnv env;
		protected final NameType nameType;

		private final Map<String, byte[]> cache = new HashMap<>();
	}

	private static final boolean DEBUG = false;

	private static final String pathPrefix = "/matchenv/";
	private static final String pathSuffix = ".class";
	private static final String unownedUnitFilename = "foreign";
}
