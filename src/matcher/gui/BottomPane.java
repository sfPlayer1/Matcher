package matcher.gui;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import matcher.classifier.ClassifierLevel;
import matcher.classifier.FieldClassifier;
import matcher.classifier.MethodClassifier;
import matcher.classifier.RankResult;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MatchType;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;

public class BottomPane extends StackPane implements IGuiComponent {
	public BottomPane(Gui gui, MatchPaneSrc srcPane, MatchPaneDst dstPane) {
		super();

		this.gui = gui;
		this.srcPane = srcPane;
		this.dstPane = dstPane;

		init();
	}

	private void init() {
		setPadding(new Insets(GuiConstants.padding));

		HBox center = new HBox(GuiConstants.padding);
		getChildren().add(center);
		StackPane.setAlignment(center, Pos.CENTER);
		center.setAlignment(Pos.CENTER);

		matchClassButton.setText("match classes");
		matchClassButton.setOnAction(event -> matchClasses());
		matchClassButton.setDisable(true);

		center.getChildren().add(matchClassButton);

		matchMemberButton.setText("match members");
		matchMemberButton.setOnAction(event -> matchMembers());
		matchMemberButton.setDisable(true);

		center.getChildren().add(matchMemberButton);

		matchPerfectMembersButton.setText("match 100% members");
		matchPerfectMembersButton.setOnAction(event -> matchPerfectMembers());
		matchPerfectMembersButton.setDisable(true);

		center.getChildren().add(matchPerfectMembersButton);

		HBox right = new HBox(GuiConstants.padding);
		getChildren().add(right);
		StackPane.setAlignment(right, Pos.CENTER_RIGHT);
		right.setAlignment(Pos.CENTER_RIGHT);
		right.setPickOnBounds(false);

		unmatchClassButton.setText("unmatch classes");
		unmatchClassButton.setOnAction(event -> unmatchClass());
		unmatchClassButton.setDisable(true);

		right.getChildren().add(unmatchClassButton);

		unmatchMemberButton.setText("unmatch members");
		unmatchMemberButton.setOnAction(event -> unmatchMember());
		unmatchMemberButton.setDisable(true);

		right.getChildren().add(unmatchMemberButton);

		SelectListener selectListener = new SelectListener();
		srcPane.addListener(selectListener);
		dstPane.addListener(selectListener);
	}

	@Override
	public void onMatchChange(Set<MatchType> types) {
		if (types.contains(MatchType.Class)) {
			updateClassMatchButtons();
		}

		if (types.contains(MatchType.Class) || types.contains(MatchType.Method) || types.contains(MatchType.Field)) {
			updateMemberMatchButtons();
		}
	}

	private void updateClassMatchButtons() {
		ClassInstance clsA = srcPane.getSelectedClass();
		ClassInstance clsB = dstPane.getSelectedClass();

		matchClassButton.setDisable(!canMatchClasses(clsA, clsB));
		unmatchClassButton.setDisable(!canUnmatchClass(clsA));
	}

	private void updateMemberMatchButtons() {
		MemberInstance<?> memberA = srcPane.getSelectedMethod();
		if (memberA == null) memberA = srcPane.getSelectedField();

		MemberInstance<?> memberB = dstPane.getSelectedMethod();
		if (memberB == null) memberB = dstPane.getSelectedField();

		matchMemberButton.setDisable(!canMatchMembers(memberA, memberB));
		unmatchMemberButton.setDisable(!canUnmatchMember(memberA));

		matchPerfectMembersButton.setDisable(!canMatchPerfectMembers(srcPane.getSelectedClass()));
	}

	// match / unmatch actions implementation

	private boolean canMatchClasses(ClassInstance clsA, ClassInstance clsB) {
		return clsA != null && clsB != null && clsA.getMatch() != clsB;
	}

	private void matchClasses() {
		ClassInstance clsA = srcPane.getSelectedClass();
		ClassInstance clsB = dstPane.getSelectedClass();

		if (!canMatchClasses(clsA, clsB)) return;

		gui.getMatcher().match(clsA, clsB);
		gui.onMatchChange(EnumSet.allOf(MatchType.class));
	}

	private boolean canMatchMembers(MemberInstance<?> memberA, MemberInstance<?> memberB) {
		return memberA != null && memberB != null && memberA.getClass() == memberB.getClass() && memberA.getMatch() != memberB;
	}

	private void matchMembers() {
		MemberInstance<?> memberA = srcPane.getSelectedMethod();
		if (memberA == null) memberA = srcPane.getSelectedField();

		MemberInstance<?> memberB = dstPane.getSelectedMethod();
		if (memberB == null) memberB = dstPane.getSelectedField();

		if (!canMatchMembers(memberA, memberB)) return;

		if (memberA instanceof MethodInstance) {
			gui.getMatcher().match((MethodInstance) memberA, (MethodInstance) memberB);
			gui.onMatchChange(EnumSet.of(MatchType.Method));
		} else {
			gui.getMatcher().match((FieldInstance) memberA, (FieldInstance) memberB);
			gui.onMatchChange(EnumSet.of(MatchType.Field));
		}
	}

