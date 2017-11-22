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

	@Override
	public String toString() {
		return classifier.getName()+": "+score;
	}

	private final IClassifier<T> classifier;
	private final double score;
}