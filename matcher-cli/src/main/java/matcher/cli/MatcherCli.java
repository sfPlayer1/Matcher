package matcher.cli;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.beust.jcommander.JCommander;

import matcher.cli.provider.CliCommandProvider;
import matcher.cli.provider.CliParameterProvider;

/**
 * Main CLI arg handler.
 * You have to first register your {@link CliParameterProvider}s and/or {@link CliCommandProvider}s
 * via their respective register methods, and then call {@link #processArgs(String[])}.
 * JCommander attempts to parse the passed args into the providers, and if applicable,
 * then proceeds to call their own {@code processArgs} methods, from where they're free
 * to handle the parsed arguments as needed.
 */
public class MatcherCli {
	public MatcherCli(boolean acceptUnknownParams) {
		this.acceptUnknownParams = acceptUnknownParams;
	}

	public void registerParameterProvider(CliParameterProvider paramProvider) {
		paramProviders.add(paramProvider);
	}

	public void registerCommandProvider(CliCommandProvider commandProvider) {
		commandProviders.add(commandProvider);
	}

	public void processArgs(String[] args) {
		JCommander.Builder jcBuilder = JCommander.newBuilder();

		// Top level parameter providers
		for (CliParameterProvider paramProvider : paramProviders) {
			jcBuilder.addObject(paramProvider.getDataHolder());
		}

		// Command providers
		for (CliCommandProvider commandProvider : commandProviders) {
			jcBuilder.addCommand(commandProvider.getCommandName(), commandProvider.getDataHolder());
		}

		JCommander jCommander = jcBuilder.build();
		jCommander.setAcceptUnknownOptions(acceptUnknownParams);
		jCommander.parse(args);

		if (args.length == 0) {
			jCommander.usage();
			return;
		}

		for (CliParameterProvider paramProvider : paramProviders) {
			paramProvider.processArgs();
		}

		for (CliCommandProvider commandProvider : commandProviders) {
			if (commandProvider.getCommandName().equals(jCommander.getParsedCommand())) {
				commandProvider.processArgs();
				break;
			}
		}
	}

	public static final Logger LOGGER = LoggerFactory.getLogger("Matcher CLI");
	private final List<CliParameterProvider> paramProviders = new ArrayList<>(5);
	private final List<CliCommandProvider> commandProviders = new ArrayList<>(5);
	private final boolean acceptUnknownParams;
}
