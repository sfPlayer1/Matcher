package matcher.type;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class InputFile {
	InputFile(Path path) {
		try {
			this.path = path;
			this.size = Files.size(path);

			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			ByteBuffer buffer = ByteBuffer.allocate(256 * 1024);

			try (SeekableByteChannel channel = Files.newByteChannel(path)) {
				while (channel.read(buffer) != -1) {
					buffer.flip();
					digest.update(buffer);
					buffer.clear();
				}
			}

			sha256 = digest.digest();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public InputFile(String path, long size, byte[] sha256) {
		this.path = Paths.get(path);
		this.size = size;
		this.sha256 = sha256;
	}

	public final Path path;
	public final long size;
	public final byte[] sha256;
}