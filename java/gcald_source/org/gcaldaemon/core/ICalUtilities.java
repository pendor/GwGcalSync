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

import java.io.ByteArrayInputStream;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.UtcOffset;
import net.fortuna.ical4j.model.component.Observance;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Priority;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Trigger;
import net.fortuna.ical4j.model.property.TzOffsetTo;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;

import org.gcaldaemon.logger.QuickWriter;

/**
 * iCalendar (ICS) utilities.
 *
 * <li>iCal file parser
 * <li>iCal event comparator
 *
 * Created: Jan 03, 2007 12:50:56 PM
 *
 * @author Andras Berkes
 */
final class ICalUtilities {

	// --- CONSTANTS ---

	private static final long YEAR = 1000L * 60 * 60 * 24 * 365;
	private static final int MAX_REGISTRY_SIZE = 100;

	/**
	 * Enabled alarm minutes in Google Calendar
	 *
	 * <li>Minutes: 5, 10, 15, 20, 25, 30, 45
	 * <li>Hours: 1, 2, 3 (* 60 = 60, 120, 180 minutes)
	 * <li>Days: 1, 2, 7 (* 1440 = 1440, 2880, 10080 minutes)
	 */
	private static final int[] GOOGLE_ALARM_MINUTES = { 5, 10, 15, 20, 25, 30,
			45, 60, 120, 180, 1440, 2880, 10080 };

	// --- RECURRENCE RULE CACHE ---

	private static final HashMap recurrenceCache = new HashMap();

	// --- ALARM REGISTRY ---

	private static final HashMap alarmRegistry = new HashMap();
	private static boolean enableExtensions;

	// --- PRIVATE CONSTRUCTOR ---

	private ICalUtilities() {
	}

	// --- ICAL FILE PARSERS ---

	private static final CalendarBuilder builder = new CalendarBuilder();

	static final boolean setTimeZone(final String id, final String offset) throws Exception {

		// Get time zone registry
		final TimeZoneRegistry registry = builder.getRegistry();
		final TimeZone timeZone = registry.getTimeZone(id);
		if (timeZone == null) {
			return false;
		}
		final VTimeZone vTimeZone = timeZone.getVTimeZone();
		if (vTimeZone == null) {
			return false;
		}
		Component seasonalTime = vTimeZone.getObservances().getComponent(
				Observance.STANDARD);
		if (seasonalTime == null) {
			seasonalTime = vTimeZone.getObservances().getComponent(
					Observance.DAYLIGHT);
		}
		if (seasonalTime == null) {
			return false;
		}
		final TzOffsetTo offsetTo = (TzOffsetTo) seasonalTime
				.getProperty(Property.TZOFFSETTO);
		if (offsetTo == null) {
			return false;
		}

		// Set the new offset (eg: +040000)
		final UtcOffset utcOffset = new UtcOffset(offset);
		offsetTo.setOffset(utcOffset);
		registry.register(timeZone);
		return true;
	}

	protected static final Calendar parseCalendar(byte[] iCalBytes)
			throws Exception {
		try {
			synchronized (builder) {
				return builder.build(new ByteArrayInputStream(iCalBytes));
			}
		} catch (final ParserException parserException) {
			try {

				// Try to recover the invalid content
				final int lineNo = parserException.getLineNo();
				final QuickWriter writer = new QuickWriter(iCalBytes.length);
				String original = StringUtils.decodeToString(iCalBytes,
						StringUtils.US_ASCII);
				LineNumberReader reader = new LineNumberReader(
						new StringReader(original));
				String line;
				for (;;) {
					line = reader.readLine();

					// End of file
					if (line == null) {
						break;
					}

					// Try to skip the faulty line
					if (lineNo == reader.getLineNumber()) {
						continue;
					}

					// MS Outlook and KOrganizer bugfix
					if (line.trim().length() == 0) {
						continue;
					}

					// Skip related property (eg. Mac OSX & Ericsson W810i)
					if (line.startsWith("TRIGGER;RELATED")) {
						final int i = line.indexOf(':');
						if (i == -1) {
							writer.write("TRIGGER;VALUE=DURATION:-PT1H");
						} else {
							writer.write("TRIGGER;VALUE=DURATION");
							writer.write(line.substring(i));
						}
						writer.write("\r\n");
						continue;
					}

					// Append line
					writer.write(line);
					writer.write("\r\n");
				}
				reader = null;
				original = null;
				iCalBytes = writer.getBytes();
				synchronized (builder) {
					return builder.build(new ByteArrayInputStream(iCalBytes));
				}
			} catch (final Exception secondException) {

				// Unable to recover
				throw parserException;
			}
		}
	}

