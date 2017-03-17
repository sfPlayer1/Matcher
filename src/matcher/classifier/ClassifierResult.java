package matcher.classifier;

public class ClassifierResult<T> {
	public ClassifierResult(IClassifier<T> classifier, double score) {
		this.classifier = classifier;
		this.score = score;
	}

	public IClassifier<T> getClassifier() {
		return classifier;
	}

	public double getScore() {
		return score;
	}

	private final IClassifier<T> classifier;
	private final double score;
}