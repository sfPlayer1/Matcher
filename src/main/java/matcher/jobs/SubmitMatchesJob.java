package matcher.jobs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

import job4j.JobState;

import matcher.Matcher;
import matcher.config.Config;
import matcher.config.UidConfig;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.Matchable;
import matcher.type.MatchableKind;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class SubmitMatchesJob extends MatcherJob<Void> {
	public SubmitMatchesJob(Matcher matcher) {
		super(JobCategories.SUBMIT_MATCHES);

		this.matcher = matcher;
	}

	@Override
	protected Void execute(DoubleConsumer progressReceiver) {
		submitMatches(progressReceiver);
		return null;
	}

	private void submitMatches(DoubleConsumer progressReceiver) {
		UidConfig config = Config.getUidConfig();
		if (!config.isValid()) return;

		try {
			HttpURLConnection conn = (HttpURLConnection) new URL("https",
					config.getAddress().getHostString(),
					config.getAddress().getPort(),
					String.format("/%s/link/%s/%s", config.getProject(), config.getVersionA(), config.getVersionB())).openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("X-Token", config.getToken());
			conn.setDoOutput(true);

			List<Matchable<?>> requested = new ArrayList<>();

			try (DataOutputStream os = new DataOutputStream(conn.getOutputStream())) {
				for (ClassInstance cls : matcher.getEnv().getClassesA()) {
					if (state == JobState.CANCELING) {
						break;
					}

					if (!cls.hasMatch() || !cls.isInput()) continue; // TODO: skip with known + matched uids

					assert cls.getMatch() != cls;

					requested.add(cls);
					os.writeByte(MatchableKind.CLASS.ordinal());
					os.writeUTF(cls.getId());
					os.writeUTF(cls.getMatch().getId());

					for (MethodInstance method : cls.getMethods()) {
						if (!method.hasMatch() || !method.isReal()) continue;

						String srcMethodId = cls.getId()+"/"+method.getId();
						String dstMethodId = cls.getMatch().getId()+"/"+method.getMatch().getId();

						requested.add(method);
						os.writeByte(MatchableKind.METHOD.ordinal());
						os.writeUTF(srcMethodId);
						os.writeUTF(dstMethodId);

						for (MethodVarInstance arg : method.getArgs()) {
							if (!arg.hasMatch()) continue;

							requested.add(arg);
							os.writeByte(MatchableKind.METHOD_ARG.ordinal());
							os.writeUTF(srcMethodId+arg.getId());
							os.writeUTF(dstMethodId+arg.getMatch().getId());
						}
					}

					for (FieldInstance field : cls.getFields()) {
						if (!field.hasMatch() || !field.isReal()) continue;

						requested.add(field);
						os.writeByte(MatchableKind.FIELD.ordinal());
						os.writeUTF(cls.getId()+"/"+field.getId());
						os.writeUTF(cls.getMatch().getId()+"/"+field.getMatch().getId());
					}
				}
			}

			progressReceiver.accept(0.5);

			try (DataInputStream is = new DataInputStream(conn.getInputStream())) {
				for (Matchable<?> matchable : requested) {
					int uid = is.readInt();
				}
			}

			progressReceiver.accept(1);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private final Matcher matcher;
}
