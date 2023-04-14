package job4j;

public class JobSettings {
	static JobSettings copy(JobSettings original) {
		return new JobSettings() {{
				this.invisible = original.invisible;
				this.visualPassthrough = original.visualPassthrough;
				this.printStackTraceOnError = original.printStackTraceOnError;
				this.cancelPreviousJobsWithSameId = original.cancelPreviousJobsWithSameId;
				this.timeoutSeconds = original.timeoutSeconds;
			}};
	}

	protected boolean invisible;
	protected boolean visualPassthrough;
	protected boolean printStackTraceOnError = true;
	protected boolean cancelPreviousJobsWithSameId;
	protected long timeoutSeconds = Long.MAX_VALUE;

	/**
	 * Whether or not this job and its subjobs should be
	 * visible to the user. Has no effects on job execution.
	 */
	public boolean isInvisible() {
		return this.invisible;
	}

	/**
	 * Whether or not this job should be visible to the user.
	 * In contrast to {@link #isInvisible()}, the subjobs
	 * aren't made invisible too, but instead they appear as
	 * subjobs of this job's parent (or at the job root, if
	 * no parent is present).
	 */
	public boolean isVisualPassthrough() {
		return this.visualPassthrough;
	}

	public boolean isPrintStackTraceOnError() {
		return this.printStackTraceOnError;
	}

	/**
	 * Whether or not already running jobs with the
	 * same ID should get canceled when this job
	 * gets submitted.
	 */
	public boolean isCancelPreviousJobsWithSameId() {
		return this.cancelPreviousJobsWithSameId;
	}

	/**
	 * Gets the job's timeout (maximum allowed execution time
	 * before getting canceled). Defaults to {@link java.lang.Long#MAX_VALUE}.
	 */
	public long getTimeout() {
		return this.timeoutSeconds;
	}

	public static class MutableJobSettings extends JobSettings {
		private JobSettings immutable;

		void onSettingChange() {
			this.immutable = null;
		}

		public void makeInvisible() {
			this.invisible = true;
			onSettingChange();
		}

		public void enableVisualPassthrough() {
			this.visualPassthrough = true;
			onSettingChange();
		}

		public void dontPrintStacktraceOnError() {
			this.printStackTraceOnError = false;
			onSettingChange();
		}

		public void cancelPreviousJobsWithSameId() {
			this.cancelPreviousJobsWithSameId = true;
			onSettingChange();
		}

		public void setTimeout(long seconds) {
			this.timeoutSeconds = seconds;
			onSettingChange();
		}

		public JobSettings getImmutable() {
			if (immutable == null) {
				immutable = JobSettings.copy(this);
			}

			return immutable;
		}
	}
}
