package matcher.type;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public class InputFile {
	InputFile(Path path) {
		try {
			this.path = path;
			this.fileName = getSanitizedFileName(path);
			this.size = Files.size(path);
			this.hash = HashType.SHA256.hash(path);
			this.hashType = HashType.SHA256;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public InputFile(String fileName) {
		this(fileName, unknownSize, null, null);
	}

	public InputFile(String fileName, byte[] hash, HashType hashType) {
		this(fileName, unknownSize, hash, hashType);
	}

	public InputFile(String fileName, long size, byte[] hash, HashType hashType) {
		if (fileName == null) throw new IllegalArgumentException();

		this.path = null;
		this.fileName = fileName;
		this.size = size;
		this.hash = hash;
		this.hashType = hashType;
	}

	public boolean hasPath() {
		return path != null;
	}

	public String getFileName() {
		if (fileName != null) {
			return fileName;
		} else {
			return path.getFileName().toString();
		}
	}

	public boolean equals(Path path) {
		try {
			if (this.path != null) return Files.isSameFile(path, this.path);

			if (!getSanitizedFileName(path).equals(fileName)) return false;
			if (size != -1 && Files.size(path) != size) return false;

			return hash == null || Arrays.equals(hash, hashType.hash(path));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof InputFile)) return false;

		InputFile o = (InputFile) obj;

		try {
			return (path == null || o.path == null || Files.isSameFile(path, o.path))
					&& (fileName == null || o.fileName == null || fileName.equals(o.fileName))
					&& (size < 0 || o.size < 0 || size == o.size)
					&& (hash == null || o.hash == null || hashType == o.hashType && Arrays.equals(hash, o.hash));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public int hashCode() {
		return getFileName().hashCode();
	}

	@Override
	public String toString() {
		return getFileName();
	}

	private static String getSanitizedFileName(Path path) {
		return path.getFileName().toString().replace('\n', ' ');
	}

	public enum HashType {
		SHA1("SHA-1"),
		SHA256("SHA-256");

		HashType(String algorithm) {
			this.algorithm = algorithm;
		}

		public MessageDigest createDigest() {
			try {
				return MessageDigest.getInstance(algorithm);
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
		}

		public byte[] hash(Path path) throws IOException {
			TlData tlData = tlDatas.get();

			MessageDigest digest = tlData.digests.get(this);
			ByteBuffer buffer = tlData.buffer;
			buffer.clear();

			try (SeekableByteChannel channel = Files.newByteChannel(path)) {
				while (channel.read(buffer) != -1) {
					buffer.flip();
					digest.update(buffer);
					buffer.clear();
				}
			}

			return digest.digest();
		}

		private static class TlData {
			TlData() {
				for (HashType type : HashType.values()) {
					digests.put(type, type.createDigest());
				}

				buffer = ByteBuffer.allocate(256 * 1024);
			}

			final Map<HashType, MessageDigest> digests = new EnumMap<>(HashType.class);
			final ByteBuffer buffer;
		}

		private static final ThreadLocal<TlData> tlDatas = ThreadLocal.withInitial(TlData::new);

		public final String algorithm;
	}

	public static final long unknownSize = -1;

	public final Path path;
	public final String fileName;
	public final long size;
	public final byte[] hash;
	public final HashType hashType;
}