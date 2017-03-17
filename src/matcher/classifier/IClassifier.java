package matcher.classifier;

import matcher.Matcher;

public interface IClassifier<T> {
	String getName();
	double getWeight();
	double getScore(T a, T b, Matcher matcher);
}