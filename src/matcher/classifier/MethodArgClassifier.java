package matcher.classifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import matcher.type.ClassEnvironment;
import matcher.type.MethodVarInstance;

public class MethodArgClassifier {
	public static void init() {
		addClassifier(type, 10);
		addClassifier(position, 3);
	}

	private static void addClassifier(AbstractClassifier classifier, double weight, ClassifierLevel... levels) {
		if (levels.length == 0) levels = ClassifierLevel.ALL;

		classifier.weight = weight;

		for (ClassifierLevel level : levels) {
			classifiers.computeIfAbsent(level, ignore -> new ArrayList<>()).add(classifier);
			maxScore.put(level, getMaxScore(level) + weight);
		}
	}

	public static double getMaxScore(ClassifierLevel level) {
		return maxScore.getOrDefault(level, 0.);
	}

	public static List<RankResult<MethodVarInstance>> rank(MethodVarInstance src, MethodVarInstance[] dsts, ClassifierLevel level, ClassEnvironment env, double maxMismatch) {
		return ClassifierUtil.rank(src, dsts, classifiers.getOrDefault(level, Collections.emptyList()), ClassifierUtil::checkPotentialEquality, env, maxMismatch);
	}

	private static final Map<ClassifierLevel, List<IClassifier<MethodVarInstance>>> classifiers = new EnumMap<>(ClassifierLevel.class);
	private static final Map<ClassifierLevel, Double> maxScore = new EnumMap<>(ClassifierLevel.class);

	private static AbstractClassifier type = new AbstractClassifier("type") {
		@Override
		public double getScore(MethodVarInstance argA, MethodVarInstance argB, ClassEnvironment env) {
			return ClassifierUtil.checkPotentialEquality(argA.getType(), argB.getType()) ? 1 : 0;
		}
	};

	private static AbstractClassifier position = new AbstractClassifier("position") {
		@Override
		public double getScore(MethodVarInstance methodA, MethodVarInstance methodB, ClassEnvironment env) {
			return ClassifierUtil.classifyPosition(methodA, methodB, MethodVarInstance::getIndex, (a, idx) -> a.getMethod().getArg(idx), a -> a.getMethod().getArgs());
		}
	};

	private static abstract class AbstractClassifier implements IClassifier<MethodVarInstance> {
		AbstractClassifier(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public double getWeight() {
			return weight;
		}

		private final String name;
		private double weight;
	}
}
