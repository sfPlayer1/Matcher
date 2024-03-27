package job4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JobCategory {
	private final String id;
	private final List<JobCategory> parents = Collections.synchronizedList(new ArrayList<>());

	public JobCategory(String id) {
		this(id, new JobCategory[0]);
	}

	public JobCategory(String id, JobCategory... parents) {
		this.id = id;
		this.parents.addAll(Arrays.asList(parents));
	}

	public String getId() {
		return this.id;
	}

	public boolean hasParent(JobCategory category) {
		synchronized (this.parents) {
			return this.parents.stream()
					.filter((parent) -> parent.equals(category) || parent.hasParent(category))
					.findAny()
					.isPresent();
		}
	}

	public boolean hasParent(String categoryId) {
		synchronized (this.parents) {
			return this.parents.stream()
					.filter((parent) -> parent.getId().equals(categoryId) || parent.hasParent(categoryId))
					.findAny()
					.isPresent();
		}
	}

	public void addParent(JobCategory category) {
		this.parents.add(category);
	}

	/**
	 * {@return an unmodifiable view of the parent list}.
	 */
	public List<JobCategory> getParents() {
		return Collections.unmodifiableList(this.parents);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof JobCategory)) {
			return false;
		}

		JobCategory other = (JobCategory) obj;

		boolean sameId = other.getId().equals(this.id);
		boolean sameParents = other.getParents().equals(parents);

		return sameId && sameParents;
	}
}