	static final VTimeZone[] getTimeZones(final Calendar calendar) throws Exception {
		final ComponentList zoneList = calendar.getComponents(Component.VTIMEZONE);
		final VTimeZone[] zones = new VTimeZone[zoneList.size()];
		zoneList.toArray(zones);
		return zones;
	}

	static final VEvent[] getEvents(final Calendar calendar) throws Exception {
		final ComponentList eventList = calendar.getComponents(Component.VEVENT);
		final VEvent[] events = new VEvent[eventList.size()];
		eventList.toArray(events);
		return events;
	}

	static final VToDo[] getToDos(final Calendar calendar) throws Exception {
		final ComponentList toDoList = calendar.getComponents(Component.VTODO);
		final VToDo[] toDos = new VToDo[toDoList.size()];
		toDoList.toArray(toDos);
		return toDos;
	}

	static final String getUid(final VEvent event) throws Exception {
		final Uid uid = event.getUid();
		if (uid == null) {
			return null;
		}
		String id = uid.getValue();
		if (id == null || id.length() == 0) {
			return null;
		}
		final RecurrenceId recurrenceId = event.getRecurrenceId();
		if (recurrenceId != null) {
			final Date date = recurrenceId.getDate();
			if (date != null) {
				id = id + '!' + date.getTime();
			}
		}
		return id;
	}

	// --- ICAL EVENT COMPARATOR ---

