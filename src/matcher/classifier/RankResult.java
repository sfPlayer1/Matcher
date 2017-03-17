package matcher.classifier;

import java.util.List;

public class RankResult<T> {
	public RankResult(T subject, double score, List<ClassifierResult<T>> results) {
		this.subject = subject;
		this.score = score;
		this.results = results;
	}

	public T getSubject() {
		return subject;
	}

	public double getScore() {
		return score;
	}

	public List<ClassifierResult<T>> getResults() {
		return results;
	}

	private final T subject;
	private final double score;
	private final List<ClassifierResult<T>> results;
}