module matcher.gui {
	requires cfr;
	requires com.github.javaparser.core;
	requires java.prefs;
	requires matcher.core;
	requires org.objectweb.asm.util;
	requires org.vineflower.vineflower;
	requires procyon.compilertools;
	requires jadx.core;
	requires jadx.plugins.api;
	requires jadx.plugins.java_input;
	requires transitive javafx.base;
	requires transitive javafx.controls;
	requires transitive javafx.graphics;
	requires transitive javafx.web;

	uses matcher.Plugin;

	exports matcher.gui;
	exports matcher.gui.srcprocess;
	exports matcher.gui.ui;
	exports matcher.gui.ui.menu;
	exports matcher.gui.ui.tab;
}
