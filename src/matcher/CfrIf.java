package matcher;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.getopt.GetOptParser;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;

public class CfrIf {
	public static String decompile(ClassInstance cls, ClassFeatureExtractor extractor, boolean mapped) {
		Options options = new GetOptParser().parse(new String[] { cls.getName() }, OptionsImpl.getFactory());
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

				assert file.endsWith(fileSuffix);
				String clsName = file.substring(0, file.length() - fileSuffix.length());

				ClassInstance cls = null;

				if (mapped) {
					for (ClassInstance cCls : extractor.getClasses().values()) {
						if (clsName.equals(cCls.getMappedName())) {
							file = cCls.getName();
							cls = cCls;
							break;
						}
					}
				}

				if (cls == null) cls = extractor.getClassInstance(clsName);

				if (cls == null) {
					if (warnedMissing.add(clsName)) System.out.println("can't find class file for "+file);

					throw new IOException("can't find class file for "+file);
				}

				byte[] data = extractor.serializeClass(cls, mapped);

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

		ClassFile classFile = state.getClassFileMaybePath(cls.getName());
		state.configureWith(classFile);

		classFile.analyseTop(state);

		TypeUsageCollector typeUsageCollector = new TypeUsageCollector(classFile);
		classFile.collectTypeUsages(typeUsageCollector);

		Dumper dumper = new ToStringDumper();
		classFile.dump(dumper);

		return dumper.toString();
	}

	private static final String fileSuffix = ".class";
}
