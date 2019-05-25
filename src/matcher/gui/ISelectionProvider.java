package matcher.gui;

import matcher.classifier.RankResult;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MatchType;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public interface ISelectionProvider {
	ClassInstance getSelectedClass();
	MemberInstance<?> getSelectedMember();
	MethodInstance getSelectedMethod();
	FieldInstance getSelectedField();
	MethodVarInstance getSelectedMethodVar();

	default RankResult<?> getSelectedRankResult(MatchType type) {
		return null;
	}
}
