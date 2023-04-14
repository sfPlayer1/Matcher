package matcher.gui.jobs;

import job4j.JobCategory;

import matcher.jobs.JobCategories;

public class GuiJobCategories {
	public static final JobCategory OPEN_NEW_PROJECT = new JobCategory("open-new-project");

	static {
		JobCategories.INIT_MATCHER.addParent(OPEN_NEW_PROJECT);
	}
}
