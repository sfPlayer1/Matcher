package job4j;

import java.util.Optional;

public class JobResult<T> {
	T result;
	Throwable error;

	JobResult(T result, Throwable error) {
		this.result = result;
		this.error = error;
	}

	public Optional<T> getResult() {
		return Optional.ofNullable(result);
	}

	public Optional<Throwable> getError() {
		return Optional.ofNullable(error);
	}
}
