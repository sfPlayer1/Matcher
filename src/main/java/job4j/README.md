# Job4j
Job4j is an asynchronous task system for Java. At its core it isn't that much different from other task/worker systems provided by Swing or JavaFX, but in contrast to those, Job4j isn't bound to any UI libraries which may or may not be available at runtime (headless deployment on servers, Graal native etc).


## Architecture
Job4j is a pretty small library. In fact, it only provides six classes; though it's important to know how they interact with each other.

At Job4j's core, there's the `JobManager` class. It is responsible for queueing and running jobs, managing the job executor thread pool, firing job start and job finish events and handling inter-job dependencies.

Now, the JobManager wouldn't be able to do anything without actual `Job`s. They house the actual task to execute, state management, progress handling etc. It is possible to register event listeners for most `JobState` changes there. You can also block execution of certain jobs when other jobs are running, which is done via `JobCategory`s. Its `JobSettings` are defined on job creation, and turned immutable thereafter. Once a job has finished running, it returns a `JobResult` object.


## Usage
`Job` is an abstract class, which can either be extended (for larger, complex tasks) or defined in-line with an anonymous class. Each job has to implement the `execute` method, which houses the main task. Here's an example:
```java
var job = new Job<String>() {
	@Override
	protected String execute(DoubleConsumer progressReceiver) {
		String result = doHeavyComputation();
		return result;
	}
}
```
Several things can be witnessed:
- `new Job<String>`: `String` is the expected return value of the Job. If you don't want to return anything, use `Void`.
- The return value of the `execute` method corresponds to the class passed above.
- `execute` provides a `DoubleConsumer`, which you can pass your current progress. It operates on a scale from 0 to 1, is 0 by default and automatically gets set to 1 when the task finished executing. So you don't have to set those two values manually.

However, the above example isn't complete yet. When creating a new job, you also need to pass a `JobCategory` instance to the constructor. JobCategories should be declared as `static final` objects in a separate class, so you can reference them from all the classes you're creating jobs in. This is important, since JobCategories are used for:
1. giving your job an ID and
2. managing inter-job dependencies / execution blockers.

So, a complete example would be:
```java
public class JobCategories {
	public static final JobCategory INIT_PROGRAM = new JobCategory("init-program");
	public static final JobCategory DO_HEAVY_COMPUTATION = new JobCategory("do-heavy-computation");
}

public class MyClass {
	public static void doSomething() {
		var job = new Job<String>(JobCategories.DO_HEAVY_COMPUTATION) {
			@Override
			protected String execute(DoubleConsumer progressReceiver) {
				String result = doHeavyComputation();
				return result;
			}
		}
		job.addBlockedBy(JobCategories.INIT_PROGRAM);
	}
}

```
If a job of the category `INIT_PROGRAM` is running when the above job gets queued, the latter has to wait until the former job is finished before it can run.

### Overriding default job settings
Changing a job's default settings can be done by overriding the `changeDefaultSettings` method:
```java
var job = new Job<Void>(category) {
	@Override
	protected void changeDefaultSettings(MutableJobSettings settings) {
		settings.dontPrintStacktraceOnError();
	}

	@Override
	protected Void execute(DoubleConsumer progressReceiver) {
		return null;
	}
}
```

### Adding subjobs
Each job can have an arbitrary amount of subjobs. These can be added at any time during the parent job's lifespan, however it's recommended to register all known subjobs ahead of time in the `registerSubJobs` method. It gets called right before the parent job starts executing, and ensures a great user experience by letting users know which jobs are going to run and how long they're gonna be waiting.
```java
var job = new Job<Void>(category) {
	@Override
	protected void registerSubJobs() {
		addSubJob(importantSubJob, true)
		addSubJob(unimportantSubJob, false)
	}

	@Override
	protected Void execute(DoubleConsumer progressReceiver) {
		importantSubJob.run();
		unimportantSubJob.run();
		return null;
	}
}
```
A few things to note:
- Subjobs are executed synchronously.
- Once added, subjobs cannot be removed, only canceled.
- Jobs are smart and automatically handle progress correctly. In the example above, when `importantSubJob`'s progress reaches 50%, the overall progress of `job` will be set to 25%.
- When you have subjobs, you usually don't want to pass the parent job's `progressReceiver` any values, as progress is automatically calculated from the subjobs' progress stats anyway. If you _do_ modify the parent jobs' progress, that mechanism gets disabled.
- The second argument passed to `addSubJob` defines whether or not the parent job should be canceled as well, should the passed subjob be canceled or have an error. This is useful for job groups where all other subjobs depend on the calculated result from the preceding subjob.

