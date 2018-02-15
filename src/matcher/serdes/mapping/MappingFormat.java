package matcher.serdes.mapping;

public enum MappingFormat {
	TINY("Tiny", "tiny"),
	TINY_GZIP("Tiny (gzipped)", "tiny.gz"),
	ENIGMA("Enigma", null),
	MCP("MCP", null),
	SRG("SRG", "srg");

	private MappingFormat(String name, String fileExt) {
		this.name = name;
		this.fileExt = fileExt;
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
}