	private static final boolean isEquals(final VEvent oldEvent, final VEvent newEvent,
			final boolean findNewEvents, final String calendarURL) throws Exception {
		String oldValue, newValue;

		// Compare summary / title
		oldValue = null;
		newValue = null;
		final Summary sum1 = oldEvent.getSummary();
		final Summary sum2 = newEvent.getSummary();
		if (sum1 != null) {
			oldValue = sum1.getValue();
		}
		if (sum2 != null) {
			newValue = sum2.getValue();
		}
		if (!isEquals(oldValue, newValue)) {
			return false;
		}

		// Compare description / content
		oldValue = null;
		newValue = null;
		final Description des1 = oldEvent.getDescription();
		final Description des2 = newEvent.getDescription();
		if (des1 != null) {
			oldValue = des1.getValue();
		}
		if (des2 != null) {
			newValue = des2.getValue();
		}
		if (!isEquals(oldValue, newValue)) {
			return false;
		}

		// Compare start date
		oldValue = null;
		newValue = null;
		final DtStart sta1 = oldEvent.getStartDate();
		final DtStart sta2 = newEvent.getStartDate();
		if (sta1 != null) {
			oldValue = dateToString(sta1.getDate());
		}
		if (sta2 != null) {
			newValue = dateToString(sta2.getDate());
		}
		if (!isEquals(oldValue, newValue)) {
			return false;
		}

		// Compare end date
		final DtEnd end1 = oldEvent.getEndDate();
		final DtEnd end2 = newEvent.getEndDate();
		if (end1 != null) {
			oldValue = dateToString(end1.getDate());
		}
		if (end2 != null) {
			newValue = dateToString(end2.getDate());
		}
		if (!isEquals(oldValue, newValue)) {
			return false;
		}

		// Compare location
		oldValue = null;
		newValue = null;
		final Location loc1 = oldEvent.getLocation();
		final Location loc2 = newEvent.getLocation();
		if (loc1 != null) {
			oldValue = loc1.getValue();
		}
		if (loc2 != null) {
			newValue = loc2.getValue();
		}
		if (!isEquals(oldValue, newValue)) {
			return false;
		}

		// Compare recurrence rules
		oldValue = getRecurrenceDates(oldEvent);
		newValue = getRecurrenceDates(newEvent);
		if (!isEquals(oldValue, newValue)) {
			return false;
		}

		// Compare recurrence exceptions
		oldValue = getRecurrenceExceptions(oldEvent);
		newValue = getRecurrenceExceptions(newEvent);
		if (!isEquals(oldValue, newValue)) {
			return false;
		}

		// Compare other properties (new or updated events)
		if (findNewEvents) {

			// Compare attendees
			oldValue = listAttendees(oldEvent);
			newValue = listAttendees(newEvent);
			if (!isEquals(oldValue, newValue)) {
				return false;
			}

			// Compare status (tentative, confirmed, canceled)
			oldValue = null;
			newValue = null;
			final Status stat1 = oldEvent.getStatus();
			final Status stat2 = newEvent.getStatus();
			if (stat1 != null) {
				oldValue = stat1.getValue();
			}
			if (stat2 != null) {
				newValue = stat2.getValue();
			}
			if (newValue != null && !isEquals(oldValue, newValue)) {
				return false;
			}

			// Compare classification (public / private)
			oldValue = null;
			newValue = null;
			final Clazz cla1 = oldEvent.getClassification();
			final Clazz cla2 = newEvent.getClassification();
			if (cla1 != null) {
				oldValue = cla1.getValue();
			}
			if (cla2 != null) {
				newValue = cla2.getValue();
			}
			if (newValue != null && !isEquals(oldValue, newValue)) {
				return false;
			}

			// Compare transparency (transparent / opaque)
			oldValue = null;
			newValue = null;
			final Transp tra1 = oldEvent.getTransparency();
			final Transp tra2 = newEvent.getTransparency();
			if (tra1 != null) {
				oldValue = tra1.getValue();
			}
			if (tra2 != null) {
				newValue = tra2.getValue();
			}
			if (newValue != null && !isEquals(oldValue, newValue)) {
				return false;
			}

			// Compare alarms
			oldValue = getAlarm(oldEvent);
			newValue = getAlarm(newEvent);
			final String uid = getUid(oldEvent);
			if (uid != null) {
				final String key = calendarURL + '\t' + uid;
				if (oldValue == null && !enableExtensions) {

					// Get previous alarm from registry
					oldValue = (String) alarmRegistry.get(key);
				}
				if (newValue == null) {
					if (!enableExtensions) {
						alarmRegistry.remove(key);
					}
					if (oldValue != null) {

						// Zero values = clear reminders mark
						if (!enableExtensions) {
							newEvent.getAlarms().add(
									new VAlarm(new Dur(0, 0, 0, 0)));
						}
						return false;
					}
				} else {
					if (!isEquals(oldValue, newValue)) {

						// Store alarm
						if (!enableExtensions) {
							if (alarmRegistry.size() > MAX_REGISTRY_SIZE) {
								alarmRegistry.clear();
							}
							alarmRegistry.put(key, newValue);
						}
						return false;
					}
				}
			}

			if (enableExtensions) {

				// Compare categories
				oldValue = null;
				newValue = null;
				final Property cat1 = oldEvent.getProperty(Property.CATEGORIES);
				final Property cat2 = newEvent.getProperty(Property.CATEGORIES);
				if (cat1 != null) {
					oldValue = cat1.getValue();
					if (oldValue != null && oldValue.startsWith("http")) {
						oldValue = null;
					}
				}
				if (cat2 != null) {
					newValue = cat2.getValue();
					if (newValue != null && newValue.startsWith("http")) {
						newValue = null;
					}
				}
				if (!isEquals(oldValue, newValue)) {
					return false;
				}

				// Compare priority
				oldValue = null;
				newValue = null;
				final Priority pri1 = oldEvent.getPriority();
				final Priority pri2 = newEvent.getPriority();
				if (pri1 != null) {
					oldValue = pri1.getValue();
				}
				if (pri2 != null) {
					newValue = pri2.getValue();
				}
				if (!isEquals(oldValue, newValue)) {
					return false;
				}

				// Compare URL
				oldValue = null;
				newValue = null;
				final Url url1 = oldEvent.getUrl();
				final Url url2 = newEvent.getUrl();
				if (url1 != null) {
					oldValue = url1.getValue();
				}
				if (url2 != null) {
					newValue = url2.getValue();
				}
				if (!isEquals(oldValue, newValue)) {
					return false;
				}
			}
		}

		return true;
	}

