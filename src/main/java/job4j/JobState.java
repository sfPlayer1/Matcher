package job4j;

public enum JobState {
	CREATED,
	QUEUED,
	RUNNING,
	CANCELING,
	CANCELED,
	ERRORED,
	SUCCEEDED;

	public boolean isFinished() {
		return this == CANCELED
				|| this == ERRORED
				|| this == SUCCEEDED;
	}
}
