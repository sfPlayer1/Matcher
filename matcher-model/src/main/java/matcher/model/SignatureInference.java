package matcher.model;

import java.util.HashSet;
import java.util.Set;

import matcher.model.type.ClassEnvironment;
import matcher.model.type.ClassInstance;
import matcher.model.type.FieldInstance;
import matcher.model.type.MethodInstance;
import matcher.model.type.Signature.ClassTypeSignature;
import matcher.model.type.Signature.FieldSignature;
import matcher.model.type.Signature.JavaTypeSignature;
import matcher.model.type.Signature.MethodSignature;
import matcher.model.type.Signature.ReferenceTypeSignature;
import matcher.model.type.Signature.SimpleClassTypeSignature;
import matcher.model.type.Signature.ThrowsSignature;
import matcher.model.type.Signature.TypeArgument;
import matcher.model.type.Signature.TypeParameter;

public final class SignatureInference<T> {
	public static void process(ClassEnvironment env) {
		Set<String> missingParams = new HashSet<>();
		Set<String> shadowedParams = new HashSet<>();

		for (ClassInstance cls : env.getClassesA()) {
			if (!cls.isInput() || cls.getSignature() != null) continue;

			// find type variables needed by own fields (can't declare their own type variables, so must be from the class)

			for (FieldInstance field : cls.getFields()) {
				FieldSignature sig = field.getSignature();
				if (sig == null) continue;

				processRefTypeSig(sig.getCls(), shadowedParams, missingParams);
			}

			// find type variables needed by own methods and not declared by them

			for (MethodInstance method : cls.getMethods()) {
				MethodSignature sig = method.getSignature();
				if (sig == null) continue;

				if (sig.getTypeParameters() != null) {
					for (TypeParameter typeParem : sig.getTypeParameters()) {
						shadowedParams.add(typeParem.getIdentifier());
					}
				}

				for (JavaTypeSignature arg : sig.getArgs()) {
					ReferenceTypeSignature argRefType = arg.getCls();
					if (argRefType != null) processRefTypeSig(argRefType, shadowedParams, missingParams);
				}

				if (sig.getResult() != null) {
					if (sig.getResult().getCls() != null) processRefTypeSig(sig.getResult().getCls(), shadowedParams, missingParams);
				}

				if (sig.getThrowsSignatures() != null) {
					for (ThrowsSignature throwsSig : sig.getThrowsSignatures()) {
						if (throwsSig.getCls() != null) {
							processClsTypeSig(throwsSig.getCls(), shadowedParams, missingParams);
						} else { // throwsSig.getVar() != null
							if (!shadowedParams.contains(throwsSig.getVar())) {
								missingParams.add(throwsSig.getVar());
							}
						}
					}
				}

				shadowedParams.clear();
			}

			missingParams.clear();
		}
	}

	private static void processRefTypeSig(ReferenceTypeSignature sig, Set<String> shadowedParams, Set<String> missingParams) {
		if (sig.getCls() != null) {
			processClsTypeSig(sig.getCls(), shadowedParams, missingParams);
		} else if (sig.getVar() != null) {
			if (!shadowedParams.contains(sig.getVar())) {
				missingParams.add(sig.getVar());
			}
		} else { // sig.getArrayElemCls() != null
			if (sig.getArrayElemCls().getCls() != null) {
				processRefTypeSig(sig.getArrayElemCls().getCls(), shadowedParams, missingParams);
			}
		}
	}

	private static void processClsTypeSig(ClassTypeSignature sig, Set<String> shadowedParams, Set<String> missingParams) {
		if (sig.getTypeArguments() != null) {
			for (TypeArgument typeArg : sig.getTypeArguments()) {
				if (typeArg.getCls() != null) {
					processRefTypeSig(typeArg.getCls(), shadowedParams, missingParams);
				}
			}
		}

		if (sig.getSuffixes() != null) {
			for (SimpleClassTypeSignature ts : sig.getSuffixes()) {
				if (ts.getTypeArguments() != null) {
					for (TypeArgument typeArg : ts.getTypeArguments()) {
						if (typeArg.getCls() != null) {
							processRefTypeSig(typeArg.getCls(), shadowedParams, missingParams);
						}
					}
				}
			}
		}
	}
}