	private static final String getRecurrenceExceptions(final VEvent event)
			throws Exception {

		// Get exception dates
		final Date[] dates = getExceptionDates(event);
		if (dates == null) {
			return null;
		}

		// Convert date array to string
		final long[] times = new long[dates.length];
		int i;
		for (i = 0; i < dates.length; i++) {
			times[i] = dates[i].getTime();
		}
		Arrays.sort(times);
		final QuickWriter writer = new QuickWriter(100);
		for (i = 0; i < times.length; i++) {
			writer.append(Long.toString(times[i]));
			writer.append('\t');
		}
		return writer.toString();
	}

	static final Date[] getExceptionDates(final VEvent event) throws Exception {
		final PropertyList exDateList = event.getProperties(Property.EXDATE);
		if (exDateList == null || exDateList.isEmpty()) {
			return null;
		}
		final ExDate[] exDates = new ExDate[exDateList.size()];
		exDateList.toArray(exDates);
		final LinkedList list = new LinkedList();
		DateList dateList;
		for(final ExDate exDate : exDates) {
			dateList = exDate.getDates();
			if (dateList != null) {
				list.addAll(dateList);
			}
		}
		if (list.isEmpty()) {
			return null;
		}
		final Date[] dates = new Date[list.size()];
		list.toArray(dates);
		return dates;
	}

	private static final String getRecurrenceDates(final VEvent event)
			throws Exception {
		final RRule rule = (RRule) event.getProperty(Property.RRULE);
		if (rule != null) {
			final String cacheKey = event.getStartDate().getValue() + '\t'
					+ rule.getValue();

			// Get from cache (HashMap synchronized via "Synchronizer" object)
			String testDates = (String) recurrenceCache.get(cacheKey);
			if (testDates != null) {
				return testDates;
			}
			final Recur recur = rule.getRecur();
			final DateTime startDate = new DateTime(event.getStartDate().getDate());
			long interval = YEAR * 2;
			if (Recur.YEARLY.equals(recur.getFrequency())) {
				interval *= 5;
			}
			final DateTime endDate = new DateTime(startDate.getTime() + (interval));
			final DateList list = recur.getDates(startDate, endDate, Value.DATE_TIME);
			final String[] dates = new String[list.size()];
			for (int i = 0; i < dates.length; i++) {
				dates[i] = dateToString((DateTime) list.get(i));
			}
			Arrays.sort(dates, String.CASE_INSENSITIVE_ORDER);
			final QuickWriter writer = new QuickWriter(300);
			for(final String date : dates) {
				writer.write(date);
				writer.write('\t');
			}
			if (recurrenceCache.size() > MAX_REGISTRY_SIZE) {
				recurrenceCache.clear();
			}
			testDates = writer.toString();
			recurrenceCache.put(cacheKey, testDates);
			return testDates;
		}
		return null;
	}

	private static final String dateToString(final Date date) throws Exception {
		if (date == null) {
			return "";
		}
		return Long.toString(date.getTime());
	}

	static final String[] getAttendees(final VEvent event) throws Exception {
		final PropertyList list = event.getProperties(Property.ATTENDEE);
		int count = list.size();
		if (count == 0) {
			return null;
		}
		final Attendee[] array = new Attendee[count];
		list.toArray(array);
		final LinkedList emails = new LinkedList();
		String value;
		int i;
		for (i = 0; i < count; i++) {
			value = array[i].getValue();
			if (value == null || value.indexOf('@') == -1) {
				continue;
			}
			if (value.toLowerCase().startsWith("mailto:")) {
				value = value.substring(7).trim();
			}
			emails.addLast(value);
		}
		count = emails.size();
		if (count == 0) {
			return null;
		}
		final String[] values = new String[count];
		emails.toArray(values);
		Arrays.sort(values, String.CASE_INSENSITIVE_ORDER);
		return values;
	}

