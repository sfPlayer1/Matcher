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

public class InputFile {
	InputFile(Path path) {
		try {
			this.path = path;
			this.fileName = getSanitizedFileName(path);
			this.size = Files.size(path);
			this.sha256 = hash(path);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public InputFile(String fileName) {
		this(fileName, unknownSize, null);
	}

	public InputFile(String fileName, long size, byte[] sha256) {
		if (fileName == null) throw new IllegalArgumentException();

		this.path = null;
		this.fileName = fileName;
		this.size = size;
		this.sha256 = sha256;
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

			return sha256 == null || Arrays.equals(sha256, hash(path));
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
					&& (sha256 == null || o.sha256 == null || Arrays.equals(sha256, o.sha256));
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

	private static byte[] hash(Path path) throws IOException {
		TlData tlData = InputFile.tlData.get();

		MessageDigest digest = tlData.digest;
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
			try {
				digest = MessageDigest.getInstance("SHA-256");
				buffer = ByteBuffer.allocate(256 * 1024);
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
		}

		final MessageDigest digest;
		final ByteBuffer buffer;
	}

	public static final long unknownSize = -1;

	private static final ThreadLocal<TlData> tlData = ThreadLocal.withInitial(TlData::new);

	public final Path path;
	public final String fileName;
	public final long size;
	public final byte[] sha256;
}