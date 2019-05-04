package matcher.mapping;

public enum MappingFormat {
	TINY("Tiny", "tiny", true, true, false),
	TINY_GZIP("Tiny (gzipped)", "tiny.gz", true, true, false),
	TINY_2("Tiny v2", "tiny", true, true, true),
	ENIGMA("Enigma", null, false, true, false),
	MCP("MCP", null, true, true, false),
	SRG("SRG", "srg", false, false, false);

	private MappingFormat(String name, String fileExt, boolean supportsComments, boolean supportsArgs, boolean supportsLocals) {
		this.name = name;
		this.fileExt = fileExt;
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
	public final boolean supportsComments;
	public final boolean supportsArgs;
	public final boolean supportsLocals;
}