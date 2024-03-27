package job4j;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import job4j.JobSettings.MutableJobSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import matcher.Util;

public abstract class Job<T> implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(Job.class);
	private final String id;
	private final JobCategory category;
	private final MutableJobSettings settings = new MutableJobSettings();
	private volatile T result;
	private volatile Throwable error;
	private volatile List<Job<?>> subJobs = Collections.synchronizedList(new ArrayList<>());
	private volatile Thread thread;
	protected volatile Job<?> parent;
	protected volatile double ownProgress = 0;
	protected volatile double overallProgress = 0;
	protected volatile boolean killed;
	protected volatile JobState state = JobState.CREATED;
	protected volatile List<Consumer<Job<?>>> subJobAddedListeners = Collections.synchronizedList(new ArrayList<>());
	protected volatile List<DoubleConsumer> progressListeners = Collections.synchronizedList(new ArrayList<>());
	protected volatile List<Runnable> cancelListeners = Collections.synchronizedList(new ArrayList<>());
	protected volatile List<BiConsumer<Optional<T>, Optional<Throwable>>> completionListeners = Collections.synchronizedList(new ArrayList<>());
	protected volatile List<JobCategory> blockingJobCategories = Collections.synchronizedList(new ArrayList<>());

	public Job(JobCategory category) {
		this(category, null);
	}

	public Job(JobCategory category, String idAppendix) {
		this.category = category;
		this.id = category.getId() + (idAppendix == null ? "" : ":" + idAppendix);

		changeDefaultSettings(settings);
	}


	//======================================================================================
	// Overridable methods
	//======================================================================================

	/**
	 * Override this method to modify the job's default settings.
	 * Make changes directly on the passed {@link #settings} object.
	 */
	protected void changeDefaultSettings(MutableJobSettings settings) {}

	/**
	 * Override this method to register any subjobs known ahead of time.
	 * Compared to the dynamic {@link #addSubJob} this improves the UX
	 * by letting the users know which tasks are going to be ran ahead of time
	 * and giving more accurate progress reports.
	 */
	protected void registerSubJobs() {};

	/**
	 * The main task this job shall execute. Progress is reported on a
	 * scale from -INF to +1. If this job is only used as an empty shell
	 * for hosting subjobs, the progressReceiver doesn't have to be invoked,
	 * then this job's overall progress is automatically calculated
	 * from the individual subjobs' progresses.
	 */
	protected abstract T execute(DoubleConsumer progressReceiver);


	//======================================================================================
	// Listener registration
	//======================================================================================

	/**
	 * Every time a subjob is registered, the listener gets invoked with the
	 * newly added job instance.
	 */
	public void addSubJobAddedListener(Consumer<Job<?>> listener) {
		this.subJobAddedListeners.add(listener);
	}

	/**
	 * Every time this job's progress changes, the double consumer gets invoked.
	 * Progress is a value between -INF and 1, where negative values indicate an uncertain runtime.
	 */
	public void addProgressListener(DoubleConsumer listener) {
		this.progressListeners.add(listener);
	}

	/**
	 * Gets called on job cancellation. The job hasn't completed at this point in time yet,
	 * it can still run for an indefinite amount of time until it eventually does or does not
	 * react to the event.
	 */
	public void addCancelListener(Runnable listener) {
		this.cancelListeners.add(listener);
	}

	/**
	 * Gets called once the job is finished. No specific state is guaranteed,
	 * it has to be checked manually.
	 * Passes the job's computed result (may be missing or incomplete if canceled/errored early),
	 * and, if errored, the encountered exception. Errors' stacktraces are printed automatically,
	 * so it doesn't have to be done manually each time.
	 */
	public void addCompletionListener(BiConsumer<Optional<T>, Optional<Throwable>> listener) {
		this.completionListeners.add(listener);
	}


	//======================================================================================
	// User-definable configuration
	//======================================================================================

	/**
	 * Add IDs of other jobs which must be completed first.
	 */
	public void addBlockedBy(JobCategory... blockingJobCategories) {
		this.blockingJobCategories.addAll(Arrays.asList(blockingJobCategories));
	}


	//======================================================================================
	// Hierarchy modification
	//======================================================================================

	/**
	 * Dynamically add subjobs. Please consider overriding {@link #registerSubJobs}
	 * to register any subjobs known ahead of time!
	 */
	public void addSubJob(Job<?> subJob, boolean cancelsParentWhenCanceledOrErrored) {
		if (hasParentJobInHierarchy(subJob)) {
			throw new IllegalArgumentException("Can't add a subjob which is already a parent job!");
		}

		subJob.setParent(this);
		subJob.addProgressListener(this::onSubJobProgressChange);
		this.subJobs.add(subJob);

		if (cancelsParentWhenCanceledOrErrored) {
			subJob.addCancelListener(() -> cancel());
			subJob.addCompletionListener((subJobResult, subJobError) -> {
				if (subJobError.isPresent()) {
					onError(subJobError.get());
				}
			});
		}

		List.copyOf(this.subJobAddedListeners).forEach((listener) -> listener.accept(subJob));
	}

	/**
	 * Parents are considered effectively final, so don't ever call this method
	 * while the job is already running. It is only exposed for situations
	 * where jobs indirectly start other jobs, so that the latter ones can
	 * be turned into direct children of the caller jobs.
	 */
	private void setParent(Job<?> parent) {
		if (containsSubJob(parent, true)) {
			throw new IllegalArgumentException("Can't set an already added subjob as parent job!");
		}

		if (this.state.compareTo(JobState.RUNNING) >= 0) {
			throw new UnsupportedOperationException("Can't change job's parent after already having been started");
		}

		this.parent = parent;
	}


	//======================================================================================
	// Lifecycle
	//======================================================================================

	/**
	 * Queues the job for execution.
	 * If called on a subjob, executes it directly.
	 */
	public void run() {
		if (this.state.compareTo(JobState.QUEUED) > 0) {
			// Already running/finished
			return;
		}

		this.state = JobState.QUEUED;

		if (this.parent == null) {
			// This job is an orphan / top-level job.
			// It will be executed on its own thread,
			// managed by the JobManager.
			JobManager.get().queue(this);
		} else {
			// This is a subjob. Subjobs get executed
			// synchronously directly on the parent thread.
			runNow();
		}
	}

	/**
	 * Queues the job for execution, waits for it to get scheduled,
	 * executes the job and then returns the result and/or error.
	 * This is basically the synchronous version of registering a
	 * CompletionListener.
	 */
	public JobResult<T> runAndAwait() {
		if (this.state.compareTo(JobState.QUEUED) > 0) {
			// Already running/finished
			return new JobResult<>(null, null);
		}

		run();

		while (!this.state.isFinished()) {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				// ignored
			}
		}

		return new JobResult<T>(result, error);
	}

	void runNow() {
		if (this.state.compareTo(JobState.QUEUED) > 0) {
			// Already running/finished
			return;
		}

		thread = Thread.currentThread();
		this.state = JobState.RUNNING;
		registerSubJobs();

		try {
			this.result = execute(this::onOwnProgressChange);
		} catch (Throwable e) {
			onError(e);
		}

		switch (this.state) {
			case RUNNING:
				onSuccess();
				break;
			case CANCELING:
				onCanceled();
				break;
			case ERRORED:
				break;
			default:
				throw new IllegalStateException("Job finished running but isn't in a valid state!");
		}
	}

	private void onOwnProgressChange(double progress) {
		validateProgress(progress);

		if (progress < 1f - Util.floatError && Math.abs(progress - ownProgress) < 0.005) {
			// Avoid time consuming computations for
			// unnoticeable progress deltas
			return;
		}

		this.ownProgress = progress;
		onProgressChange();
	}

	private void onSubJobProgressChange(double progress) {
		validateProgress(progress);
		onProgressChange();
	}

	protected void validateProgress(double progress) {
		if (progress > 1f + Util.floatError) {
			throw new IllegalArgumentException("Progress has to be a value between -INF and 1!");
		}
	}

	protected void onProgressChange() {
		double progress = 0;
		List<Double> progresses;

		if (ownProgress < 0 - Util.floatError || ownProgress > 0 + Util.floatError) {
			// Own progress has been set. This overrides the automatic
			// progress calculation dependent on subjob progress.
			progresses = List.of(ownProgress);
		} else {
			// Don't use own progress if it's never been set.
			// This happens if the current job is only used as an
			// empty shell for hosting subjobs.
			progresses = new ArrayList<>(subJobs.size());

			for (Job<?> job : List.copyOf(this.subJobs)) {
				progresses.add(job.getProgress());
			}
		}

		for (double value : progresses) {
			if (value < 0) {
				progress = -1;
				break;
			} else {
				if (value > 1f + Util.floatError) {
					throw new IllegalArgumentException("Progress has to be a value between -INF and 1!");
				}

				progress += value / progresses.size();
			}
		}

		this.overallProgress = Math.min(1.0, progress);
		List.copyOf(this.progressListeners).forEach(listener -> listener.accept(this.overallProgress));
	}

	public boolean cancel() {
		if (this.state != JobState.CANCELING && !this.state.isFinished()) {
			onCancel();
			return true;
		}

		return false;
	}

	protected void onCancel() {
		JobState previousState = this.state;
		this.state = JobState.CANCELING;

		List.copyOf(this.cancelListeners).forEach(listener -> listener.run());
		List.copyOf(this.subJobs).forEach(job -> job.cancel());

		if (previousState.compareTo(JobState.RUNNING) < 0) {
			onCanceled();
		}
	}

	protected void onCanceled() {
		this.state = JobState.CANCELED;
		onFinish();
	}

	void killRecursive(Throwable error) {
		if (this.state.isFinished() || this.killed || this.thread == null) {
			return;
		}

		this.thread.interrupt();
		this.killed = true;
		onError(error);
	}

	protected void onError(Throwable error) {
		this.state = JobState.ERRORED;
		this.error = error;

		if (this.settings.isPrintStackTraceOnError()
				&& !JobManager.get().isShuttingDown()
				&& !this.killed) {
			logger.error("An exception has been encountered in job '{}':\n{}",
					id, Util.getStacktrace(error));
		}

		List.copyOf(this.subJobs).forEach((subJob) -> subJob.cancel());

		onFinish();
	}

	protected void onSuccess() {
		this.state = JobState.SUCCEEDED;
		onFinish();
	}

	protected void onFinish() {
		onOwnProgressChange(1);

		List.copyOf(this.completionListeners).forEach(listener -> listener.accept(Optional.ofNullable(result), Optional.ofNullable(error)));
	}


	//======================================================================================
	// Getters & Checkers
	//======================================================================================

	Thread getThread() {
		return thread;
	}

	public String getId() {
		return this.id;
	}

	public JobCategory getCategory() {
		return category;
	}

	public Job<?> getParent() {
		return this.parent;
	}

	public double getProgress() {
		return this.overallProgress;
	}

	public JobState getState() {
		return this.state;
	}

	public JobSettings getSettings() {
		return settings.getImmutable();
	}

	/**
	 * {@return an unmodifiable list of subjobs}.
	 */
	public List<Job<?>> getSubJobs(boolean recursive) {
		if (!recursive) {
			return Collections.unmodifiableList(this.subJobs);
		}

		List<Job<?>> subjobs = List.copyOf(this.subJobs);
		List<Job<?>> subjobsRecursive = new ArrayList<>(subjobs);

		for (Job<?> subjob : subjobs) {
			subjobsRecursive.addAll(subjob.getSubJobs(true));
		}

		return Collections.unmodifiableList(subjobsRecursive);
	}

	public boolean hasSubJob(String id, boolean recursive) {
		List<Job<?>> subjobs = List.copyOf(this.subJobs);
		boolean hasSubJob = false;

		for (Job<?> subjob : subjobs) {
			if (subjob.getId().equals(id)) {
				hasSubJob = true;
				break;
			}
		}

		if (!recursive) return hasSubJob;

		for (Job<?> subjob : subjobs) {
			if (subjob.hasSubJob(id, recursive)) {
				hasSubJob = true;
				break;
			}
		}

		return hasSubJob;
	}

	/**
	 * Checks if this job or any of its subjobs are
	 * blocked by the passed job category.
	 */
	public boolean isBlockedBy(JobCategory category) {
		boolean blocked = this.blockingJobCategories.contains(category);

		if (blocked) return true;

		blocked = List.copyOf(this.blockingJobCategories).stream()
				.filter((blocking) -> category.hasParent(blocking))
				.findAny()
				.isPresent();

		if (blocked) return true;

		return List.copyOf(this.subJobs).stream()
				.filter(job -> job.isBlockedBy(category))
				.findAny()
				.isPresent();
	}

	public boolean containsSubJob(Job<?> subJob, boolean recursive) {
		boolean contains = this.subJobs.contains(subJob);

		if (contains || !recursive) return contains;

		return List.copyOf(this.subJobs).stream()
				.filter(nestedSubJob -> nestedSubJob.containsSubJob(subJob, true))
				.findAny()
				.isPresent();
	}

	public boolean hasParentJobInHierarchy(Job<?> job) {
		if (parent == null) return false;

		return job == parent || parent.hasParentJobInHierarchy(job);
	}


	//======================================================================================
	// Conversions
	//======================================================================================

	public interface JobFuture<V> extends Future<V> {
		Job<V> getUnderlyingJob();
	}

	public JobFuture<T> asFuture() {
		Job<T> job = this;

		return new JobFuture<T>() {
			@Override
			public Job<T> getUnderlyingJob() {
				return job;
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return job.cancel();
			}

			@Override
			public boolean isCancelled() {
				return job.getState() == JobState.CANCELED;
			}

			@Override
			public boolean isDone() {
				return job.getState().isFinished();
			}

			@Override
			public T get() throws InterruptedException, ExecutionException {
				job.runAndAwait();

				if (job.error == null) {
					return job.result;
				} else if (job.error instanceof InterruptedException) {
					throw (InterruptedException) error;
				} else if (job.error instanceof ExecutionException) {
					throw (ExecutionException) error;
				} else {
					throw new ExecutionException(error);
				}
			}

			@Override
			public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				job.settings.setTimeout(unit.toSeconds(timeout));
				job.runAndAwait();

				if (job.error == null) {
					return job.result;
				} else if (job.error instanceof InterruptedException) {
					throw (InterruptedException) error;
				} else if (job.error instanceof ExecutionException) {
					throw (ExecutionException) error;
				} else if (job.error instanceof TimeoutException) {
					throw (TimeoutException) error;
				} else {
					throw new ExecutionException(error);
				}
			}
		};
	}
}
