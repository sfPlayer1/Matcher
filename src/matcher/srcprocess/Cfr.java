package matcher.srcprocess;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageCollectorImpl;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.getopt.GetOptParser;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;
import org.benf.cfr.reader.util.output.StreamDumper;

import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;

public class Cfr implements Decompiler {
	@Override
	public synchronized String decompile(ClassInstance cls, ClassFeatureExtractor extractor, boolean mapped, boolean tmpNamed, boolean unmatchedTmp) {
		String name = cls.getName(mapped, tmpNamed, unmatchedTmp) + ".class";
		Options options = new GetOptParser().parse(new String[] { name }, OptionsImpl.getFactory()).getSecond();
		ClassFileSource source = new ClassFileSource() {
			@Override
			public void informAnalysisRelativePathDetail(String usePath, String specPath) {
				if (!usePath.equals(specPath)) {
					System.out.println("informAnalysisRelativePathDetail: "+specPath+" -> "+usePath);
					nameMap.put(specPath, usePath);
				}
			}

			@Override
			public String getPossiblyRenamedPath(String name) {
				return name;
			}

			@Override
			public Pair<byte[], String> getClassFileContent(String file) throws IOException {
				String realFile = nameMap.get(file);
				if (realFile != null) file = realFile;

				String clsName;

				if (file.endsWith(fileSuffix)) {
					clsName = file.substring(0, file.length() - fileSuffix.length());
				} else {
					clsName = file;
				}

				ClassInstance cls = extractor.getClsByName(clsName, mapped, tmpNamed, unmatchedTmp);

				if (cls == null || cls.getAsmNodes() == null) {
					if (warnedMissing.add(clsName)) System.out.println("can't find class "+clsName+" for "+file);

					throw new IOException("can't find class "+clsName+" for "+file);
				}

				byte[] data = extractor.serializeClass(cls, mapped, tmpNamed, unmatchedTmp);

				return Pair.make(data, file);
			}

			@Override
			public Collection<String> addJar(String var1) {
				throw new UnsupportedOperationException();
			}

			private final Map<String, String> nameMap = new HashMap<>();
			private final Set<String> warnedMissing = new HashSet<>();
		};

		DCCommonState state = new DCCommonState(options, source);

		ClassFile classFile = state.getClassFileMaybePath(name);
		state.configureWith(classFile);
		classFile = state.getClassFile(classFile.getClassType());
		classFile.analyseTop(state);

		TypeUsageCollector typeUsageCollector = new TypeUsageCollectorImpl(classFile);
		classFile.collectTypeUsages(typeUsageCollector);

		StringDumper dumper = new StringDumper(typeUsageCollector.getTypeUsageInformation(), options);
		classFile.dump(dumper);

		return dumper.toString();
	}

	private static class StringDumper extends StreamDumper {
		public StringDumper(TypeUsageInformation typeUsageInformation, Options options) {
			super(typeUsageInformation, options, new IllegalIdentifierDump.Nop());
		}

		@Override
		protected void write(String str) {
			buffer.append(str);
		}

		@Override
		public void addSummaryError(Method var1, String var2) { }

		@Override
		public void close() { }

		@Override
		public String toString() {
			return buffer.toString();
		}

		private final StringBuilder buffer = new StringBuilder();
	}

	private static final String fileSuffix = ".class";
}
