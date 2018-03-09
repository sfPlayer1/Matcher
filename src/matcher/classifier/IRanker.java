package matcher.classifier;

import java.util.List;

import matcher.type.ClassEnvironment;

public interface IRanker<T> {
	List<RankResult<T>> rank(T src, T[] dsts, ClassifierLevel level, ClassEnvironment env, double maxMismatch);
}