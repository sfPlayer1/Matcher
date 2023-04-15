module matcher.core {
	requires java.prefs;
	requires org.objectweb.asm.commons;
	requires org.objectweb.asm.tree.analysis;
	requires org.objectweb.asm.util;
	requires transitive net.fabricmc.mappingio;
	requires transitive org.objectweb.asm;
	requires transitive org.objectweb.asm.tree;
	requires transitive org.slf4j;

	uses matcher.Plugin;

	exports matcher;
	exports matcher.bcremap;
	exports matcher.classifier;
	exports matcher.config;
	exports matcher.mapping;
	exports matcher.serdes;
	exports matcher.type;
}
