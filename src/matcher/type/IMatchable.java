package matcher.type;

public interface IMatchable<T> {
	T getMatch();
	boolean isNameObfuscated();
}