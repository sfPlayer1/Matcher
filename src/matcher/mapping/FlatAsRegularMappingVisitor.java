package matcher.mapping;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

final class FlatAsRegularMappingVisitor implements MappingVisitor {
	public FlatAsRegularMappingVisitor(FlatMappingVisitor out) {
		this.next = out;
	}

	@Override
	public Set<MappingFlag> getFlags() {
		return next.getFlags();
	}

	@Override
	public void reset() {
		next.reset();
	}

	@Override
	public boolean visitHeader() {
		return next.visitHeader();
	}

	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) {
		next.visitNamespaces(srcNamespace, dstNamespaces);

		int count = dstNamespaces.size();
		dstNames = new String[count];
		Set<MappingFlag> flags = next.getFlags();

		if (flags.contains(MappingFlag.NEEDS_UNIQUENESS)) {
			dstClassNames = new String[count];
			dstMemberNames = new String[count];
		} else {
			dstClassNames = dstMemberNames = null;
		}

		dstMemberDescs = flags.contains(MappingFlag.NEEDS_DST_FIELD_DESC) || flags.contains(MappingFlag.NEEDS_DST_METHOD_DESC) ? new String[count] : null;
	}

	@Override
	public void visitMetadata(String key, String value) {
		next.visitMetadata(key, value);
	}

	@Override
	public boolean visitContent() {
		return next.visitContent();
	}

	@Override
	public boolean visitClass(String srcName) {
		this.srcClsName = srcName;

		Arrays.fill(dstNames, null);
		if (dstClassNames != null) Arrays.fill(dstClassNames, null);

		return true;
	}

	@Override
	public boolean visitField(String srcName, String srcDesc) {
		this.srcMemberName = srcName;
		this.srcMemberDesc = srcDesc;

		Arrays.fill(dstNames, null);
		if (dstMemberNames != null) Arrays.fill(dstMemberNames, null);
		if (dstMemberDescs != null) Arrays.fill(dstMemberDescs, null);

		return true;
	}

	@Override
	public boolean visitMethod(String srcName, String srcDesc) {
		this.srcMemberName = srcName;
		this.srcMemberDesc = srcDesc;

		Arrays.fill(dstNames, null);
		if (dstMemberNames != null) Arrays.fill(dstMemberNames, null);
		if (dstMemberDescs != null) Arrays.fill(dstMemberDescs, null);

		return true;
	}

	@Override
	public boolean visitMethodArg(int argPosition, int lvIndex, String srcName) {
		this.srcMemberSubName = srcName;
		this.argIdx = argPosition;
		this.lvIndex = lvIndex;

		Arrays.fill(dstNames, null);

		return true;
	}

	@Override
	public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, String srcName) {
		this.srcMemberSubName = srcName;
		this.argIdx = lvtRowIndex;
		this.lvIndex = lvIndex;
		this.startOpIdx = startOpIdx;

		Arrays.fill(dstNames, null);

		return true;
	}

	@Override
	public boolean visitEnd() {
		return next.visitEnd();
	}

	@Override
	public void visitDstName(MappedElementKind targetKind, int namespace, String name) {
		dstNames[namespace] = name;
	}

	@Override
	public void visitDstDesc(MappedElementKind targetKind, int namespace, String desc) {
		if (dstMemberDescs != null) dstMemberDescs[namespace] = desc;
	}

	@Override
	public boolean visitElementContent(MappedElementKind targetKind) {
		boolean relay;

		switch (targetKind) {
		case CLASS:
			relay = next.visitClass(srcClsName, dstNames);
			if (relay && dstClassNames != null) System.arraycopy(dstNames, 0, dstClassNames, 0, dstNames.length);
			break;
		case FIELD:
			relay = next.visitField(srcClsName, srcMemberName, srcMemberDesc, dstClassNames, dstNames, dstMemberDescs);
			if (relay && dstMemberNames != null) System.arraycopy(dstNames, 0, dstMemberNames, 0, dstNames.length);
			break;
		case METHOD:
			relay = next.visitMethod(srcClsName, srcMemberName, srcMemberDesc, dstClassNames, dstNames, dstMemberDescs);
			if (relay && dstMemberNames != null) System.arraycopy(dstNames, 0, dstMemberNames, 0, dstNames.length);
			break;
		case METHOD_ARG:
			relay = next.visitMethodArg(srcClsName, srcMemberName, srcMemberDesc,
					argIdx, lvIndex, srcMemberSubName,
					dstClassNames, dstMemberNames, dstMemberDescs, dstNames);
			break;
		case METHOD_VAR:
			relay = next.visitMethodVar(srcClsName, srcMemberName, srcMemberDesc,
					argIdx, lvIndex, startOpIdx, srcMemberSubName,
					dstClassNames, dstMemberNames, dstMemberDescs, dstNames);
			break;
		default:
			throw new IllegalStateException();
		}

		return relay;
	}

	@Override
	public void visitComment(MappedElementKind targetKind, String comment) {
		switch (targetKind) {
		case CLASS:
			next.visitClassComment(srcClsName, dstClassNames, comment);
			break;
		case FIELD:
			next.visitFieldComment(srcClsName, srcMemberName, srcMemberDesc,
					dstClassNames, dstMemberNames, dstMemberDescs, comment);
			break;
		case METHOD:
			next.visitMethodComment(srcClsName, srcMemberName, srcMemberDesc,
					dstClassNames, dstMemberNames, dstMemberDescs, comment);
			break;
		case METHOD_ARG:
			next.visitMethodArgComment(srcClsName, srcMemberName, srcMemberDesc, argIdx, lvIndex, srcMemberSubName,
					dstClassNames, dstMemberNames, dstMemberDescs, dstNames, comment);
			break;
		case METHOD_VAR:
			next.visitMethodVarComment(srcClsName, srcMemberName, srcMemberDesc, argIdx, lvIndex, startOpIdx, srcMemberSubName,
					dstClassNames, dstMemberNames, dstMemberDescs, dstNames, comment);
			break;
		}
	}

	private final FlatMappingVisitor next;

	private String srcClsName;
	private String srcMemberName;
	private String srcMemberDesc;
	private String srcMemberSubName;
	private int argIdx, lvIndex, startOpIdx;
	private String[] dstNames;
	private String[] dstClassNames;
	private String[] dstMemberNames;
	private String[] dstMemberDescs;
}
