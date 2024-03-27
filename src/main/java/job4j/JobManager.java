package job4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import matcher.Util;

public class JobManager {
	private static final JobManager INSTANCE = new JobManager();
	private static final ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));

	static {
		threadPool.setKeepAliveTime(60L, TimeUnit.SECONDS);
		threadPool.allowCoreThreadTimeOut(true);
	}

	public static synchronized JobManager get() {
		return INSTANCE;
	}

	private volatile List<BiConsumer<Job<?>, JobManagerEvent>> eventListeners = Collections.synchronizedList(new ArrayList<>());
	private volatile List<Job<?>> queuedJobs = Collections.synchronizedList(new LinkedList<>());
	private volatile List<Job<?>> runningJobs = Collections.synchronizedList(new LinkedList<>());
	private volatile boolean shuttingDown;

	public void registerEventListener(BiConsumer<Job<?>, JobManagerEvent> listener) {
		this.eventListeners.add(listener);
	}

	private void notifyEventListeners(Job<?> job, JobManagerEvent event) {
		synchronized (this.eventListeners) {
			this.eventListeners.forEach(listener -> listener.accept(job, event));
		}
	}

	/**
	 * Queues the job for execution.
	 */
	void queue(Job<?> job) {
		boolean threadAlreadyExecutingJob = false;

		synchronized (this.runningJobs) {
			for (Job<?> runningJob : this.runningJobs) {
				if (runningJob.getThread() == Thread.currentThread()) {
					// An already running job indirectly started another job.
					// Neither one declared the correct hierarchy (they don't know each other),
					// nevertheless one job indirectly parents the other one.
					// Now we're declaring the correct hierarchy ourselves.
					threadAlreadyExecutingJob = true;
					runningJob.addSubJob(job, false);
				}
			}
		}

		if (threadAlreadyExecutingJob) {
			job.run();
			return;
		}

		if (job.getSettings().isCancelPreviousJobsWithSameId()) {
			synchronized (queuedJobs) {
				for (Job<?> queuedJob : this.queuedJobs) {
					if (queuedJob.getCategory() == job.getCategory()
							&& queuedJob.getId().equals(job.getId())) {
						queuedJob.cancel();
					}
				}
			}

			synchronized (runningJobs) {
				for (Job<?> runningJob : this.runningJobs) {
					if (runningJob.getCategory() == job.getCategory()
							&& runningJob.getId().equals(job.getId())) {
						runningJob.cancel();
					}
				}
			}
		}

		this.queuedJobs.add(job);

		job.addCompletionListener((result, error) -> onJobFinished(job));
		notifyEventListeners(job, JobManagerEvent.JOB_QUEUED);
		tryLaunchNext();
	}

	private void onJobFinished(Job<?> job) {
		notifyEventListeners(job, JobManagerEvent.JOB_FINISHED);
		this.runningJobs.remove(job);
		tryLaunchNext();
	}

	private synchronized void tryLaunchNext() {
		for (Job<?> queuedJob : this.queuedJobs) {
			boolean blocked = false;

			synchronized (this.runningJobs) {
				List<Job<?>> jobsToCheckAgainst = new ArrayList<>();

				for (Job<?> runningJob : this.runningJobs) {
					jobsToCheckAgainst.add(runningJob);

					for (Job<?> runningSubJob : runningJob.getSubJobs(true)) {
						jobsToCheckAgainst.add(runningSubJob);
					}
				}

				for (Job<?> jobToCheckAgainst : jobsToCheckAgainst) {
					if (queuedJob.isBlockedBy(jobToCheckAgainst.getCategory())) {
						blocked = true;
						break;
					}
				}
			}

			if (!blocked) {
				this.queuedJobs.remove(queuedJob);
				this.runningJobs.add(queuedJob);
				notifyEventListeners(queuedJob, JobManagerEvent.JOB_STARTED);

				Thread wrapper = new Thread(() -> {
					try {
						threadPool.submit(() -> queuedJob.runNow()).get(queuedJob.getSettings().getTimeout(), TimeUnit.SECONDS);
					} catch (Throwable e) {
						if (e instanceof TimeoutException) {
							queuedJob.cancel();
						} else if (!shuttingDown) {
							throw new RuntimeException(String.format("An exception has been encountered in job '%s':\n%s",
									queuedJob.getId(), Util.getStacktrace(e)));
						}
					}
				});
				wrapper.setName(queuedJob.getId() + " wrapper thread");
				wrapper.start();
			}
		}
	}

	/**
	 * {@return an unmodifiable view of the queued jobs list}.
	 */
	public List<Job<?>> getQueuedJobs() {
		return Collections.unmodifiableList(this.queuedJobs);
	}

	/**
	 * {@return an unmodifiable view of the running jobs list}.
	 */
	public List<Job<?>> getRunningJobs() {
		return Collections.unmodifiableList(this.runningJobs);
	}

	/**
	 * @param id the job in question's ID
	 * @param recursive whether or not all running jobs' subjobs should be checked too
	 */
	public boolean isJobRunning(String id, boolean recursive) {
		List<Job<?>> jobs = List.copyOf(this.runningJobs);

		boolean running = jobs.stream()
				.filter((job -> job.getId().equals(id)))
				.findAny()
				.isPresent();

		if (!recursive) return running;

		running = jobs.stream()
				.filter((job -> job.hasSubJob(id, recursive)))
				.findAny()
				.isPresent();

		return running;
	}

	public int getMaxJobExecutorThreads() {
		return threadPool.getMaximumPoolSize();
	}

	public void setMaxJobExecutorThreads(int maxThreads) {
		int oldSize = threadPool.getMaximumPoolSize();
		int newSize = maxThreads;

		if (newSize < oldSize) {
			JobManager.threadPool.setCorePoolSize(newSize);
			JobManager.threadPool.setMaximumPoolSize(newSize);
		} else if (newSize > oldSize) {
			JobManager.threadPool.setMaximumPoolSize(newSize);
			JobManager.threadPool.setCorePoolSize(newSize);
		}
	}

	public void shutdown() {
		if (shuttingDown) return;

		shuttingDown = true;
		this.queuedJobs.clear();

		synchronized (this.runningJobs) {
			this.runningJobs.forEach(job -> job.cancel());
		}

		threadPool.shutdownNow();
	}

	public boolean isShuttingDown() {
		return shuttingDown;
	}

	public enum JobManagerEvent {
		JOB_QUEUED,
		JOB_STARTED,
		JOB_FINISHED
	}
}
