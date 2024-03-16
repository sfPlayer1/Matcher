package matcher.gui.ui;

import matcher.model.classifier.RankResult;
import matcher.model.type.ClassInstance;
import matcher.model.type.FieldInstance;
import matcher.model.type.MatchType;
import matcher.model.type.MemberInstance;
import matcher.model.type.MethodInstance;
import matcher.model.type.MethodVarInstance;

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
