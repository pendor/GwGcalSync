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

/**
 * iCalendar modification container.
 *
 * Created: Jan 03, 2007 12:50:56 PM
 *
 * @author Andras Berkes
 */
public final class CachedCalendar extends Request {

	// --- CONSTANTS ---

	private static final String END_OF_CALENDAR = "END:VCALENDAR";

	// --- VARIABLES ---

	/**
	 * Timestamp of last modification
	 */
	protected long lastModified;

	/**
	 * Previous iCalendar file
	 */
	protected byte[] previousBody;

	/**
	 * Calendar's VTODO block (optional)
	 */
	String toDoBlock;

	// --- VEVENT & VTODO CONCATENATOR ---

	public final byte[] toByteArray() throws Exception {
		if (toDoBlock == null) {
			return body;
		}
		final String calendar = StringUtils.decodeToString(body, StringUtils.UTF_8);
		final int pos = calendar.lastIndexOf(END_OF_CALENDAR);
		if (pos == -1) {
			return body;
		}
		final String text = calendar.substring(0, pos) + toDoBlock + END_OF_CALENDAR;
		return StringUtils.encodeString(text, StringUtils.UTF_8);
	}

}
