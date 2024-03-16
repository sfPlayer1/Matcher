module matcher.core {
	requires transitive matcher.model;

	uses matcher.Plugin;

	exports matcher;
	exports matcher.serdes;
}
