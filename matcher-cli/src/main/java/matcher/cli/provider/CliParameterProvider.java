package matcher.cli.provider;

import com.beust.jcommander.Parameter;

/**
 * Top-level parameter provider.
 */
public interface CliParameterProvider {
	/**
	 * Instance of the class containing the {@link Parameter} annotations.
	 */
	Object getDataHolder();

	/**
	 * Verifies args and handles them.
	 */
	void processArgs();
}
