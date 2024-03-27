package matcher.jobs;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;

import job4j.JobState;

import matcher.Matcher;
import matcher.classifier.ClassifierLevel;
import matcher.classifier.MethodClassifier;
import matcher.type.MethodInstance;

public class AutoMatchMethodsJob extends MatcherJob<Boolean> {
	public AutoMatchMethodsJob(Matcher matcher, ClassifierLevel level) {
		super(JobCategories.AUTOMATCH_METHODS);

		this.matcher = matcher;
		this.level = level;
	}

	@Override
	protected Boolean execute(DoubleConsumer progressReceiver) {
		AtomicInteger totalUnmatched = new AtomicInteger();
		Map<MethodInstance, MethodInstance> matches = matcher.match(level, Matcher.absMethodAutoMatchThreshold, Matcher.relMethodAutoMatchThreshold,
				cls -> cls.getMethods(), MethodClassifier::rank, MethodClassifier.getMaxScore(level),
				progressReceiver, totalUnmatched);

		for (Map.Entry<MethodInstance, MethodInstance> entry : matches.entrySet()) {
			if (state == JobState.CANCELING) {
				break;
			}

			matcher.match(entry.getKey(), entry.getValue());
		}

		Matcher.LOGGER.info("Auto matched {} methods ({} unmatched)", matches.size(), totalUnmatched.get());

		return !matches.isEmpty();
	}

	private final Matcher matcher;
	private final ClassifierLevel level;
}
