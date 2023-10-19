module matcher.model {
	requires java.prefs;
	requires org.objectweb.asm.commons;
	requires org.objectweb.asm.tree.analysis;
	requires org.objectweb.asm.util;
	requires transitive net.fabricmc.mappingio;
	requires transitive org.objectweb.asm;
	requires transitive org.objectweb.asm.tree;
	requires transitive org.slf4j;

	exports matcher.model;
	exports matcher.model.bcremap;
	exports matcher.model.classifier;
	exports matcher.model.config;
	exports matcher.model.mapping;
	exports matcher.model.type;
}
