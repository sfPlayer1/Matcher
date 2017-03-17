package matcher.classifier;

import java.util.List;

import matcher.Matcher;

public interface IRanker<T> {
	List<RankResult<T>> rank(T src, T[] dsts, Matcher matcher);
}