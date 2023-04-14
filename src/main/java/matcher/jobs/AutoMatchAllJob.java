package matcher.jobs;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleConsumer;

import job4j.Job;
import job4j.JobState;
import job4j.JobSettings.MutableJobSettings;

import matcher.Matcher;
import matcher.classifier.ClassifierLevel;
import matcher.type.MatchType;

public class AutoMatchAllJob extends MatcherJob<Set<MatchType>> {
	public AutoMatchAllJob(Matcher matcher) {
		super(JobCategories.AUTOMATCH_ALL);

		this.matcher = matcher;
	}

	@Override
	protected Set<MatchType> execute(DoubleConsumer progressReceiver) {
		for (Job<?> job : getSubJobs(false)) {
			if (state == JobState.CANCELING) {
				break;
			}

			job.run();
		}

		matcher.getEnv().getCache().clear();

		Set<MatchType> matchedTypes = new HashSet<>();

		if (matchedAnyClasses) {
			matchedTypes.add(MatchType.Class);
		}

		if (matchedAnyMembers) {
			matchedTypes.add(MatchType.Method);
			matchedTypes.add(MatchType.Field);
		}

		if (matchedAnyLocals) {
			matchedTypes.add(MatchType.MethodVar);
		}

		return matchedTypes;
	}

	@Override
	protected void registerSubJobs() {
		Job<Boolean> job;

		// Automatch classes, pass 1
		job = new AutoMatchClassesJob(matcher, ClassifierLevel.Initial);
		job.addCompletionListener(this::onMatchedClasses);
		addSubJob(job, true);

		// Automatch classes, pass 2
		job = new AutoMatchClassesJob(matcher, ClassifierLevel.Initial) {
			@Override
			protected Boolean execute(DoubleConsumer progressReceiver) {
				if (!matchedAnyClasses) {
					// No matches were found in the last pass,
					// so we aren't going to find any either.
					return false;
				}

				return super.execute(progressReceiver);
			}
		};
		job.addCompletionListener(this::onMatchedClasses);
		addSubJob(job, false);

		// Automatch all: intermediate
		job = new MatcherJob<>(JobCategories.AUTOMATCH_ALL_INTERMEDIATE) {
			@Override
			protected Boolean execute(DoubleConsumer progressReceiver) {
				return autoMatchMembers(ClassifierLevel.Intermediate, this);
			}
		};
		addSubJob(job, false);

		// Automatch all: full
		job = new MatcherJob<>(JobCategories.AUTOMATCH_ALL_FULL) {
			@Override
			protected Boolean execute(DoubleConsumer progressReceiver) {
				return autoMatchMembers(ClassifierLevel.Full, this);
			};
		};
		addSubJob(job, false);

		// Automatch all: extra
		job = new MatcherJob<>(JobCategories.AUTOMATCH_ALL_EXTRA) {
			@Override
			protected Boolean execute(DoubleConsumer progressReceiver) {
				return autoMatchMembers(ClassifierLevel.Extra, this);
			};
		};
		addSubJob(job, false);

		// Automatch locals
		job = new MatcherJob<Boolean>(JobCategories.AUTOMATCH_ALL_LOCALS) {
			@Override
			protected Boolean execute(DoubleConsumer progressReceiver) {
				return autoMatchLocals(this);
			};
		};
		addSubJob(job, false);
	}

