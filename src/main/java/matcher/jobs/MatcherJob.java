package matcher.jobs;

import job4j.Job;
import job4j.JobCategory;

public abstract class MatcherJob<T> extends Job<T> {
	public MatcherJob(JobCategory category) {
		super(category);
		init();
	}

	public MatcherJob(JobCategory category, String id) {
		super(category, id);
		init();
	}

	private void init() {
		addBlockedBy(JobCategories.INIT_MATCHER);
	}
}
