package matcher.model.type;

@SuppressWarnings("serial")
public final class InvalidSharedEnvQueryException extends RuntimeException {
	InvalidSharedEnvQueryException(ClassInstance a, ClassInstance b) {
		super("Querying shared env for "+(a != null ? a.getId() : b.getId())+" which is present in "+(a != null ? (b != null ? "a+b" : "a") : "b"));

		this.a = a;
		this.b = b;
	}

	public RuntimeException checkOrigin(ClassInstance cls) {
		if (cls.getEnv().isShared()) {
			return new InvalidSharedEnvException(cls, this);
		} else {
			return this;
		}
	};

	public final ClassInstance a;
	public final ClassInstance b;
}
