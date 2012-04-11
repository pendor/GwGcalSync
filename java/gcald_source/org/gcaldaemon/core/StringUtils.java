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
package org.gcaldaemon.core;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

/**
 * Common String utilities (formatters, converters, etc).
 *
 * Created: Jan 03, 2007 12:50:56 PM
 *
 * @author Andras Berkes
 */
public final class StringUtils {

	public static final String US_ASCII = "US-ASCII";
	public static final String UTF_8 = "UTF-8";

	protected static final byte[] encodeString(final String string, final String encoding)
			throws CharacterCodingException {
		return encodeArray(string.toCharArray(), encoding);
	}

	static final byte[] encodeArray(final char[] chars, final String encoding)
			throws CharacterCodingException {
		if (encoding.equals(US_ASCII)) {
			final byte[] array = new byte[chars.length];
			for (int i = 0; i < array.length; i++) {
				array[i] = (byte) chars[i];
			}
			return array;
		}
		final ByteBuffer buffer = getEncoder(encoding).encode(CharBuffer.wrap(chars));
		final byte[] array = new byte[buffer.limit()];
		System.arraycopy(buffer.array(), 0, array, 0, array.length);
		return array;
	}

	private static final CharsetEncoder getEncoder(final String encoding) {
		return Charset.forName(encoding).newEncoder();
	}

	protected static final String decodeToString(final byte[] bytes, final String encoding)
			throws UnsupportedEncodingException {
		return new String(decodeToArray(bytes, encoding));
	}

	private static final char[] decodeToArray(final byte[] bytes, final String encoding)
			throws UnsupportedEncodingException {
		if (encoding.equals(US_ASCII)) {
			final char[] array = new char[bytes.length];
			for (int i = 0; i < array.length; i++) {
				array[i] = (char) bytes[i];
			}
			return array;
		}
		try {
			final CharBuffer buffer = getDecoder(encoding).decode(
					ByteBuffer.wrap(bytes));
			final char[] array = new char[buffer.limit()];
			System.arraycopy(buffer.array(), 0, array, 0, array.length);
			return array;
		} catch (final Exception nioException) {
			return (new String(bytes, encoding)).toCharArray();
		}
	}

	private static final CharsetDecoder getDecoder(final String encoding) {
		return Charset.forName(encoding).newDecoder();
	}

	public static final long stringToLong(final String string)
			throws NumberFormatException {
		final StringBuffer buffer = new StringBuffer(string.toLowerCase());
		final long unit = resolveUnit(buffer);
		long value = Long.parseLong(buffer.toString().trim());
		if (unit != 1) {
			value *= unit;
		}
		return value;
	}

	private static final long resolveUnit(final StringBuffer buffer) {
		long unit = 1;
		int i = -1;
		for (;;) {
			i = buffer.indexOf("msec", 0);
			if (i != -1) {
				break;
			}
			i = buffer.indexOf("mill", 0);
			if (i != -1) {
				break;
			}
			i = buffer.indexOf("sec", 0);
			if (i != -1) {
				unit = 1000L;
				break;
			}
			i = buffer.indexOf("min", 0);
			if (i != -1) {
				unit = 1000L * 60;
				break;
			}
			i = buffer.indexOf("hour", 0);
			if (i != -1) {
				unit = 1000L * 60 * 60;
				break;
			}
			i = buffer.indexOf("day", 0);
			if (i != -1) {
				unit = 1000L * 60 * 60 * 24;
				break;
			}
			i = buffer.indexOf("week", 0);
			if (i != -1) {
				unit = 1000L * 60 * 60 * 24 * 7;
				break;
			}
			i = buffer.indexOf("month", 0);
			if (i != -1) {
				unit = 1000L * 60 * 60 * 24 * 30;
				break;
			}
			i = buffer.indexOf("year", 0);
			if (i != -1) {
				unit = 1000L * 60 * 60 * 24 * 365;
				break;
			}
			i = buffer.indexOf("kbyte", 0);
			if (i != -1) {
				unit = 1024L;
				break;
			}
			i = buffer.indexOf("mbyte", 0);
			if (i != -1) {
				unit = 1024L * 1024;
				break;
			}
			i = buffer.indexOf("gbyte", 0);
			if (i != -1) {
				unit = 1024L * 1024 * 1024;
				break;
			}
			i = buffer.indexOf("tbyte", 0);
			if (i != -1) {
				unit = 1024L * 1024 * 1024 * 1024;
				break;
			}
			i = buffer.indexOf("byte", 0);
			break;
		}
		if (i != -1) {
			buffer.setLength(i);
		}
		return unit;
	}
}
