package matcher.mapping;

public enum MappingFormat {
	TINY("Tiny", "tiny"),
	TINY_GZIP("Tiny (gzipped)", "tiny.gz"),
	SRG("SRG", "srg");

	private MappingFormat(String name, String fileExt) {
		this.name = name;
		this.fileExt = fileExt;
	}

	public String getGlobPattern() {
		return "*."+fileExt;
	}

	public final String name;
	public final String fileExt;
}