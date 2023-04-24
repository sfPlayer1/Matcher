module matcher.cli {
	requires transitive jcommander;
	requires transitive matcher.core;

	uses matcher.Plugin;

	exports matcher.cli;
	exports matcher.cli.provider;
}
