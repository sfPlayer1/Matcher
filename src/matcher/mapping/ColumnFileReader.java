package matcher.mapping;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

final class ColumnFileReader implements Closeable {
	public ColumnFileReader(Reader reader, char columnSeparator) {
		this.reader = reader;
		this.columnSeparator = columnSeparator;
	}

	@Override
	public void close() throws IOException {
		reader.close();
	}

	/**
	 * Try to read the current column with specific expected content.
	 *
	 * <p>The reader will point to the next column or end of line if successful, otherwise remains unchanged.
	 *
	 * @param expect content to expect
	 * @return true if the column was read and had the expected content, false otherwise
	 * @throws IOException
	 */
	public boolean nextCol(String expect) throws IOException {
		if (eol) return false;

		int len = expect.length();
		if (!fillBuffer(len)) return false;

		for (int i = 0; i < len; i++) {
			if (buffer[bufferPos + i] != expect.charAt(i)) return false; // read failed, not all of expect available
		}

		char trailing = 0;

		if (fillBuffer(len + 1) // not eof
				&& (trailing = buffer[bufferPos + len]) != columnSeparator // not end of column
				&& trailing != '\n' // not end of line
				&& trailing != '\r') {
			return false; // read failed, column contains data beyond expect
		}

		// successful read

		bufferPos += expect.length();

		// seek to the start of the next column
		if (trailing == columnSeparator) {
			bufferPos++;
		} else {
			eol = true;
		}

		return true;
	}

	/**
	 * Read and consume a column without unescaping.
	 */
	public String nextCol() throws IOException {
		return nextCol(false);
	}

	/**
	 * Read and consume a column with unescaping.
	 */
	public String nextEscapedCol() throws IOException {
		return nextCol(true);
	}

	/**
	 * Read and consume a column and unescape it if requested.
	 */
	public String nextCol(boolean unescape) throws IOException {
		if (eol) return null;

		int start;
		int end = bufferPos;
		int firstEscaped = -1;

		readLoop: for (;;) {
			while (end < bufferLimit) {
				char c = buffer[end];

				if (c == columnSeparator || c == '\n' || c == '\r') { // end of the current column
					start = bufferPos;
					bufferPos = end;

					// seek to the start of the next column
					if (c == columnSeparator) {
						bufferPos++;
					} else {
						eol = true;
					}

					break readLoop;
				} else if (unescape && c == '\\' && firstEscaped < 0) {
					firstEscaped = bufferPos;
				}

				end++;
			}

			// buffer ran out, refill

			int oldStart = bufferPos;
			boolean filled = fillBuffer(end - bufferPos + 1);
			int posShift = bufferPos - oldStart; // fillBuffer may compact the data, shifting it to the buffer start
			assert posShift <= 0;
			end += posShift;
			if (firstEscaped >= 0) firstEscaped += posShift;

			if (!filled) {
				start = bufferPos;
				bufferPos = end;
				eol = true;
				break;
			}
		}

		int len = end - start;

		if (len == 0) {
			return "";
		} else if (firstEscaped >= 0) {
			return Tiny2Util.unescape(String.valueOf(buffer, start, len));
		} else {
			return String.valueOf(buffer, start, len);
		}
	}

	/**
	 * Read and consume a column and convert it to integer.
	 */
	public int nextIntCol() throws IOException {
		String str = nextCol(false);

		try {
			return str != null ? Integer.parseInt(str) : -1;
		} catch (NumberFormatException e) {
			throw new IOException("invalid number in line "+lineNumber+": "+str);
		}
	}


	public boolean nextLine(int indent) throws IOException {
		fillLopo: do {
			while (bufferPos < bufferLimit) {
				char c = buffer[bufferPos];

				if (c == '\n') {
					if (indent == 0) { // skip empty lines if indent is 0
						if (!fillBuffer(2)) break fillLopo;

						c = buffer[bufferPos + 1];

						if (c == '\n' || c == '\r') { // 2+ consecutive new lines, consume first nl and retry
							bufferPos++;
							lineNumber++;
							continue;
						}
					}

					if (!fillBuffer(indent + 1)) return false;

					for (int i = 1; i <= indent; i++) {
						if (buffer[bufferPos + i] != '\t') return false;
					}

					bufferPos += indent + 1;
					lineNumber++;
					eol = false;

					return true;
				}

				bufferPos++;
			}
		} while (fillBuffer(1));

		eol = eof = true;

		return false;
	}

	public boolean hasExtraIndents() throws IOException {
		return fillBuffer(1) && buffer[bufferPos] == '\t';
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public boolean isAtEof() {
		return eof;
	}

	public void mark() {
		if (bufferPos > 0) {
			int available = bufferLimit - bufferPos;
			System.arraycopy(buffer, bufferPos, buffer, 0, available);
			bufferPos = 0;
			bufferLimit = available;
		}

		mark = bufferPos;
	}

	public void reset() {
		if (mark < 0) throw new IllegalStateException("not marked");

		bufferPos = mark;
	}

	private boolean fillBuffer(int count) throws IOException {
		int available = bufferLimit - bufferPos;
		int req = count - available;
		if (req <= 0) return true;

		if (bufferPos + count > buffer.length) { // not enough remaining buffer space
			if (mark >= 0) { // marked for rewind -> grow
				buffer = Arrays.copyOf(buffer, Math.max(bufferPos + count, buffer.length * 2));
			} else if (count > buffer.length) { // too small for compacting to suffice -> grow and compact
				char[] newBuffer = new char[Math.max(count, buffer.length * 2)];
				System.arraycopy(buffer, bufferPos, newBuffer, 0, available);
				buffer = newBuffer;
			} else { // compact
				System.arraycopy(buffer, bufferPos, buffer, 0, available);
			}

			bufferPos = 0;
			bufferLimit = available;
		}

		int reqLimit = bufferLimit + req;

		do {
			int read = reader.read(buffer, bufferLimit, buffer.length - bufferLimit);
			if (read < 0) return false; // eof

			bufferLimit += read;
		} while (bufferLimit < reqLimit);

		return true;
	}

	private final Reader reader;
	private final char columnSeparator;
	private char[] buffer = new char[4096 * 4];
	private int bufferPos;
	private int bufferLimit;
	private int mark = -1;
	private int lineNumber = 1;
	private boolean eol; // tracks whether the last column has been read, otherwise ambiguous if the last col is empty
	private boolean eof;
}
