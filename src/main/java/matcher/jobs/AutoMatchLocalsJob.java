package matcher.jobs;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import job4j.JobState;

import matcher.Matcher;
import matcher.classifier.ClassifierLevel;
import matcher.classifier.MethodVarClassifier;
import matcher.classifier.RankResult;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class AutoMatchLocalsJob extends MatcherJob<Boolean> {
	public AutoMatchLocalsJob(Matcher matcher, ClassifierLevel level, boolean args) {
		super(JobCategories.AUTOMATCH_LOCALS);

		this.matcher = matcher;
		this.level = level;
		this.args = args;
	}

	@Override
	protected Boolean execute(DoubleConsumer progressReceiver) {
		Function<MethodInstance, MethodVarInstance[]> supplier;
		double absThreshold, relThreshold;

		if (args) {
			supplier = MethodInstance::getArgs;
			absThreshold = Matcher.absMethodArgAutoMatchThreshold;
			relThreshold = Matcher.relMethodArgAutoMatchThreshold;
		} else {
			supplier = MethodInstance::getVars;
			absThreshold = Matcher.absMethodVarAutoMatchThreshold;
			relThreshold = Matcher.relMethodVarAutoMatchThreshold;
		}

		List<MethodInstance> methods = matcher.getEnv().getClassesA().stream()
				.filter(cls -> cls.isReal() && cls.hasMatch() && cls.getMethods().length > 0)
				.flatMap(cls -> Stream.<MethodInstance>of(cls.getMethods()))
				.filter(m -> m.hasMatch() && supplier.apply(m).length > 0)
				.filter(m -> {
					for (MethodVarInstance a : supplier.apply(m)) {
						if (!a.hasMatch() && a.isMatchable()) return true;
					}

					return false;
				})
				.collect(Collectors.toList());
		Map<MethodVarInstance, MethodVarInstance> matches;
		AtomicInteger totalUnmatched = new AtomicInteger();

		if (methods.isEmpty()) {
			matches = Collections.emptyMap();
		} else {
			double maxScore = MethodVarClassifier.getMaxScore(level);
			double maxMismatch = maxScore - Matcher.getRawScore(absThreshold * (1 - relThreshold), maxScore);
			matches = new ConcurrentHashMap<>(512);

			Matcher.runInParallel(methods, m -> {
				int unmatched = 0;

				for (MethodVarInstance var : supplier.apply(m)) {
					if (state == JobState.CANCELING) {
						break;
					}

					if (var.hasMatch() || !var.isMatchable()) continue;

					List<RankResult<MethodVarInstance>> ranking = MethodVarClassifier.rank(var, supplier.apply(m.getMatch()), level, matcher.getEnv(), maxMismatch);

					if (Matcher.checkRank(ranking, absThreshold, relThreshold, maxScore)) {
						MethodVarInstance match = ranking.get(0).getSubject();

						matches.put(var, match);
					} else {
						unmatched++;
					}
				}

				if (unmatched > 0) totalUnmatched.addAndGet(unmatched);
			}, progressReceiver);

			Matcher.sanitizeMatches(matches);
		}

		for (Map.Entry<MethodVarInstance, MethodVarInstance> entry : matches.entrySet()) {
			if (state == JobState.CANCELING) {
				break;
			}

			matcher.match(entry.getKey(), entry.getValue());
		}

		Matcher.LOGGER.info("Auto matched {} method {}s ({} unmatched)", matches.size(), (args ? "arg" : "var"), totalUnmatched.get());

		return !matches.isEmpty();
	}

	private final Matcher matcher;
	private final ClassifierLevel level;
	private final boolean args;
}
