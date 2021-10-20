package matcher.type;

@SuppressWarnings("serial")
public final class InvalidSharedEnvException extends RuntimeException {
	InvalidSharedEnvException(ClassInstance cls, InvalidSharedEnvQueryException queryExc) {
		super("Shared env class "+cls.id+" ("+cls.getOrigin()+") requires a/b types: "+queryExc.getMessage());

		this.cls = cls;
		this.queryExc = queryExc;
	}

	public final ClassInstance cls;
	public final InvalidSharedEnvQueryException queryExc;
}