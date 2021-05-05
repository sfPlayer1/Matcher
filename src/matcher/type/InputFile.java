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
			this.pathHint = path;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public InputFile(String fileName) {
		this(fileName, unknownSize, null, null);
	}

	public InputFile(String fileName, Path pathHint) {
		this(fileName, unknownSize, null, null, pathHint);
	}

	public InputFile(String fileName, byte[] hash, HashType hashType) {
		this(fileName, unknownSize, hash, hashType);
	}

	public InputFile(String fileName, byte[] hash, HashType hashType, Path pathHint) {
		this(fileName, unknownSize, hash, hashType, pathHint);
	}

	public InputFile(String fileName, long size, byte[] hash, HashType hashType) {
		this(fileName, size, hash, hashType, null);
	}

	public InputFile(String fileName, long size, byte[] hash, HashType hashType, Path pathHint) {
		this.path = null;
		this.fileName = fileName;
		this.size = size;
		this.hash = hash;
		this.hashType = hashType;
		this.pathHint = pathHint;
	}

	public boolean hasPath() {
		return path != null;
	}

	public boolean equals(Path path) {
		try {
			if (this.path != null) return Files.isSameFile(path, this.path);

			if (fileName != null && !getSanitizedFileName(path).equals(fileName)) return false;
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
		return toString().hashCode();
	}

	@Override
	public String toString() {
		if (fileName != null) {
			return fileName;
		} else if (pathHint != null) {
			return pathHint.getFileName().toString();
		} else if (hash != null && hash.length >= 8) {
			return Long.toUnsignedString((hash[0] & 0xffL) << 56 | (hash[1] & 0xffL) << 48 | (hash[2] & 0xffL) << 40 | (hash[3] & 0xffL) << 32
					| (hash[4] & 0xffL) << 24 | (hash[5] & 0xffL) << 16 | (hash[6] & 0xffL) << 8 | hash[7] & 0xffL, 16);
		} else {
			return "unknown";
		}
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
	public final Path pathHint;
}