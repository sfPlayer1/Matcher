package matcher.mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MappingNsRenamer extends ForwardingMappingVisitor {
	public MappingNsRenamer(MappingVisitor next, Map<String, String> nameMap) {
		super(next);

		Objects.requireNonNull(nameMap, "null name map");

		this.nameMap = nameMap;
	}

	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) {
		String newSrcNamespace = nameMap.getOrDefault(srcNamespace, srcNamespace);
		List<String> newDstNamespaces = new ArrayList<>(dstNamespaces.size());

		for (String ns : dstNamespaces) {
			newDstNamespaces.add(nameMap.getOrDefault(ns, ns));
		}

		super.visitNamespaces(newSrcNamespace, newDstNamespaces);
	}

	private final Map<String, String> nameMap;
}
