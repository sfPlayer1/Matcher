package matcher.jobs;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;

import matcher.Matcher;
import matcher.classifier.ClassifierLevel;
import matcher.classifier.FieldClassifier;
import matcher.type.FieldInstance;

public class AutoMatchFieldsJob extends MatcherJob<Boolean> {
	public AutoMatchFieldsJob(Matcher matcher, ClassifierLevel level) {
		super(JobCategories.AUTOMATCH_FIELDS);

		this.matcher = matcher;
		this.level = level;
	}

	@Override
	protected Boolean execute(DoubleConsumer progressReceiver) {
		AtomicInteger totalUnmatched = new AtomicInteger();
		double maxScore = FieldClassifier.getMaxScore(level);

		Map<FieldInstance, FieldInstance> matches = matcher.match(level,
				Matcher.absFieldAutoMatchThreshold, Matcher.relFieldAutoMatchThreshold,
				cls -> cls.getFields(), FieldClassifier::rank, maxScore,
				progressReceiver, totalUnmatched);

		for (Map.Entry<FieldInstance, FieldInstance> entry : matches.entrySet()) {
			matcher.match(entry.getKey(), entry.getValue());
		}

		Matcher.LOGGER.info("Auto matched {} fields ({} unmatched)", matches.size(), totalUnmatched.get());

		return !matches.isEmpty();
	}

	private final Matcher matcher;
	private final ClassifierLevel level;
}
