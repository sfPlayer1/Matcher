package matcher.mapping;

public enum MappingFormat {
	TINY("Tiny", "tiny", true, true),
	TINY_GZIP("Tiny (gzipped)", "tiny.gz", true, true),
	ENIGMA("Enigma", null, false, true),
	MCP("MCP", null, true, true),
	SRG("SRG", "srg", false, false);

	private MappingFormat(String name, String fileExt, boolean supportsComments, boolean supportsArgs) {
		this.name = name;
		this.fileExt = fileExt;
		this.supportsComments = supportsComments;
		this.supportsArgs = supportsArgs;
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
}