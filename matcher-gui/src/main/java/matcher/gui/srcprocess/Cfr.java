package matcher.gui.srcprocess;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;

import matcher.Matcher;
import matcher.model.NameType;
import matcher.model.type.ClassFeatureExtractor;
import matcher.model.type.ClassInstance;

public class Cfr implements Decompiler {
	@Override
	public synchronized String decompile(ClassInstance cls, ClassFeatureExtractor env, NameType nameType) {
		Map<String, String> options = new HashMap<>();

		Sink sink = new Sink();

		CfrDriver driver = new CfrDriver.Builder()
				.withOptions(options)
				.withClassFileSource(new Source(env, nameType))
				.withOutputSink(sink)
				.build();

		driver.analyse(Collections.singletonList(cls.getName(nameType).concat(fileSuffix)));

		return sink.toString();
	}

	private static class Source implements ClassFileSource {
		Source(ClassFeatureExtractor env, NameType nameType) {
			this.env = env;
			this.nameType = nameType;
		}

		@Override
		public void informAnalysisRelativePathDetail(String usePath, String classFilePath) {
			//Matcher.LOGGER.debug("informAnalysisRelativePathDetail {} {}", usePath, classFilePath);
		}

		@Override
		public Collection<String> addJar(String jarPath) {
			Matcher.LOGGER.debug("addJar {}", jarPath);

			throw new UnsupportedOperationException();
		}

		@Override
		public String getPossiblyRenamedPath(String path) {
			return path;
		}

		@Override
		public Pair<byte[], String> getClassFileContent(String path) throws IOException {
			if (!path.endsWith(fileSuffix)) {
				Matcher.LOGGER.debug("getClassFileContent invalid path: {}", path);
				throw new NoSuchFileException(path);
			}

			String clsName = path.substring(0, path.length() - fileSuffix.length());
			ClassInstance cls = env.getClsByName(clsName, nameType);

			if (cls == null) {
				Matcher.LOGGER.debug("getClassFileContent missing cls: {}", clsName);
				throw new NoSuchFileException(path);
			}

			if (cls.getAsmNodes() == null) {
				Matcher.LOGGER.debug("getClassFileContent unknown cls: {}", clsName);
				throw new NoSuchFileException(path);
			}

			byte[] data = cls.serialize(nameType);

			return Pair.make(data, path);
		}

		private final ClassFeatureExtractor env;
		private final NameType nameType;
	}

	private static class Sink implements OutputSinkFactory {
		@Override
		public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
			return Collections.singletonList(SinkClass.STRING);
		}

		@Override
		public <T> OutputSinkFactory.Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
			switch (sinkType) {
			case EXCEPTION:
				return str -> Matcher.LOGGER.error("CFR exception: {}", str);
			case JAVA:
				return sb::append;
			case PROGRESS:
				return str -> Matcher.LOGGER.debug("CFR progress: {}", str);
			case SUMMARY:
				return str -> Matcher.LOGGER.debug("CFR summary: {}", str);
			default:
				Matcher.LOGGER.debug("Unknown CFR sink type: {}", sinkType);
				return str -> Matcher.LOGGER.debug("{}", str);
			}
		}

		@Override
		public String toString() {
			return sb.toString();
		}

		private final StringBuilder sb = new StringBuilder();
	}

	private static final String fileSuffix = ".class";
}
