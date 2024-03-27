package matcher.jobs;

import java.util.List;
import java.util.function.DoubleConsumer;

import matcher.classifier.ClassClassifier;
import matcher.classifier.ClassifierLevel;
import matcher.classifier.FieldClassifier;
import matcher.classifier.MethodClassifier;
import matcher.classifier.MethodVarClassifier;
import matcher.classifier.RankResult;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.Matchable;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class RankMatchResultsJob extends MatcherJob<List<? extends RankResult<? extends Matchable<?>>>> {
	public RankMatchResultsJob(ClassEnvironment env, ClassifierLevel matchLevel, Matchable<?> selection, List<ClassInstance> cmpClasses) {
		super(JobCategories.RANK_MATCHES);

		this.env = env;
		this.selection = selection;
		this.matchLevel = matchLevel;
		this.cmpClasses = cmpClasses;
	}

	@Override
	protected List<? extends RankResult<? extends Matchable<?>>> execute(DoubleConsumer progressReceiver) {
		if (selection instanceof ClassInstance) { // unmatched class or no member/method var selected
			ClassInstance cls = (ClassInstance) selection;
			return ClassClassifier.rankParallel(cls, cmpClasses.toArray(new ClassInstance[0]), matchLevel, env, MAX_MISMATCH);
		} else if (selection instanceof MethodInstance) { // unmatched method or no method var selected
			MethodInstance method = (MethodInstance) selection;
			return MethodClassifier.rank(method, method.getCls().getMatch().getMethods(), matchLevel, env, MAX_MISMATCH);
		} else if (selection instanceof FieldInstance) { // field
			FieldInstance field = (FieldInstance) selection;
			return FieldClassifier.rank(field, field.getCls().getMatch().getFields(), matchLevel, env, MAX_MISMATCH);
		} else if (selection instanceof MethodVarInstance) { // method arg/var
			MethodVarInstance var = (MethodVarInstance) selection;
			MethodInstance cmpMethod = var.getMethod().getMatch();
			MethodVarInstance[] cmp = var.isArg() ? cmpMethod.getArgs() : cmpMethod.getVars();
			return MethodVarClassifier.rank(var, cmp, matchLevel, env, MAX_MISMATCH);
		} else {
			throw new IllegalStateException();
		}
	}

	public static final double MAX_MISMATCH = Double.POSITIVE_INFINITY;
	private final ClassEnvironment env;
	private final Matchable<?> selection;
	private final ClassifierLevel matchLevel;
	private final List<ClassInstance> cmpClasses;
}
