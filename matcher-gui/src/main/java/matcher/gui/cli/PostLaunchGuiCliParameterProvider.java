package matcher.gui.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.beust.jcommander.Parameter;

import matcher.cli.provider.CliParameterProvider;
import matcher.cli.provider.builtin.BuiltinCliParameters;
import matcher.gui.MatcherGui;
import matcher.model.config.ProjectConfig;

public class PostLaunchGuiCliParameterProvider implements CliParameterProvider {
	public PostLaunchGuiCliParameterProvider(MatcherGui gui) {
		this.gui = gui;
	}

	@Parameter(names = {BuiltinCliParameters.INPUTS_A})
	List<Path> inputsA = defaultInputsA;

	@Parameter(names = {BuiltinCliParameters.INPUTS_B})
	List<Path> inputsB = defaultInputsB;

	@Parameter(names = {BuiltinCliParameters.CLASSPATH_A})
	List<Path> classpathA = defaultClasspathA;

	@Parameter(names = {BuiltinCliParameters.CLASSPATH_B})
	List<Path> classpathB = defaultClasspathB;

	@Parameter(names = {BuiltinCliParameters.SHARED_CLASSPATH})
	List<Path> sharedClasspath = defaultSharedClasspath;

	@Parameter(names = {BuiltinCliParameters.INPUTS_BEFORE_CLASSPATH})
	boolean inputsBeforeClasspath = defaultInputsBeforeClasspath;

	@Parameter(names = {BuiltinCliParameters.NON_OBFUSCATED_CLASS_PATTERN_A})
	String nonObfuscatedClassPatternA = defaultNonObfuscatedClassPatternA;

	@Parameter(names = {BuiltinCliParameters.NON_OBFUSCATED_CLASS_PATTERN_B})
	String nonObfuscatedClassPatternB = defaultNonObfuscatedClassPatternB;

	@Parameter(names = {BuiltinCliParameters.NON_OBFUSCATED_MEMBER_PATTERN_A})
	String nonObfuscatedMemberPatternA = defaultNonObfuscatedMemberPatternA;

	@Parameter(names = {BuiltinCliParameters.NON_OBFUSCATED_MEMBER_PATTERN_B})
	String nonObfuscatedMemberPatternB = defaultNonObfuscatedMemberPatternB;

	@Parameter(names = {BuiltinCliParameters.MAPPINGS_A})
	Path mappingsPathA;

	@Parameter(names = {BuiltinCliParameters.MAPPINGS_B})
	Path mappingsPathB;

	@Parameter(names = {BuiltinCliParameters.DONT_SAVE_UNMAPPED_MATCHES})
	boolean dontSaveUnmappedMatches;

	@Parameter(names = {BuiltinCliParameters.OUTPUT_FILE})
	Path outputFile;

	@Parameter(names = {BuiltinGuiCliParameters.HIDE_UNMAPPED_A})
	boolean hideUnmappedA;

	@Override
	public Object getDataHolder() {
		return this;
	}

	@Override
	public void processArgs() {
		gui.setHideUnmappedA(hideUnmappedA);

		if (!inputsA.equals(defaultInputsA)
				|| !inputsB.equals(defaultInputsB)
				|| !classpathA.equals(defaultClasspathA)
				|| !classpathB.equals(defaultClasspathB)
				|| !sharedClasspath.equals(defaultSharedClasspath)
				|| inputsBeforeClasspath != defaultInputsBeforeClasspath
				|| !nonObfuscatedClassPatternA.equals(defaultNonObfuscatedClassPatternA)
				|| !nonObfuscatedClassPatternB.equals(defaultNonObfuscatedClassPatternB)
				|| !nonObfuscatedMemberPatternA.equals(defaultNonObfuscatedMemberPatternA)
				|| !nonObfuscatedMemberPatternB.equals(defaultNonObfuscatedMemberPatternB)) {
			ProjectConfig config = new ProjectConfig.Builder(inputsA, inputsB)
					.classPathA(new ArrayList<>(classpathA))
					.classPathB(new ArrayList<>(classpathB))
					.sharedClassPath(new ArrayList<>(sharedClasspath))
					.inputsBeforeClassPath(inputsBeforeClasspath)
					.mappingsPathA(mappingsPathA)
					.mappingsPathB(mappingsPathB)
					.saveUnmappedMatches(!dontSaveUnmappedMatches)
					.nonObfuscatedClassPatternA(nonObfuscatedClassPatternA)
					.nonObfuscatedClassPatternB(nonObfuscatedClassPatternB)
					.nonObfuscatedMemberPatternA(nonObfuscatedMemberPatternA)
					.nonObfuscatedMemberPatternB(nonObfuscatedMemberPatternB)
					.build();

			gui.newProject(config, inputsA.isEmpty() || inputsB.isEmpty());
		}
	}

	private static final List<Path> defaultInputsA = Collections.emptyList();
	private static final List<Path> defaultInputsB = Collections.emptyList();
	private static final List<Path> defaultClasspathA = Collections.emptyList();
	private static final List<Path> defaultClasspathB = Collections.emptyList();
	private static final List<Path> defaultSharedClasspath = Collections.emptyList();
	private static final boolean defaultInputsBeforeClasspath = false;
	private static final String defaultNonObfuscatedClassPatternA = "";
	private static final String defaultNonObfuscatedClassPatternB = "";
	private static final String defaultNonObfuscatedMemberPatternA = "";
	private static final String defaultNonObfuscatedMemberPatternB = "";
	private final MatcherGui gui;
}