### Invisible jobs
If you're adding lots of small subjobs, consider using `JobSettings::makeInvisible`, which hides this job and all its subjobs from potential users' UIs, so they don't get flooded with dozens of UI-cluttering progress bars etc. This option doesn't do anything other than setting a flag, which can then be read by potential GUI implementations. It doesn't change any functionality and the parent's overall progress will still be influenced by them.<br>
There's a second, similar setting, called `enableVisualPassthrough`. It does basically the same thing, except that only the job you set this on becomes invisible, not however its subjobs. So if you have the following job constellation:
```
TopLevelJob
└── SubJob
	├── SubSubJob1
	└── SubSubJob2
```
and you set `enableVisualPassthrough` on `SubJob`, all of its subjobs will be moving up a level:
```
TopLevelJob
├── SubSubJob1
└── SubSubJob2
```
Keep in mind that this is purely visual and doesn't influence any behavior, just like `makeInvisible`. It's the responsibility of UI devs to implement correct behavior, Job4j only provides these flags.

### Running a job
Once you've created the job, you can simply run it via `job.run()`. If the job:
- is a top level job (has no parents), it submits itself to the JobManager queue.
- is a subjob, it gets executed right away (synchronously, on the parent job's thread).

If you want to execute a job synchronously, you can use the `job.runAndAwait()` method, which behaves more or less like `Future::get`. Jobs don't implement the `Future` interface directly for various reasons, though if you really need to use them as one, you can call `job.asFuture()`.

If a job, somewhere in the code it executes, indirectly starts another job (without the two knowing of each other), the JobManager will see that they're both on the same thread and therefore adds the newly queued job as a subjob to the already running job.


## Job lifecycle
When you create a job, any overridden JobSettings will be applied immediately. The job's ID is also going to be set right away, either taking the supplied `JobCategory`'s ID directly, or, if created via the overloaded constructor, the `JobCategory`'s ID plus a semicolon and the supplied ID-appendix.

Once you call `job.run()`, its state gets changed to `QUEUED`, and it will continue as described [above](#running-a-job). Once the JobManager decides it's time for the job to start, it will call the internal `Job::runNow` method. This is when the state gets updated to `RUNNING`, all subjobs are registered and the actual task gets executed. Note that subjobs don't get started automatically, they have to be ran individually from the parent job's `execute` method. A parent and its subjobs always run on the same thread.

Should the `execute` method throw a RuntimeException, the job's subroutines will automatically catch it and set the state to `ERRORED`. If not overridden in the job's settings, the exception gets logged, and, depending on the boolean passed to `Job::addSubJob` earlier, the parent job (if present) will also get canceled. Read [here](#adding-subjobs) for more information.<br>
If the job gets canceled, it will enter the `CANCELING` state, and remain there until the `execute` method finished. This is why you should regularly check the job's state inside of your `execute` method bodies, so you can stop early if the job is to be canceled. After that, the state gets updated to `CANCELED`.<br>
Lastly, when a job finishes execution without erroring or getting canceled, it enters the `SUCCEEDED` state.

Be it `ERRORED`, `CANCELED` or `SUCCEEDED` - all three states indicate that the job has finished running (can be quickly checked via `JobState::isFinished`). Once a job has reached this state, it cannot be restarted. It proceeds to notify all relevant event listeners, and passes them a `JobResult` - consisting of the calculated value (if not errored or canceled too early), and a potential error if an exception occurred. After that, the job is done and gets garbage collected if no references persist. Except when it is a subjob, then it stays alive until the topmost parent job finishes, too.


## Thread management
`JobManager` has its own static `ThreadPoolExecutor` instance which is used to execute top level jobs on. By default it uses the formula `Math.max(2, Runtime.getRuntime().availableProcessors() / 2)` to calculate the max amount of threads it will allocate, but this number can always be changed later via `JobManager::setMaxJobExecutorThreads`.

Here you can see how `Job::run` works internally:
<table>
	<tr>
		<th colspan="2">Caller thread</th>
		<th>Wrapper thread</th>
		<th colspan="6">Job thread</th>
	</tr>
	<tr>
		<th>Caller class</th>
		<th>JobManager</th>
		<th>JobManager anonymous class</th>
		<th colspan="2">Job instance</th>
		<th>Job execute method</th>
		<th colspan="2">Subjob instance</th>
		<th>Subjob execute method</th>
	</tr>
	<tr>
		<td>Create job</td>
		<td></td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
	</tr>
	<tr>
		<td>Configure job</td>
		<td></td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
	</tr>
	<tr>
		<td>Run job</td>
		<td></td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
	</tr>
	<tr>
		<td></td>
		<td>Check preconditions</td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
	</tr>
	<tr>
		<td></td>
		<td>Queue job</td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
	</tr>
	<tr>
		<td></td>
		<td>Create & start wrapper thread</td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
	</tr>
	<tr>
		<td></td>
		<td></td>
		<td>Start job</td>
		<td colspan="2"></td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
	</tr>
	<tr>
		<td></td>
		<td></td>
		<td rowspan="12">Cancel job if it reaches timeout</td>
		<td colspan="2">Register subjobs</td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
	</tr>
	<tr>
		<td></td>
		<td></td>
		<td rowspan="10">Execute task</td>
		<td></td>
		<td></td>
		<td colspan="2"></td>
	</tr>
	<tr>
		<td></td>
		<td></td>
		<td></td>
		<td rowspan="8">Run subjob</td>
		<td colspan="2"></td>
		<td></td>
	</tr>
	<tr>
		<td></td>
		<td></td>
		<td></td>
		<td rowspan="6">Execute task</td>
		<td></td>
	</tr>
	<tr>
		<td></td>
		<td></td>
		<td></td>
		<td></td>
		<td>Do something</td>
	</tr>
	<tr>
		<td></td>
		<td></td>
		<td></td>
		<td></td>
		<td rowspan="3">Update progress</td>
	</tr>
	<tr>
		<td></td>
		<td></td>
		<td></td>
		<td rowspan="2">Handle progress update & run event listeners</td>
	</tr>
	<tr>
		<td></td>
		<td></td>
		<td>Handle progress update & run event listeners</td>
	</tr>
	<tr>
		<td></td>
		<td></td>
		<td></td>
		<td></td>
		<td>Do something</td>
	</tr>
	<tr>
		<td></td>
		<td></td>
		<td></td>
		<td colspan="2">Finish & run event listeners</td>
		<td></td>
	</tr>
	<tr>
		<td></td>
		<td></td>
		<td></td>
		<td>Do something</td>
		<td colspan="2"></td>
		<td></td>
	</tr>
	<tr>
		<td></td>
		<td></td>
		<td colspan="2">Finish & run event listeners</td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
	</tr>
	<tr>
		<td></td>
		<td>Try to launch next job</td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
		<td colspan="2"></td>
		<td></td>
	</tr>
</table>

`Job::runAndAwait` works similarly, except that it blocks the caller thread with a while loop (and 200ms `Thread::sleep`s) until the job in question finished executing.


## Conventions & guidelines
- Put larger jobs into their own classes. Anonymous inner classes get messy really quickly.
- When using the job system in libraries, _always_ use the blocking `Job::runAndAwait`. Library consumers shouldn't have to deal with annoying async stuff. If they want to, they can wrap the method call into a job of their own, and the JobManager will proceed to add your library jobs as children of the library consumer's job. This way, you don't annoy them by default, but developers still have the benefit of seeing what subjobs are being executed when they decide to wrap everything into their own job.
- As already said earlier, try to register subjobs as early as possible, so users can see what's running and better predict how long they'll have to wait.
