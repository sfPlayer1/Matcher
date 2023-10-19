package matcher.model.classifier;

import matcher.model.type.ClassEnvironment;

public interface IClassifier<T> {
	String getName();
	double getWeight();
	double getScore(T a, T b, ClassEnvironment env);
}
