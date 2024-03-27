module matcher {
	exports matcher.config;
	exports matcher.srcprocess;
	exports matcher.gui;
	exports matcher.gui.tab;
	exports matcher.type;
	exports matcher.gui.menu;
	exports matcher.mapping;
	exports matcher.classifier;
	exports matcher;
	exports matcher.bcremap;
	exports matcher.serdes;
	exports job4j;

	requires transitive org.slf4j;
	requires cfr;
	requires com.github.javaparser.core;
	requires org.vineflower.vineflower;
	requires java.prefs;
	requires transitive javafx.base;
	requires transitive javafx.controls;
	requires transitive javafx.graphics;
	requires transitive javafx.web;
	requires transitive org.controlsfx.controls;
	requires transitive org.objectweb.asm;
	requires transitive org.objectweb.asm.tree;
	requires org.objectweb.asm.commons;
	requires org.objectweb.asm.tree.analysis;
	requires org.objectweb.asm.util;
	requires procyon.compilertools;
	requires jadx.core;
	requires jadx.plugins.api;
	requires jadx.plugins.java_input;
	requires transitive net.fabricmc.mappingio;

	uses matcher.Plugin;
}
