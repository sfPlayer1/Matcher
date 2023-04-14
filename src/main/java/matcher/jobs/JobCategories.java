package matcher.jobs;

import job4j.JobCategory;

public class JobCategories {
	public static final JobCategory INIT_MATCHER = new JobCategory("init-matcher");
	public static final JobCategory LOAD_PROJECT = new JobCategory("load-project", INIT_MATCHER);
	public static final JobCategory INIT_ENV = new JobCategory("init-env", LOAD_PROJECT);
	public static final JobCategory MATCH_UNOBFUSCATED = new JobCategory("match-unobfuscated", LOAD_PROJECT);
	public static final JobCategory NETWORK_SYNC = new JobCategory("network-sync");

	public static final JobCategory IMPORT_MATCHES = new JobCategory("import-matches");
	public static final JobCategory RANK_MATCHES = new JobCategory("rank-matches");
	public static final JobCategory SUBMIT_MATCHES = new JobCategory("submit-matches", NETWORK_SYNC);

	public static final JobCategory DECOMPILE = new JobCategory("decompile");
	public static final JobCategory DECOMPILE_SOURCE = new JobCategory("decompile-source", DECOMPILE);
	public static final JobCategory DECOMPILE_DEST = new JobCategory("decompile-dest", DECOMPILE);

	public static final JobCategory AUTOMATCH = new JobCategory("automatch");

	public static final JobCategory AUTOMATCH_ALL = new JobCategory("automatch-all", AUTOMATCH);
	public static final JobCategory AUTOMATCH_ALL_INTERMEDIATE = new JobCategory("automatch-all:intermediate", AUTOMATCH_ALL);
	public static final JobCategory AUTOMATCH_ALL_FULL = new JobCategory("automatch-all:full", AUTOMATCH_ALL);
	public static final JobCategory AUTOMATCH_ALL_EXTRA = new JobCategory("automatch-all:extra", AUTOMATCH_ALL);
	public static final JobCategory AUTOMATCH_ALL_LOCALS = new JobCategory("automatch-all:locals", AUTOMATCH_ALL);

	public static final JobCategory AUTOMATCH_CLASSES = new JobCategory("automatch-classes", AUTOMATCH);
	public static final JobCategory AUTOMATCH_FIELDS = new JobCategory("automatch-fields", AUTOMATCH);
	public static final JobCategory AUTOMATCH_METHODS = new JobCategory("automatch-methods", AUTOMATCH);
	public static final JobCategory AUTOMATCH_LOCALS = new JobCategory("automatch-locals", AUTOMATCH);
	public static final JobCategory AUTOMATCH_METHOD_ARGS = new JobCategory("automatch-locals:method-args", AUTOMATCH_LOCALS);
	public static final JobCategory AUTOMATCH_METHOD_VARS = new JobCategory("automatch-locals:method-vars", AUTOMATCH_LOCALS);

	public static final JobCategory PROPAGATE_METHOD_NAMES = new JobCategory("propagate-method-names");
}
