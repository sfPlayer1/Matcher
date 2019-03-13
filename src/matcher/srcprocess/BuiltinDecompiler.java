package matcher.srcprocess;

import java.util.function.Supplier;

public enum BuiltinDecompiler {
	CFR("CFR", Cfr::new),
	FERNFLOWER("Fernflower", Fernflower::new),
	PROCYON("Procyon", Procyon::new);

	private BuiltinDecompiler(String name, Supplier<? extends Decompiler> supplier) {
		this.name = name;
		this.supplier = supplier;
	}

	public Decompiler get() {
		Decompiler ret = instance.get();

		if (ret == null) {
			ret = supplier.get();
			instance.set(ret);
		}

		return ret;
	}

	public final String name;
	private final Supplier<? extends Decompiler> supplier;
	private final ThreadLocal<Decompiler> instance = new ThreadLocal<>();
}
