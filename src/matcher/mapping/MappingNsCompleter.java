package matcher.mapping;

import java.util.List;
import java.util.Map;

public final class MappingNsCompleter extends ForwardingMappingVisitor {
	public MappingNsCompleter(MappingVisitor next, Map<String, String> alternatives) {
		super(next);

		this.alternatives = alternatives;
	}

	@Override
	public boolean visitHeader() {
		relayHeaderOrMetadata = next.visitHeader();

		return true;
	}

	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) {
		int count = dstNamespaces.size();
		alternativesMapping = new int[count];
		pendingNamespaces = new int[count];
		pendingDstNames = new String[count];
		pendingNameCount = 0;

		for (int i = 0; i < count; i++) {
			String src = alternatives.get(dstNamespaces.get(i));
			int srcIdx;

			if (src == null) {
				srcIdx = i;
			} else if (src.equals(srcNamespace)) {
				srcIdx = -1;
			} else {
				srcIdx = dstNamespaces.indexOf(src);
				if (srcIdx < 0) throw new RuntimeException("invalid alternative mapping ns "+src+": not in "+dstNamespaces+" or "+srcNamespace);
			}

			alternativesMapping[i] = srcIdx;
		}

		if (relayHeaderOrMetadata) next.visitNamespaces(srcNamespace, dstNamespaces);
	}

	@Override
	public void visitMetadata(String key, String value) {
		if (relayHeaderOrMetadata) next.visitMetadata(key, value);
	}

	@Override
	public boolean visitContent() {
		relayHeaderOrMetadata = true; // for in-content metadata

		return next.visitContent();
	}

	@Override
	public boolean visitClass(String srcName) {
		this.srcName = srcName;

		return next.visitClass(srcName);
	}

	@Override
	public boolean visitField(String srcName, String srcDesc) {
		this.srcName = srcName;

		return next.visitField(srcName, srcDesc);
	}

	@Override
	public boolean visitMethod(String srcName, String srcDesc) {
		this.srcName = srcName;

		return next.visitMethod(srcName, srcDesc);
	}

	@Override
	public boolean visitMethodArg(int argPosition,  int lvIndex, String srcName) {
		this.srcName = srcName;

		return next.visitMethodArg(argPosition, lvIndex, srcName);
	}

	@Override
	public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, String srcName) {
		this.srcName = srcName;

		return next.visitMethodVar(lvtRowIndex, lvIndex, startOpIdx, srcName);
	}

	@Override
	public void visitDstName(MappedElementKind targetKind, int namespace, String name) {
		if (name != null && pendingNameCount == 0) {
			next.visitDstName(targetKind, namespace, name);
		} else {
			if (pendingNameCount > pendingNamespaces.length) throw new IllegalArgumentException("too many dst names");

			pendingNamespaces[pendingNameCount] = namespace;
			pendingDstNames[pendingNameCount] = name;
			pendingNameCount++;
		}
	}

	@Override
	public boolean visitElementContent(MappedElementKind targetKind) {
		for (int i = 0; i < pendingNameCount; i++) {
			int ns = pendingNamespaces[i];
			String name = pendingDstNames[i];

			if (name == null) {
				int src = ns;
				long visited = 1L << src;

				do {
					int newSrc = alternativesMapping[src];

					if (newSrc < 0) { // mapping to src name
						name = srcName;
						break;
					} else if (newSrc == src) { // no-op (identity) mapping, explicit in case src > 64
						break;
					} else if ((visited & 1L << newSrc) != 0) { // cyclic mapping
						break;
					} else {
						src = newSrc;
						name = pendingDstNames[src];
						visited |= 1L << src;
					}
				} while (name == null);
			}

			next.visitDstName(targetKind, ns, name);
		}

		pendingNameCount = 0;

		return next.visitElementContent(targetKind);
	}

	private final Map<String, String> alternatives;
	private int[] alternativesMapping;

	private String srcName;
	private int[] pendingNamespaces;
	private String[] pendingDstNames;
	private int pendingNameCount;

	private boolean relayHeaderOrMetadata;
}
