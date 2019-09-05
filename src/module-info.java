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

	requires cfr;
	requires com.github.javaparser.core;
	requires intellij.fernflower;
	requires java.prefs;
	requires transitive javafx.base;
	requires transitive javafx.controls;
	requires transitive javafx.graphics;
	requires transitive javafx.web;
	requires transitive org.objectweb.asm;
	requires org.objectweb.asm.commons;
	requires transitive org.objectweb.asm.tree;
	requires org.objectweb.asm.tree.analysis;
	requires org.objectweb.asm.util;
	requires procyon.compilertools;

	uses matcher.Plugin;
}