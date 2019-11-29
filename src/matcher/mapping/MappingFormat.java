package matcher.mapping;

public enum MappingFormat {
	TINY("Tiny", "tiny", false, true, true, false, false, false),
	TINY_GZIP("Tiny (gzipped)", "tiny.gz", true, true, true, false, false, false),
	TINY_2("Tiny v2", "tiny", false, true, true, true, true, true),
	ENIGMA("Enigma", null, false, false, true, true, true, false),
	MCP("MCP", null, false, false, false, true, true, false),
	SRG("SRG", "srg", false, false, false, false, false, false),
	TSRG("TSRG", "tsrg", false, false, false, false, false, false),
	PROGUARD("ProGuard", "map", false, false, true, false, false, false);

	private MappingFormat(String name, String fileExt, boolean isGzipped,
			boolean hasNamespaces, boolean hasFieldDescriptors,
			boolean supportsComments, boolean supportsArgs, boolean supportsLocals) {
		this.name = name;
		this.fileExt = fileExt;
		this.isGzipped = isGzipped;
		this.hasNamespaces = hasNamespaces;
		this.hasFieldDescriptors = hasFieldDescriptors;
		this.supportsComments = supportsComments;
		this.supportsArgs = supportsArgs;
		this.supportsLocals = supportsLocals;
	}

	public boolean hasSingleFile() {
		return fileExt != null;
	}

	public String getGlobPattern() {
		if (fileExt == null) throw new UnsupportedOperationException("not applicable to dir based format");

		return "*."+fileExt;
	}

	public final String name;
	public final String fileExt;
	public final boolean isGzipped;
	public final boolean hasNamespaces;
	public final boolean hasFieldDescriptors;
	public final boolean supportsComments;
	public final boolean supportsArgs;
	public final boolean supportsLocals;
}