	private boolean autoMatchMembers(ClassifierLevel level, Job<Boolean> parentJob) {
		if (parentJob.getState() == JobState.CANCELING) {
			return false;
		}

		boolean matchedAny = false;
		boolean matchedAnyOverall = false;
		boolean matchedAnyClassesBefore = true;

		do {
			matchedAny = false;

			// Register method matching subjob
			var methodJob = new AutoMatchMethodsJob(matcher, level) {
				@Override
				protected void changeDefaultSettings(MutableJobSettings settings) {
					super.changeDefaultSettings(settings);
					settings.makeInvisible();
				}
			};
			methodJob.addCompletionListener(this::onMatchedMembers);
			parentJob.addSubJob(methodJob, false);

			// Register field matching subjob
			var fieldJob = new AutoMatchFieldsJob(matcher, level) {
				@Override
				protected void changeDefaultSettings(MutableJobSettings settings) {
					super.changeDefaultSettings(settings);
					settings.makeInvisible();
				}
			};
			fieldJob.addCompletionListener(this::onMatchedMembers);
			parentJob.addSubJob(fieldJob, false);

			// Register class matching subjob
			var classesJob = new AutoMatchClassesJob(matcher, level) {
				@Override
				protected void changeDefaultSettings(MutableJobSettings settings) {
					super.changeDefaultSettings(settings);
					settings.makeInvisible();
				}
			};
			classesJob.addCompletionListener(this::onMatchedClasses);
			parentJob.addSubJob(classesJob, false);

			// Run subjobs
			matchedAny |= methodJob.runAndAwait().getResult().orElse(false);
			matchedAnyOverall |= matchedAny;

			if (parentJob.getState() == JobState.CANCELING) {
				break;
			}

			matchedAny |= fieldJob.runAndAwait().getResult().orElse(false);
			matchedAnyOverall |= matchedAny;

			if (parentJob.getState() == JobState.CANCELING
					|| (!matchedAny && !matchedAnyClassesBefore)) {
				classesJob.cancel();
				break;
			}

			matchedAnyClassesBefore = classesJob.runAndAwait().getResult().orElse(false);
		} while (matchedAny && parentJob.getState() != JobState.CANCELING);

		return matchedAnyOverall;
	}

	private boolean autoMatchLocals(Job<Boolean> parentJob) {
		if (parentJob.getState() == JobState.CANCELING) {
			return false;
		}

		boolean matchedAnyOverall = false;
		boolean matchedAny;

		do {
			matchedAny = false;

			// Register arg matching subjob
			var argJob = new AutoMatchLocalsJob(matcher, ClassifierLevel.Full, true) {
				@Override
				protected void changeDefaultSettings(MutableJobSettings settings) {
					super.changeDefaultSettings(settings);
					settings.makeInvisible();
				}
			};
			argJob.addCompletionListener(this::onMatchedLocals);
			parentJob.addSubJob(argJob, false);

			// Register var matching subjob
			var varJob = new AutoMatchLocalsJob(matcher, ClassifierLevel.Full, false) {
				@Override
				protected void changeDefaultSettings(MutableJobSettings settings) {
					super.changeDefaultSettings(settings);
					settings.makeInvisible();
				}
			};
			varJob.addCompletionListener(this::onMatchedLocals);
			parentJob.addSubJob(varJob, false);

			// Run subjobs
			matchedAny |= argJob.runAndAwait().getResult().orElse(false);
			matchedAnyOverall |= matchedAny;

			if (parentJob.getState() == JobState.CANCELING) {
				break;
			}

			matchedAny |= varJob.runAndAwait().getResult().orElse(false);
			matchedAnyOverall |= matchedAny;
		} while (matchedAny && parentJob.getState() != JobState.CANCELING);

		return matchedAnyOverall;
	}

	private void onMatchedClasses(Optional<Boolean> matchedAny, Optional<Throwable> error) {
		matchedAnyClasses |= matchedAny.orElse(false);
	}

	private void onMatchedMembers(Optional<Boolean> matchedAny, Optional<Throwable> error) {
		matchedAnyMembers |= matchedAny.orElse(false);
	}

	private void onMatchedLocals(Optional<Boolean> matchedAny, Optional<Throwable> error) {
		matchedAnyLocals |= matchedAny.orElse(false);
	}

	private final Matcher matcher;
	private boolean matchedAnyClasses;
	private boolean matchedAnyMembers;
	private boolean matchedAnyLocals;
}