	private static final String listAttendees(final VEvent event) throws Exception {
		final String[] emails = getAttendees(event);
		if (emails == null) {
			return null;
		}
		final QuickWriter writer = new QuickWriter(500);
		for(final String email : emails) {
			writer.write(email);
			writer.write('\t');
		}
		return writer.toString();
	}

	private static final String getAlarm(final VEvent event) throws Exception {
		final int alarmMinutes = getAlarmMinutes(event);
		if (alarmMinutes == -1) {
			return null;
		}
		return Integer.toString(alarmMinutes);
	}

	static final int getAlarmMinutes(final VEvent event) throws Exception {
		final ComponentList alarms = event.getAlarms();
		int n, mins = -1;
		if (alarms != null && !alarms.isEmpty()) {
			VAlarm alarm = null;
			final int max = alarms.size();
			Object property;
			for (n = 0; n < max; n++) {
				property = alarms.get(n);
				if (property != null && property instanceof VAlarm) {
					alarm = (VAlarm) property;
					break;
				}
			}
			if (alarm != null) {
				final Trigger trigger = alarm.getTrigger();
				if (trigger != null) {
					mins = 0;
					final Dur dur = trigger.getDuration();
					if (dur != null && dur.isNegative()) {
						n = dur.getSeconds();
						if (n > 0) {
							if (n < 60) {
								mins = 1;
							} else {
								mins = n / 60;
							}
						}
						n = dur.getMinutes();
						if (n > 0) {
							mins += n;
						}
						n = dur.getHours();
						if (n > 0) {
							mins += (n * 60);
						}
						n = dur.getDays();
						if (n > 0) {
							mins += (n * 1440);
						}
						n = dur.getWeeks();
						if (n > 0) {
							mins += (n * 10080);
						}
					}
				}
			}
		}
		if (mins > 0) {
			int dif, closestDif = Integer.MAX_VALUE;
			int closestMins = mins;
			for (n = 0; n < GOOGLE_ALARM_MINUTES.length; n++) {
				dif = Math.abs(GOOGLE_ALARM_MINUTES[n] - mins);
				if (dif == 0) {
					closestMins = mins;
					break;
				}
				if (dif < closestDif) {
					closestDif = dif;
					closestMins = GOOGLE_ALARM_MINUTES[n];
				}
			}
			mins = closestMins;
		}
		return mins;
	}

	// --- STRING COMPARER ---

	private static final boolean isEquals(String prop1, String prop2)
			throws Exception {
		if (prop1 != null && prop1.length() == 0) {
			prop1 = null;
		}
		if (prop2 != null && prop2.length() == 0) {
			prop2 = null;
		}
		if (prop1 != null && prop2 != null) {
			prop1 = normalizeLineBreaks(prop1);
			prop2 = normalizeLineBreaks(prop2);
			return prop1.equals(prop2);
		} else {
			if (prop1 == null && prop2 == null) {
				return true;
			}
		}
		return false;
	}

	static final String normalizeLineBreaks(String text) throws Exception {
		if (text == null || text.length() == 0) {
			return text;
		}
		text = text.trim();
		if (text.indexOf('\r') == -1 && text.indexOf('\n') == -1) {
			return text;
		}
		text = text.trim();
		if (text.indexOf('\r') == -1 && text.indexOf('\n') == -1) {
			return text;
		}
		final LineNumberReader reader = new LineNumberReader(new StringReader(text));
		final QuickWriter writer = new QuickWriter(text.length());
		String line;
		for (;;) {
			line = reader.readLine();
			if (line == null) {
				break;
			}
			if (line.length() != 0) {
				writer.write(line);
			}
			writer.write('\n');
		}
		return writer.toString();
	}

	// --- UTILS ---

	static final String getEventTitle(final VEvent event) throws Exception {
		String title = null;
		final Summary summary = event.getSummary();
		if (summary != null) {
			if (summary.getValue() != null) {
				title = summary.getValue();
				title = title.replace('\r', ' ').replace('\n', ' ');
			}
		}
		if (title == null || title.length() == 0) {
			title = "No Subject";
		} else {
			if (title.length() > 20) {
				title = title.substring(0, 20) + "...";
			}
		}
		return title;
	}

}
