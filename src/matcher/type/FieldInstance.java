package matcher.type;

import java.util.Set;

import org.objectweb.asm.tree.FieldNode;

import matcher.Util;

public class FieldInstance extends MemberInstance<FieldInstance> {
	FieldInstance(ClassInstance cls, String origName, String desc, FieldNode asmNode, int position, ClassFeatureExtractor extractor) {
		super(cls, getId(origName, desc), origName, true, position);

		this.type = extractor.getCreateClassInstance(desc);
		this.asmNode = asmNode;

		type.fieldTypeRefs.add(this);
	}

	@Override
	public String getName() {
		return origName;
	}

	@Override
	public String getDesc() {
		return type.id;
	}

	@Override
	public boolean isReal() {
		return asmNode != null;
	}

	public FieldNode getAsmNode() {
		return asmNode;
	}

	public ClassInstance getType() {
		return type;
	}

	public Set<MethodInstance> getReadRefs() {
		return readRefs;
	}

	public Set<MethodInstance> getWriteRefs() {
		return writeRefs;
	}

	static String getId(String name, String desc) {
		return name+";;"+desc;
	}

	final FieldNode asmNode;
	final ClassInstance type;
	ClassInstance exactType;

	final Set<MethodInstance> readRefs = Util.newIdentityHashSet();
	final Set<MethodInstance> writeRefs = Util.newIdentityHashSet();
}