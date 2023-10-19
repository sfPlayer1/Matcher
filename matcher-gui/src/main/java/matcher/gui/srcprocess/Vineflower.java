package matcher.gui.srcprocess;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IContextSource.IOutputSink;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import matcher.Matcher;
import matcher.model.NameType;
import matcher.model.type.ClassFeatureExtractor;
import matcher.model.type.ClassInstance;

public class Vineflower implements Decompiler {
	@Override
	public String decompile(ClassInstance cls, ClassFeatureExtractor env, NameType nameType) {
		// invoke VF with on-demand class lookup into matcher's state and string based output
		Map<String, Object> properties = new HashMap<>(IFernflowerPreferences.DEFAULTS);
		properties.put(IFernflowerPreferences.REMOVE_BRIDGE, "0");
		properties.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "0");
		properties.put(IFernflowerPreferences.INDENT_STRING, "\n");
		properties.put(IFernflowerPreferences.THREADS, String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors() - 2)));
		properties.put(IFernflowerPreferences.LOG_LEVEL, IFernflowerLogger.Severity.WARN.name());

		OutputSink sink = new OutputSink();
		BaseDecompiler decompiler = new BaseDecompiler(NopResultSaver.INSTANCE, properties, new PrintStreamLogger(System.out));
		decompiler.addSource(new MatcherClsSource(cls, nameType, sink));
		decompiler.decompileContext();
		return sink.results.get(cls.getName(nameType));
	}

	private static class NopResultSaver implements IResultSaver {
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
		public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) { }

		static final NopResultSaver INSTANCE = new NopResultSaver();
	}

	private static class MatcherClsSource implements IContextSource {
		MatcherClsSource(ClassInstance cls, NameType nameType, IOutputSink outputSink) {
			this.cls = cls;
			this.nameType = nameType;
			this.outputSink = outputSink;
		}

		@Override
		public String getName() {
			return "Matcher class provider";
		}

		private String getClsName(ClassInstance cls, NameType nameType) {
			String name = cls.getName(nameType);
			return name == null || name.isEmpty() ? cls.getName() : name;
		}

		@Override
		public Entries getEntries() {
			String name = getClsName(cls, nameType);
			List<Entry> entries = new ArrayList<>();
			entries.add(Entry.parse(name));
			bytecodeByClsName.put(name, cls.serialize(nameType));

			for (ClassInstance innerCls : cls.getInnerClasses()) {
				String innerName = getClsName(innerCls, nameType);
				entries.add(Entry.parse(innerName));
				bytecodeByClsName.put(innerName, innerCls.serialize(nameType));
			}

			return new Entries(entries, List.of(), List.of());
		}

		@Override
		public InputStream getInputStream(String resource) throws IOException {
			resource = resource.substring(0, resource.length() - ".class".length());
			byte[] bytecode;

			if ((bytecode = bytecodeByClsName.get(resource)) == null) {
				throw new IOException("Requested class not in decompilation scope: "+resource);
			}

			return new ByteArrayInputStream(bytecode);
		}

		@Override
		public IOutputSink createOutputSink(IResultSaver saver) {
			return outputSink;
		}

		private final ClassInstance cls;
		private final NameType nameType;
		private final IOutputSink outputSink;
		/** name->byte[] cache to avoid redundant cls.serialize invocations. */
		private final Map<String, byte[]> bytecodeByClsName = new HashMap<>();
	}

	private static class OutputSink implements IOutputSink {
		@Override
		public void begin() { }

		@Override
		public void acceptClass(String qualifiedName, String fileName, String content, int[] mapping) {
			if (DEBUG) Matcher.LOGGER.debug("acceptClass({}, {}, {}, {})", qualifiedName, fileName, content, Arrays.toString(mapping));

			results.put(qualifiedName, content);
		}

		@Override
		public void acceptDirectory(String directory) { }

		@Override
		public void acceptOther(String path) { }

		@Override
		public void close() throws IOException { }

		private Map<String, String> results = new HashMap<>();
	}

	private static final boolean DEBUG = false;
}