	private boolean canMatchPerfectMembers(ClassInstance cls) {
		return cls != null && cls.hasMatch() && hasUnmatchedMembers(cls) && hasUnmatchedMembers(cls.getMatch());
	}

	private static boolean hasUnmatchedMembers(ClassInstance cls) {
		for (MethodInstance m : cls.getMethods()) {
			if (!m.hasMatch()) return true;
		}

		for (FieldInstance m : cls.getFields()) {
			if (!m.hasMatch()) return true;
		}

		return false;
	}

	private void matchPerfectMembers() {
		ClassInstance clsA = srcPane.getSelectedClass();

		if (!canMatchPerfectMembers(clsA)) return;

		ClassInstance clsB = clsA.getMatch();

		final double minMethodScore = MethodClassifier.getMaxScore(ClassifierLevel.Full) - 1e-6;
		Map<MethodInstance, MethodInstance> matchedMethods = new IdentityHashMap<>();
		boolean matchedAnyMethods = false;

		for (MethodInstance m : clsA.getMethods()) {
			if (m.hasMatch()) continue;

			List<RankResult<MethodInstance>> results = MethodClassifier.rank(m, clsB.getMethods(), ClassifierLevel.Full, gui.getEnv());

			if (!results.isEmpty() && results.get(0).getScore() >= minMethodScore && (results.size() == 1 || results.get(1).getScore() < minMethodScore)) {
				MethodInstance match = results.get(0).getSubject();
				MethodInstance prev = matchedMethods.putIfAbsent(match, m);
				if (prev != null) matchedMethods.put(match, null);
			}
		}

		for (Map.Entry<MethodInstance, MethodInstance> entry : matchedMethods.entrySet()) {
			if (entry.getValue() == null) continue;

			gui.getMatcher().match(entry.getValue(), entry.getKey());
			matchedAnyMethods = true;
		}

		final double minFieldScore = FieldClassifier.getMaxScore(ClassifierLevel.Full) - 1e-6;
		Map<FieldInstance, FieldInstance> matchedFields = new IdentityHashMap<>();
		boolean matchedAnyFields = false;

		for (FieldInstance m : clsA.getFields()) {
			if (m.hasMatch()) continue;

			List<RankResult<FieldInstance>> results = FieldClassifier.rank(m, clsB.getFields(), ClassifierLevel.Full, gui.getEnv());

			if (!results.isEmpty() && results.get(0).getScore() >= minFieldScore && (results.size() == 1 || results.get(1).getScore() < minFieldScore)) {
				FieldInstance match = results.get(0).getSubject();
				FieldInstance prev = matchedFields.putIfAbsent(match, m);
				if (prev != null) matchedFields.put(match, null);
			}
		}

		for (Map.Entry<FieldInstance, FieldInstance> entry : matchedFields.entrySet()) {
			if (entry.getValue() == null) continue;

			gui.getMatcher().match(entry.getValue(), entry.getKey());
			matchedAnyFields = true;
		}

		if (!matchedAnyMethods && !matchedAnyFields) return;

		Set<MatchType> matchedTypes = EnumSet.noneOf(MatchType.class);

		if (matchedAnyMethods) matchedTypes.add(MatchType.Method);
		if (matchedAnyFields) matchedTypes.add(MatchType.Field);

		gui.onMatchChange(matchedTypes);
	}

	private boolean canUnmatchClass(ClassInstance cls) {
		return cls != null && cls.getMatch() != null;
	}

	private void unmatchClass() {
		ClassInstance cls = srcPane.getSelectedClass();

		if (!canUnmatchClass(cls)) return;

		gui.getMatcher().unmatch(cls);
		gui.onMatchChange(EnumSet.allOf(MatchType.class));
	}

	private boolean canUnmatchMember(MemberInstance<?> member) {
		return member != null && member.getMatch() != null;
	}

	private void unmatchMember() {
		MemberInstance<?> member = srcPane.getSelectedMethod();
		if (member == null) member = srcPane.getSelectedField();

		if (!canUnmatchMember(member)) return;

		gui.getMatcher().unmatch(member);

		if (member instanceof MethodInstance) {
			gui.onMatchChange(EnumSet.of(MatchType.Method));
		} else {
			gui.onMatchChange(EnumSet.of(MatchType.Field));
		}
	}

	private class SelectListener implements IGuiComponent {
		@Override
		public void onClassSelect(ClassInstance cls) {
			updateClassMatchButtons();
			updateMemberMatchButtons();
		}

		@Override
		public void onMethodSelect(MethodInstance method) {
			updateMemberMatchButtons();
		}

		@Override
		public void onFieldSelect(FieldInstance field) {
			updateMemberMatchButtons();
		}
	}

	private final Gui gui;
	private final MatchPaneSrc srcPane;
	private final MatchPaneDst dstPane;
	private final Button matchClassButton = new Button();
	private final Button matchMemberButton = new Button();
	private final Button matchPerfectMembersButton = new Button();
	private final Button unmatchClassButton = new Button();
	private final Button unmatchMemberButton = new Button();
}
