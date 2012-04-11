//
// GCALDaemon is an OS-independent Java program that offers two-way
// synchronization between Google Calendar and various iCalalendar (RFC 2445)
// compatible calendar applications (Sunbird, Rainlendar, iCal, Lightning, etc).
//
// Apache License
// Version 2.0, January 2004
// http://www.apache.org/licenses/
//
// Project home:
// http://gcaldaemon.sourceforge.net
//
package org.gcaldaemon.logger;

import java.io.Writer;

/**
 * Unsynchronized StringBuffer-like class for the faster string concatenation.
 *
 * Created: Jan 03, 2007 12:50:56 PM
 *
 * @author Andras Berkes
 */
public final class QuickWriter extends Writer {

	private char[] buffer;
	private int length;

	// --- CONSTRUCTORS ---

	public QuickWriter(final int initialSize) {
		buffer = new char[initialSize];
	}

	public QuickWriter() {
		buffer = new char[1024];
	}

	@Override
  public final void close() {
		flush();
	}

	@Override
  public final void flush() {
		length = 0;
		if (buffer.length > 20000) {
			buffer = new char[2048];
		}
	}

	/**
	 * Expands the buffer.
	 *
	 * @param newLength
	 */
	private final void expandBuffer(final int newLength) {
		int doubleLength = newLength * 2;
		if (doubleLength < 0) {
			doubleLength = Integer.MAX_VALUE;
		}
		final char copy[] = new char[doubleLength];
		System.arraycopy(buffer, 0, copy, 0, length);
		buffer = copy;
	}

	/**
	 * Returns the length of the buffer.
	 *
	 * @return int
	 */
	public final int length() {
		return length;
	}

	/**
	 * Sets the length of the buffer.
	 *
	 * @param newLength
	 *            new length
	 */
	public final void setLength(final int newLength) {
		if (length != newLength) {
			if (length > newLength) {
				length = newLength;
			} else {
				write(' ', newLength - length);
			}
		}
	}

	/**
	 * Returns the buffer's characters.
	 *
	 * @return char[]
	 */
	public final char[] getChars() {
		final char[] copy = new char[length];
		System.arraycopy(buffer, 0, copy, 0, length);
		return copy;
	}

	/**
	 * Returns the buffer' content as ASCII bytes.
	 *
	 * @return byte[]
	 */
	public final byte[] getBytes() {
		final byte[] bytes = new byte[length];
		for (int i = 0; i < length; i++) {
			bytes[i] = (byte) buffer[i];
		}
		return bytes;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
  public final String toString() {
		return new String(buffer, 0, length);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.io.Writer#write(char[], int, int)
	 */
	@Override
  public final void write(final char[] chars, final int off, final int len) {
		final int newLength = length + len;
		if (newLength > buffer.length) {
			expandBuffer(newLength);
		}
		System.arraycopy(chars, off, buffer, length, len);
		length = newLength;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.io.Writer#write(java.lang.String)
	 */
	@Override
  public final void write(final String str) {
		if (str != null) {
			final int len = str.length();
			if (len != 0) {
				final int newLength = length + len;
				if (newLength > buffer.length) {
					expandBuffer(newLength);
				}
				str.getChars(0, len, buffer, length);
				length = newLength;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.io.Writer#write(char[])
	 */
	@Override
  public final void write(final char[] chars) {
		final int newLength = length + chars.length;
		if (newLength > buffer.length) {
			expandBuffer(newLength);
		}
		System.arraycopy(chars, 0, buffer, length, chars.length);
		length = newLength;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.io.Writer#write(char[])
	 */
	public final void write(final QuickWriter writer) {
		final int newLength = length + writer.length;
		if (newLength > buffer.length) {
			expandBuffer(newLength);
		}
		System.arraycopy(writer.buffer, 0, buffer, length, writer.length);
		length = newLength;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.io.Writer#write(int)
	 */
	@Override
  public final void write(final int character) {
		final int newLength = length + 1;
		if (newLength > buffer.length) {
			expandBuffer(newLength);
		}
		buffer[length++] = (char) character;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.io.Writer#write(java.lang.String, int, int)
	 */
	@Override
  public final void write(final String str, final int off, final int len) {
		final int newLength = length + len;
		if (newLength > buffer.length) {
			expandBuffer(newLength);
		}
		str.getChars(off, off + len, buffer, length);
		length = newLength;
	}

	/**
	 * Writes characters.
	 *
	 * @param c
	 * @param repeats
	 */
	private final void write(final char c, final int repeats) {
		final int newLength = length + repeats;
		if (newLength > buffer.length) {
			expandBuffer(newLength);
		}
		for (int i = length; i < newLength; i++) {
			buffer[i] = c;
		}
		length = newLength;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Appendable#append(char)
	 */
	@Override
  public final Writer append(final char c) {
		final int newLength = length + 1;
		if (newLength > buffer.length) {
			expandBuffer(newLength);
		}
		buffer[length++] = c;
		return this;
	}

}