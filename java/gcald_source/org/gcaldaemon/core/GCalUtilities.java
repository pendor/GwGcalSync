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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.Observance;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Created;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Priority;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.TzId;
import net.fortuna.ical4j.model.property.TzOffsetTo;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.Version;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.logger.QuickWriter;

import com.google.gdata.client.GoogleService.InvalidCredentialsException;
import com.google.gdata.client.calendar.CalendarQuery;
import com.google.gdata.client.calendar.CalendarService;
import com.google.gdata.data.Content;
import com.google.gdata.data.DateTime;
import com.google.gdata.data.Link;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.TextConstruct;
import com.google.gdata.data.TextContent;
import com.google.gdata.data.calendar.CalendarEntry;
import com.google.gdata.data.calendar.CalendarEventEntry;
import com.google.gdata.data.calendar.CalendarEventFeed;
import com.google.gdata.data.calendar.CalendarFeed;
import com.google.gdata.data.calendar.EventWho;
import com.google.gdata.data.extensions.BaseEventEntry.EventStatus;
import com.google.gdata.data.extensions.BaseEventEntry.Transparency;
import com.google.gdata.data.extensions.BaseEventEntry.Visibility;
import com.google.gdata.data.extensions.ExtendedProperty;
import com.google.gdata.data.extensions.OriginalEvent;
import com.google.gdata.data.extensions.Recurrence;
import com.google.gdata.data.extensions.Reminder;
import com.google.gdata.data.extensions.When;
import com.google.gdata.data.extensions.Where;
import com.google.gdata.data.extensions.Who.AttendeeStatus;
import com.google.gdata.util.ServiceException;

/**
 * Google Calendar utilities.
 *
 * <li>loadCalendar
 * <li>updateEvents
 * <li>removeEvents
 *
 * Created: Jan 03, 2007 12:50:56 PM
 *
 * @author Andras Berkes
 */
public final class GCalUtilities {

	// --- CONSTANTS ---

	protected static final String ERROR_MARKER = "gcaldaemon-error";

	private static final long GOOGLE_CONNECTION_TIMEOUT = 1000L * 60 * 5;
	private static final long GOOGLE_RETRY_MILLIS = 1000L;

	private static final int MAX_POOLED_CONNECTIONS = 100;
	private static final int MAX_FEED_ENTRIES = 10000;

	private static final String GOOGLE_HTTPS_URL = "https://www.google.com";
	private static final String GOOGLE_HTTP_URL = "http://www.google.com";
	private static final String CALENDAR_FEED_POSTFIX = "/private/full";
	private static final String USER_AGENT = "Mozilla/5.0 (Windows; U;"
			+ " Windows NT 5.1; hu; rv:1.8.0.8) Gecko/20061025 Thunderbird/1.5.0.8";
	private static final String CALENDAR_FEED_PREFIX = GOOGLE_HTTPS_URL
			+ "/calendar/feeds/";
	private static final String METAFEED_URL = CALENDAR_FEED_PREFIX + "default";
	private static final String FEEDS_DEFAULT_PART = "/feeds/default/";
	private static final String CALENDAR_ICAL_PART = "/calendar/ical/";
	private static final String PRIVATE_BASIC_PART = "/private/basic.ics";

	private static final String UID_EXTENSION_NAME = "gcaldaemon-uid";
	private static final String CATEGORIES_EXTENSION_NAME = "gcaldaemon-categories";
	private static final String PRIORITY_EXTENSION_NAME = "gcaldaemon-priority";
	private static final String URL_EXTENSION_NAME = "gcaldaemon-url";

	private static final char[] CR_LF = "\r\n".toCharArray();
	private static final char[] ALARM_BEGIN = "\r\nBEGIN:VALARM\r\nTRIGGER;VALUE=DURATION:-P"
			.toCharArray();
	private static final char[] ALARM_END = "\r\nACTION:AUDIO\r\nEND:VALARM\r\n"
			.toCharArray();
	private static final char[] ALARM_MOZ_LASTACK = "\r\nX-MOZ-LASTACK:"
			.toCharArray();
	private static final char[] ALARM_RAIN_LASTACK = "X-RAINLENDAR-LASTALARMACK:"
			.toCharArray();

	private static final long LAST_ACK_TIMEOUT = 86400000L;

	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

	// --- LOGGER ---

	private static final Log log = LogFactory.getLog(GCalUtilities.class);

	// --- GLOBAL PROPERTIES ---

	private static boolean enableEmail;
	private static boolean enableSms;
	private static boolean enablePopup;

	// --- HTTP CONNECTION HANDLER ---

	private static MultiThreadedHttpConnectionManager connectionManager;
	private static HttpClient httpClient;


	// --- PRIVATE CONSTRUCTOR ---

	static void initHttpClient(final String p_proxyHost, final int p_proxyPort) {
	  connectionManager = new MultiThreadedHttpConnectionManager();
    httpClient = new HttpClient(connectionManager);

    if(p_proxyHost != null && p_proxyHost.trim().length() > 0) {
      final HostConfiguration conf = httpClient.getHostConfiguration();
      conf.setProxy(p_proxyHost, p_proxyPort);
    }
	}

	private GCalUtilities() {
	}

	// --- GOOGLE ICALENDAR LOADER ---

	static final byte[] loadCalendar(final Request request) throws Exception {
		GetMethod get = null;
		String icalURL;

		// Get auth token
		String token = null;
		if (request.url.indexOf("/private-") == -1 && request.username != null
				&& request.password != null) {
			final CalendarService service = new CalendarService(Configurator.VERSION
					.replace(' ', '-'));
			token = service.getAuthToken(request.username, request.password,
					null, null, CalendarService.CALENDAR_SERVICE,
					Configurator.VERSION);
		}

		// Load calendar
		for (int tries = 0;; tries++) {
			try {

				// Create ical URL
				if (tries < 2) {
					icalURL = GOOGLE_HTTPS_URL + request.url;
				} else {
					icalURL = GOOGLE_HTTP_URL + request.url;
				}
				final int i = icalURL.indexOf("basic.ics");
				if (i != -1) {
					icalURL = icalURL.substring(0, i + 9);
				}
				get = new GetMethod(icalURL);
				get.addRequestHeader("User-Agent", USER_AGENT);
				get.setFollowRedirects(true);
				if (token != null) {

					// Set AuthSub token
					get.addRequestHeader("Authorization", "GoogleLogin auth=\""
							+ token + '"');
				}

				// Load iCal file from Google
				log.debug("Loading calendar from " + icalURL + "...");
				final int status = httpClient.executeMethod(get);
				if (status == -1) {
					throw new Exception("Invalid HTTP response status (-1)!");
				}
				byte[] bytes = get.getResponseBody();

				// Validate content
				String content;
					content = StringUtils.decodeToString(bytes,
							StringUtils.UTF_8);
				if (content.indexOf("BEGIN:VCALENDAR") == -1) {
					log.warn("Received file from Google:\r\n" + content);
					throw new Exception("Invalid iCal file: " + icalURL);
				}

				// Register time zones
				registerTimeZones(content, bytes);

				// Insert extended properties
				bytes = insertExtensions(request, content, bytes);

				// Cleanup cache
				editURLMaps.remove(request.url);
				uidMaps.remove(request.url);
				log.debug("Calendar loaded successfully (" + bytes.length
						+ " bytes).");

				// Return ICS calendar file
				return bytes;
			} catch (final UnknownHostException networkDown) {
				log.debug("Network down!");
				return exceptionToCalendar(networkDown);
			} catch (final Exception loadError) {
				if (tries == 5) {
					log.error("Unable to load calendar!", loadError);
					return exceptionToCalendar(loadError);
				}
				log.debug("Connection refused, reconnecting...");
				Thread.sleep(GOOGLE_RETRY_MILLIS);
			} finally {
				if (get != null) {
					get.releaseConnection();
				}
			}
		}
	}

	private static final byte[] exceptionToCalendar(final Exception loadError)
			throws Exception {

		// Create new calendar
		final Calendar calendar = new Calendar();
		final PropertyList props = calendar.getProperties();
		props.add(new ProdId(ERROR_MARKER));
		props.add(Version.VERSION_2_0);
		props.add(CalScale.GREGORIAN);

		// Convert exception to event
		String title, content;
		if (loadError != null && loadError instanceof UnknownHostException) {
			title = "NETWORK DOWN";
			content = "Service temporarily unavailable!\r\n"
					+ "Please do not modify this calendar! "
					+ "Try clicking on the Reload or Refresh button. "
					+ "If this doesn't work, try again later.";
		} else {
			title = "UNAVAILABLE";
			content = "Service unavailable!\r\n"
					+ "Please do not modify this calendar!";
		}
		final long eventStart = System.currentTimeMillis();
		final long eventEnd = eventStart + 2700000L;
		final VEvent event = new VEvent(new net.fortuna.ical4j.model.DateTime(
				eventStart), new net.fortuna.ical4j.model.DateTime(eventEnd),
				title);

		// Generate UID by start millis
		final PropertyList args = event.getProperties();
		final Uid uid = new Uid(ERROR_MARKER);
		args.add(uid);

		// Create description
		if (loadError != null) {
			final String message = loadError.getMessage();
			if (message != null && message.length() != 0) {
				content = content + "\r\n[cause: " + message + ']';
			}
		}
		final Description desc = new Description(content);
		args.add(desc);

		// Add marker event to calendar
		final ComponentList events = calendar.getComponents();
		events.add(event);

		// Get calendar bytes
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
		final CalendarOutputter outputter = new CalendarOutputter();
		outputter.output(calendar, buffer);
		return buffer.toByteArray();
	}

	// --- ICAL CONVERTER ---

	private static final byte[] insertExtensions(final Request request,
			final String content, byte[] bytes) {
		try {

			// Create calendar's private feed URL
			final URL feedURL = getFeedURL(request);

			// Get service from pool
			final CalendarService service = getService(request);

			// Build edit map
			final CachedCalendar calendar = new CachedCalendar();
			calendar.url = request.url;
			calendar.username = request.username;
			calendar.password = request.password;
			calendar.previousBody = bytes;
			final HashMap extensions = createEditURLMap(service, calendar, feedURL);
			if (extensions == null || extensions.isEmpty()) {
				return bytes;
			}

			// Last ack
			final boolean containsValarm = content.indexOf("BEGIN:VALARM") != -1;
			final long lastAck = System.currentTimeMillis() - LAST_ACK_TIMEOUT;
			final net.fortuna.ical4j.model.DateTime now = new net.fortuna.ical4j.model.DateTime(
					lastAck);
			now.setUtc(true);
			final char[] ack = now.toString().toCharArray();

			// Insert extensions
			final StringTokenizer st = new StringTokenizer(content, "\r\n");
			final QuickWriter writer = new QuickWriter(bytes.length * 2);
			String extension, line, id = null;
			int days, hours, mins, i;
			Reminder reminder;
			Integer number;
			while (st.hasMoreTokens()) {
				line = st.nextToken();

				// Skip extended ical properties
				if (line.startsWith(Property.CATEGORIES)
						|| line.startsWith(Property.PRIORITY)
						|| line.startsWith(Property.URL)) {
					continue;
				}

				// Get event ID
				if (line.startsWith("UID")) {
					id = line.substring(4);
					writer.write(line);
					writer.write(CR_LF);
					continue;
				}

				// Get recurrence ID
				if (line.startsWith("RECURRENCE-ID") && id != null) {
					i = line.lastIndexOf(':');
					if (i != -1) {
						try {
							final RecurrenceId recurrenceId = new RecurrenceId(line
									.substring(i + 1));
							final Date date = recurrenceId.getDate();
							if (date != null) {
								id = id + '!' + date.getTime();
							}
						} catch (final Exception ignored) {
							log.warn(ignored);
						}
					}
					writer.write(line);
					writer.write(CR_LF);
					continue;
				}

				if (line.startsWith("END:VEVENT") && id != null) {

					// Insert reminder
					reminder = (Reminder) extensions.get(id + "\ta");
					if (reminder != null && !containsValarm) {
						writer.write(ALARM_RAIN_LASTACK);
						writer.write(ack);
						writer.write(ALARM_BEGIN);
						number = reminder.getMinutes();
						if (number != null) {
							mins = number.intValue();
							if (mins <= 45) {

								// Valid minutes: 5, 10, 15, 20, 25, 30, 45
								mins = mins / 5 * 5;
								if (mins == 35 || mins == 40) {
									mins = 45;
								} else {
									if (mins == 0) {
										mins = 5;
									}
								}

								// T1M -> Minutes
								writer.write('T');
								writer.write(Integer.toString(mins));
								writer.write('M');
							} else {

								// Valid hours: 1, 2, 3
								hours = mins / 60;
								if (hours == 0) {
									hours = 1;
								}
								if (hours <= 3) {

									// T1H -> Hours
									writer.write('T');
									writer.write(Integer.toString(hours));
									writer.write('H');
								} else {

									// Valid days: 1, 2, 7
									days = hours / 24;
									if (days == 0) {
										days = 1;
									}
									if ((days > 2 && days < 7) || days > 7) {
										days = 7;
									}

									// 1D -> Days
									writer.write(Integer.toString(days));
									writer.write('D');
								}
							}
						} else {
							writer.write("T1H");
						}
						writer.write(ALARM_MOZ_LASTACK);
						writer.write(ack);
						writer.write(ALARM_END);
					}

					// Insert categories
					extension = (String) extensions.get(id + "\tc");
					if (extension != null && extension.length() != 0) {
						writer.write(Property.CATEGORIES);
						writer.write(':');
						writer.write(extension);
						writer.write(CR_LF);
					}

					// Insert priority
					extension = (String) extensions.get(id + "\tp");
					if (extension != null && extension.length() != 0) {
						writer.write(Property.PRIORITY);
						writer.write(':');
						writer.write(extension);
						writer.write(CR_LF);
					}

					// Insert URL
					extension = (String) extensions.get(id + "\tu");
					if (extension != null && extension.length() != 0) {
						writer.write(Property.URL);
						writer.write(':');
						writer.write(extension);
						writer.write(CR_LF);
					}
					id = null;
				}
				writer.write(line);
				writer.write(CR_LF);
			}

			// Encode extended ics file
			bytes = StringUtils.encodeArray(writer.getChars(),
					StringUtils.UTF_8);
		} catch (final Exception ignored) {
			log.debug("Unable to insert extensions!", ignored);
		}
		return bytes;
	}

	// --- AUTOMATIC TIME ZONE MANAGEMENT ---

	private static final HashSet registeredTimeZones = new HashSet();

	private static final void registerTimeZones(final String content, final byte[] bytes) {
		try {
			final StringTokenizer st = new StringTokenizer(content, "\r\n");
			final HashSet timeZones = new HashSet();
			String line, timeZone;
			while (st.hasMoreTokens()) {
				line = st.nextToken();
				if (!line.startsWith("TZID:")) {
					continue;
				}
				timeZone = line.substring(5);
				if (timeZone.length() == 0) {
					continue;
				}
				if (registeredTimeZones.contains(timeZone)) {
					continue;
				}
				timeZones.add(timeZone);
			}
			if (timeZones.isEmpty()) {
				return;
			}
			final Calendar calendar = ICalUtilities.parseCalendar(bytes);
			final VTimeZone[] zones = ICalUtilities.getTimeZones(calendar);
			if (zones.length == 0) {
				return;
			}
			Component seasonalTime;
			TzOffsetTo offsetTo;
			String id, offset;
			VTimeZone zone;
			for(final VTimeZone zone2 : zones) {
				zone = zone2;
				seasonalTime = zone.getObservances().getComponent(
						Observance.STANDARD);
				if (seasonalTime == null) {
					seasonalTime = zone.getObservances().getComponent(
							Observance.DAYLIGHT);
				}
				id = zone.getTimeZoneId().getValue();
				if (registeredTimeZones.contains(id)) {
					continue;
				}
				if (seasonalTime == null) {
					continue;
				}
				offsetTo = (TzOffsetTo) seasonalTime
						.getProperty(Property.TZOFFSETTO);
				if (offsetTo == null) {
					continue;
				}
				registeredTimeZones.add(id);
				offset = offsetTo.getValue();
				log.debug("Set the offset of " + id + " to GMT" + offset + ".");
				if (!ICalUtilities.setTimeZone(id, offset)) {
					log.warn("Unknown time zone (" + id + ")!");
				}
			}
		} catch (final Exception ignored) {
			log.debug(ignored);
		}
	}

	private static final void insertEvent(CachedCalendar calendar,
			final VTimeZone[] timeZones, final VEvent event, boolean foundRRule,
			final CalendarService service, final URL feedURL) throws Exception {

		// Clear cache
		if (foundRRule && event.getRecurrenceId() != null) {
			foundRRule = false;
			editURLMaps.remove(calendar.url);
			uidMaps.remove(calendar.url);
			final CachedCalendar swap = new CachedCalendar();
			swap.lastModified = calendar.lastModified;
			swap.url = calendar.url;
			swap.username = calendar.username;
			swap.password = calendar.password;
			swap.filePath = calendar.filePath;
			swap.toDoBlock = calendar.toDoBlock;
			swap.body = calendar.body;
			swap.previousBody = calendar.body;
			calendar = swap;
		}

		// Convert event to Google entry
		final CalendarEventEntry newEntry = convertVEvent(calendar, timeZones, event);

		// Absolute time = clear reminders mark
		final List reminders = newEntry.getReminder();
		if (reminders != null && !reminders.isEmpty()) {
			final Reminder reminder = (Reminder) reminders.get(0);
			final DateTime absolute = reminder.getAbsoluteTime();
			if (absolute != null) {
				reminders.clear();
			}
		}

		// Insert new event
		if (log.isDebugEnabled()) {
			log.debug("Inserting event (" + ICalUtilities.getEventTitle(event)
					+ ") into Google Calendar...");
		}
		try {
			service.insert(feedURL, newEntry);
		} catch (final Exception exception) {

			// Get remote message
			final String msg = getMessageBody(exception);

			// Skip insert
			if (msg.indexOf("no instances") != -1
					|| msg.indexOf("read-only") != -1) {
				log.debug("Unable to insert event ("
						+ ICalUtilities.getEventTitle(event) + ")!\r\n" + msg);
				return;
			}

			// Remove reminders
			if (msg.indexOf("many reminder") != -1) {
				final List reminder = newEntry.getReminder();
				log.warn("Too many reminders!");
				if (reminder != null) {
					reminder.clear();
				}
			}

			// Resend request
			Thread.sleep(GOOGLE_RETRY_MILLIS);
			try {
				service.insert(feedURL, newEntry);
			} catch (final Exception error) {
				log.warn("Unable to insert event ("
						+ ICalUtilities.getEventTitle(event) + ")!\r\n" + msg);
			}
		}
	}

	private static final String getMessageBody(final Exception exception) {
		if (exception == null) {
			return "";
		}
		String body = null;
		if (exception instanceof ServiceException) {
			body = ((ServiceException) exception).getResponseBody();
		}
		if (body == null || body.length() == 0) {
			body = exception.toString();
		}
		return body;
	}

	private static final URL getFeedURL(final Request request) throws Exception {
		String target = request.url;
		int i = target.indexOf("/ical/");
		if (i == -1) {
			throw new Exception("Malformed iCal URL, '/ical/' part not found: "
					+ request.url);
		}
		target = target.substring(i + 6);
		i = target.indexOf('/');
		if (i == -1) {
			throw new Exception(
					"Malformed iCal URL, 4th '/' character not found: "
							+ request.url);
		}
		target = target.substring(0, i);
		return new URL(CALENDAR_FEED_PREFIX + target + CALENDAR_FEED_POSTFIX);
	}

	// --- ICAL EVENT TO GOOGLE EVENT CONVERTER ---

	private static final CalendarEventEntry convertVEvent(
			final CachedCalendar calendar, final VTimeZone[] timeZones, final VEvent event)
			throws Exception {
		final CalendarEventEntry entry = new CalendarEventEntry();
		entry.setCanEdit(true);
		entry.setDraft(new Boolean(false));
		entry.setQuickAdd(false);
		entry.setUpdated(new DateTime(new Date(), UTC));
		entry.setSendEventNotifications(false);
		String text;

		// Convert event UID to extended property
		final String uid = ICalUtilities.getUid(event);
		if (uid != null) {
			final ExtendedProperty extension = new ExtendedProperty();
			extension.setName(UID_EXTENSION_NAME);
			extension.setValue(uid);
			entry.addExtendedProperty(extension);
		}

		// Convert priority to extended property
		final Priority priority = event.getPriority();
		if (priority != null) {
			text = priority.getValue();
			if (text != null && text.length() != 0) {
				final ExtendedProperty extension = new ExtendedProperty();
				extension.setName(PRIORITY_EXTENSION_NAME);
				extension.setValue(text);
				entry.addExtendedProperty(extension);
			}
		}

		// Convert URL to extended property
		final Url url = event.getUrl();
		if (url != null) {
			text = url.getValue();
			if (text != null && text.length() != 0) {
				final ExtendedProperty extension = new ExtendedProperty();
				extension.setName(URL_EXTENSION_NAME);
				extension.setValue(text);
				entry.addExtendedProperty(extension);
			}
		}

		// Convert URL to extended property
		final Property categories = event.getProperty(Property.CATEGORIES);
		if (categories != null) {
			text = categories.getValue();
			if (text != null && text.length() != 0 && !text.startsWith("http")) {
				final ExtendedProperty extension = new ExtendedProperty();
				extension.setName(CATEGORIES_EXTENSION_NAME);
				extension.setValue(text);
				entry.addExtendedProperty(extension);
			}
		}

		// Convert created to published
		final Created created = event.getCreated();
		if (created != null) {
			final DateTime published = toDateTime(created.getDate());
			entry.setPublished(published);
		}

		// Convert summary to title
		final Summary summary = event.getSummary();
		if (summary != null) {
			text = summary.getValue();
			if (text != null && text.length() != 0) {
				entry.setTitle(new PlainTextConstruct(text));
			}
		}

		// Convert description to content
		final Description desc = event.getDescription();
		if (desc != null) {
			text = desc.getValue();
			if (text != null && text.length() != 0) {
				entry.setContent(new PlainTextConstruct(text));
			}
		}

		// Convert start date
		DtStart start = event.getStartDate();
		if (start == null) {
			Date date = null;
			if (created != null) {
				date = created.getDate();
			}
			if (date == null) {
				date = new Date();
			}
			start = new DtStart(date);
		}
		Date startDate = start.getDate();

		// Convert end date
		DtEnd end = event.getEndDate();
		if (end == null) {
			end = new DtEnd(startDate);
		}
		Date endDate = end.getDate();

		// Check dates
		if (startDate.after(endDate)) {
			final Date swap = startDate;
			startDate = endDate;
			endDate = swap;
		}

		// Set when
		final When startAndEnd = new When();
		startAndEnd.setStartTime(toDateTime(startDate));
		startAndEnd.setEndTime(toDateTime(endDate));
		entry.addTime(startAndEnd);

		// Convert location to where
		final Location location = event.getLocation();
		if (location != null) {
			text = location.getValue();
			if (text != null) {
				final Where where = new Where(text, text, text);
				entry.addLocation(where);
			}
		}

		// Convert status (tentative, confirmed, canceled)
		final Status status = event.getStatus();
		if (status != null) {
			EventStatus eventStatus;
			text = status.getValue();
			if (Status.VEVENT_CANCELLED.getValue().equals(text)) {
				eventStatus = EventStatus.CANCELED;
			} else {
				if (Status.VEVENT_CONFIRMED.getValue().equals(text)) {
					eventStatus = EventStatus.CONFIRMED;
				} else {
					eventStatus = EventStatus.TENTATIVE;
				}
			}
			entry.setStatus(eventStatus);
		}

		// Convert classification to visibility (public / private)
		final Clazz clazz = event.getClassification();
		if (clazz != null) {
			Visibility visible;
			text = clazz.getValue();
			if (Clazz.PUBLIC.getValue().equals(text)) {
				visible = Visibility.PUBLIC;
			} else {
				if (Clazz.PRIVATE.getValue().equals(text)) {
					visible = Visibility.PRIVATE;
				} else {
					visible = Visibility.DEFAULT;
				}
			}
			entry.setVisibility(visible);
		} else {
			entry.setVisibility(Visibility.DEFAULT);
		}

		// Convert transparency (transparent / opaque = free / busy)
		final Transp transp = event.getTransparency();
		if (transp == null) {

			// Default is 'Available' (=free or transparent)
			entry.setTransparency(Transparency.TRANSPARENT);
		} else {
			if (Transp.TRANSPARENT.getValue().equals(transp.getValue())) {
				entry.setTransparency(Transparency.TRANSPARENT);
			} else {
				entry.setTransparency(Transparency.OPAQUE);
			}
		}

		// Convert attendees
		final String[] emails = ICalUtilities.getAttendees(event);
		if (emails != null) {
			for(final String email : emails) {
				final EventWho who = new EventWho();
				who.setEmail(email);
				who.setAttendeeStatus(AttendeeStatus.EVENT_TENTATIVE);
				entry.addParticipant(who);
			}
		}

		// Convert recurrence
		if (start != null && end != null) {
			final Property rRule = event.getProperty(Property.RRULE);
			if (rRule != null) {
				VTimeZone timeZone = null;

				// Find time zone
				timeZone = getRecurrenceTimeZone(timeZones, event);

				// Get recurrence exceptions
				final net.fortuna.ical4j.model.Date[] dates = ICalUtilities
						.getExceptionDates(event);

				// Create recurrence value
				final Recurrence recurrence = new Recurrence();
				final QuickWriter writer = new QuickWriter(500);
				writer.write(start.toString().trim());
				writer.write(CR_LF);
				writer.write(end.toString().trim());
				writer.write(CR_LF);
				writer.write(rRule.toString().trim());
				if (dates != null) {
					for(final Date date : dates) {
						writer.write(CR_LF);
						writer.write(Property.EXDATE);
						writer.write(':');
						if (date instanceof net.fortuna.ical4j.model.DateTime) {
							final net.fortuna.ical4j.model.DateTime dateTime = (net.fortuna.ical4j.model.DateTime) date;
							dateTime.setUtc(true);
						}
						writer.write(date.toString());
					}
				}
				if (timeZone != null) {
					writer.write(CR_LF);
					writer.write(timeZone.toString().trim());
				}
				writer.write(CR_LF);
				recurrence.setValue(writer.toString());
				entry.setRecurrence(recurrence);
			}
		}

		// Convert recurrenceID
		final RecurrenceId rid = event.getRecurrenceId();
		if (rid != null) {
			final Uid property = event.getUid();
			if (property != null) {
				final String id = property.getValue();
				if (id != null) {

					// Get service from pool
					final CalendarService service = getService(calendar);

					// Create calendar's private feed URL
					final URL feedURL = getFeedURL(calendar);

					// Get original event
					final CalendarEventEntry parent = getGoogleEntryByUID(service,
							calendar, feedURL, id);
					if (parent != null) {
						final String originalHref = parent.getSelfLink().getHref();
						String originalID = originalHref;
						final int i = originalID.lastIndexOf('/');
						if (i != -1) {
							originalID = originalID.substring(i + 1);
						}

						final OriginalEvent original = new OriginalEvent();
						original.setOriginalId(originalID);
						original.setHref(originalHref);
						final When when = new When();
						when.setStartTime(toDateTime(rid.getDate()));
						original.setOriginalStartTime(when);
						entry.setOriginalEvent(original);
					}
				}
			}
		}

		// Convert reminder
		int mins = ICalUtilities.getAlarmMinutes(event);
		if (mins != -1) {
			final Reminder reminder1 = new Reminder();
			final Reminder reminder2 = new Reminder();
			final Reminder reminder3 = new Reminder();
			reminder1.setMethod(Reminder.Method.ALERT);
			reminder2.setMethod(Reminder.Method.EMAIL);
			reminder3.setMethod(Reminder.Method.SMS);
			Integer holder;
			if (mins == 0) {

				// Absolute time = clear reminders mark
				final DateTime dummy = new DateTime(0);
				reminder1.setAbsoluteTime(dummy);
				reminder2.setAbsoluteTime(dummy);
				reminder3.setAbsoluteTime(dummy);
			} else {
				if (mins <= 45) {

					// Valid minutes: 5, 10, 15, 20, 25, 30, 45
					mins = mins / 5 * 5;
					if (mins == 35 || mins == 40) {
						mins = 45;
					} else {
						if (mins == 0) {
							mins = 5;
						}
					}
					holder = new Integer(mins);
					reminder1.setMinutes(holder);
					reminder2.setMinutes(holder);
					reminder3.setMinutes(holder);
				} else {

					// Valid hours: 1, 2, 3
					int hours = mins / 60;
					if (hours == 0) {
						hours = 1;
					}
					if (hours <= 3) {
						holder = new Integer(hours);
						reminder1.setHours(holder);
						reminder2.setHours(holder);
						reminder3.setHours(holder);
					} else {

						// Valid days: 1, 2, 7
						int days = hours / 24;
						if (days == 0) {
							days = 1;
						}
						if ((days > 2 && days < 7) || days > 7) {
							days = 7;
						}
						holder = new Integer(days);
						reminder1.setDays(holder);
						reminder2.setDays(holder);
						reminder3.setDays(holder);
					}
				}
			}
			// Set "Alert" alarm
			if (enablePopup) {
				entry.getReminder().add(reminder1);
			}

			// Set "E-mail" alarm
			if (enableEmail) {
				entry.getReminder().add(reminder2);
			}

			// Set "SMS" alarm
			if (enableSms) {
				entry.getReminder().add(reminder3);
			}
		}

		return entry;
	}

	private static final DateTime toDateTime(final Date date) throws Exception {
		if (date == null) {
			return null;
		}
		final boolean isAllDay = date.toString().indexOf('T') == -1;
		DateTime dateTime;
		if (isAllDay) {
			dateTime = toOneDayEventDateTime(date);
		} else {
			dateTime = new DateTime(date, UTC);
		}
		dateTime.setDateOnly(isAllDay);
		return dateTime;
	}

	private static final DateTime toOneDayEventDateTime(final Date date)
			throws Exception {

		// Convert one day event's date to UTC date
		final String text = date.toString();
		final GregorianCalendar calendar = new GregorianCalendar(UTC);
		calendar.set(GregorianCalendar.YEAR, Integer.parseInt(text.substring(0,
				4)));
		calendar.set(GregorianCalendar.MONTH, Integer.parseInt(text.substring(
				4, 6)) - 1);
		calendar.set(GregorianCalendar.DAY_OF_MONTH, Integer.parseInt(text
				.substring(6)));
		calendar.set(GregorianCalendar.HOUR_OF_DAY, 0);
		calendar.set(GregorianCalendar.MINUTE, 0);
		calendar.set(GregorianCalendar.SECOND, 0);
		calendar.set(GregorianCalendar.MILLISECOND, 0);
		final DateTime dateTime = new DateTime(calendar.getTime(), UTC);
		return dateTime;
	}

	private static final VTimeZone getRecurrenceTimeZone(final VTimeZone[] timeZones,
			final VEvent event) throws Exception {
		if (timeZones == null || timeZones.length == 0) {
			return null;
		}
		final String tzid = getTimeZoneID(event);
		if (tzid != null) {
			VTimeZone timeZone;
			for(final VTimeZone timeZone2 : timeZones) {
				timeZone = timeZone2;
				final TzId id = timeZone.getTimeZoneId();
				if (tzid.toLowerCase().equals(
						id.getValue().toString().toLowerCase())) {
					return timeZone;
				}
			}
		}
		return null;
	}

	private static final String getTimeZoneID(final VEvent event) throws Exception {
		final Property start = event.getProperty(Property.DTSTART);
		if (start != null) {
			final String tzid = start.toString();
			if (tzid != null) {
				final int s = tzid.indexOf(Property.TZID);
				if (s != -1) {
					final int e = tzid.indexOf(':', s);
					if (e != -1) {
						return tzid.substring(s + 5, e);
					} else {
						return null;
					}
				} else {
					return null;
				}
			}
		}
		return null;
	}

	// --- GOOGLE EVENT FEED ---

	private static final List getGoogleEntries(CalendarService service,
			final CachedCalendar calendar, final URL feedURL) throws Exception {

		// Request feed
		CalendarEventFeed feed;
		for (int tries = 0;; tries++) {
			try {
				final CalendarQuery query = new CalendarQuery(feedURL);
				query.setMaxResults(MAX_FEED_ENTRIES);
				feed = service.query(query,
						CalendarEventFeed.class);
				break;
			} catch (final Exception loadError) {
				if (tries == 5) {
					throw loadError;
				}
				log.debug("Connection refused, reconnecting...");

				// Rebuild connection
				Thread.sleep(GOOGLE_RETRY_MILLIS);
				servicePool.remove(calendar.url);
				service = getService(calendar);
			}
		}

		// Return list of CalendarEventEntries
		return feed.getEntries();
	}

	// --- EVENT FINDER ---

	private static final HashMap editURLMaps = new HashMap();
	private static final HashMap uidMaps = new HashMap();

	private static final CalendarEventEntry getGoogleEntry(
			final CalendarService service, final CachedCalendar calendar, final URL feedURL,
			final VEvent event) throws Exception {

		// Get local UID
		final String uid = ICalUtilities.getUid(event);
		if (uid == null) {
			return null;
		}

		// Request entry from Google
		return getGoogleEntryByUID(service, calendar, feedURL, uid);
	}

	private final static CalendarEventEntry getGoogleEntryByUID(
			CalendarService service, final CachedCalendar calendar, final URL feedURL,
			String uid) throws Exception {

		// Create edit URL map
		if (!editURLMaps.containsKey(calendar.url)) {
			createEditURLMap(service, calendar, feedURL);
		}

		// Get editURL
		final HashMap editURLs = (HashMap) editURLMaps.get(calendar.url);
		if (editURLs == null) {
			return null;
		}
		URL editURL = (URL) editURLs.get(uid);
		if (editURL == null) {
			uid = getRemoteUID(calendar, uid);
			if (uid != null) {
				editURL = (URL) editURLs.get(uid);
				if (editURL == null) {
					return null;
				}
			} else {
				return null;
			}
		}

		// Load event
		for (int tries = 0;; tries++) {
			try {
				return service.getEntry(editURL,
						CalendarEventEntry.class);
			} catch (final Exception loadError) {
				if (tries == 5) {
					log.debug("Unable to load event (" + editURL + ")!",
							loadError);
					return null;
				}
				log.debug("Connection refused, reconnecting...");

				// Rebuild connection
				Thread.sleep(GOOGLE_RETRY_MILLIS);
				servicePool.remove(calendar.url);
				service = getService(calendar);
			}
		}
	}

	private static final String getRemoteUID(final CachedCalendar calendar, final String id) {
		final HashMap mappedUIDs = (HashMap) uidMaps.get(calendar.url);
		if (mappedUIDs == null) {
			return null;
		}
		return (String) mappedUIDs.get(id);
	}

	private static final HashMap createEditURLMap(final CalendarService service,
			final CachedCalendar calendar, final URL feedURL) throws Exception {

		// Create alarm registry
		final HashMap extensionMap = new HashMap();

		// Create edit URL map
		final List entries = getGoogleEntries(service, calendar, feedURL);
		final HashMap editURLs = new HashMap();
		final HashMap remoteUIDs = new HashMap();
		editURLMaps.put(calendar.url, editURLs);
		uidMaps.put(calendar.url, remoteUIDs);
		final Calendar oldCalendar = ICalUtilities
				.parseCalendar(calendar.previousBody);
		final VEvent[] events = ICalUtilities.getEvents(oldCalendar);

		// Loop on events
		VEvent event;
		final HashMap dateCache = new HashMap();
		for(final VEvent event2 : events) {
			event = event2;

			// Get local UID and RID
			final String uid = ICalUtilities.getUid(event);
			if (uid == null) {
				continue;
			}

			// Find original event
			final CalendarEventEntry oldEntry = findEntry(entries, event, dateCache);
			if (oldEntry == null) {
				continue;
			}

			// Get alarm
			final List reminders = oldEntry.getReminder();
			if (reminders != null && !reminders.isEmpty()) {
				extensionMap.put(uid + "\ta", reminders.get(0));
			}

			// Bind local UID to remote edit URL
			final Link editLink = oldEntry.getEditLink();
			if (editLink == null) {
				continue;
			}
			final String editURL = editLink.getHref();
			editURLs.put(uid, new URL(editURL));

			// Bind local UID to remote UID
			final List extensionList = oldEntry.getExtendedProperty();
			if (extensionList != null && !extensionList.isEmpty()) {
				final Iterator extensions = extensionList.iterator();
				ExtendedProperty extension;
				while (extensions.hasNext()) {
					extension = (ExtendedProperty) extensions.next();
					final String name = extension.getName();
					if (UID_EXTENSION_NAME.equals(name)) {
						final String localUID = extension.getValue();
						if (!uid.equals(localUID)) {
							remoteUIDs.put(localUID, uid);
						}
						continue;
					}

					// Store extensions
					if (CATEGORIES_EXTENSION_NAME.equals(name)) {
						extensionMap.put(uid + "\tc", extension.getValue());
						continue;
					}
					if (PRIORITY_EXTENSION_NAME.equals(name)) {
						extensionMap.put(uid + "\tp", extension.getValue());
						continue;
					}
					if (URL_EXTENSION_NAME.equals(name)) {
						extensionMap.put(uid + "\tu", extension.getValue());
						continue;
					}
				}
			}
		}

		// Return extensions registry (or null)
		return extensionMap;
	}

	private static final CalendarEventEntry findEntry(final List entries,
			final VEvent event, final HashMap dateCache) throws Exception {

		// Get UID and RID
		final String uid = ICalUtilities.getUid(event);

		// Get created
		long created = 0;
		final Created createdDate = event.getCreated();
		if (createdDate != null) {
			created = createdDate.getDate().getTime();
		}

		// Get start date
		String startDate = null;
		DtStart dtStart = event.getStartDate();
		if (dtStart != null) {
			final DateTime start = toDateTime(dtStart.getDate());
			if (start != null) {
				startDate = start.toUiString();
			}
		}

		// Get end date
		String endDate = null;
		DtEnd dtEnd = event.getEndDate();
		if (dtEnd != null) {
			final DateTime end = toDateTime(dtEnd.getDate());
			if (end != null) {
				endDate = end.toUiString();
			}
		}

		// Get title
		String title = null;
		final Summary summary = event.getSummary();
		if (summary != null) {
			title = ICalUtilities.normalizeLineBreaks(summary.getValue());
		}

		// Get content
		String content = null;
		final Description description = event.getDescription();
		if (description != null) {
			content = ICalUtilities.normalizeLineBreaks(description.getValue());
		}

		// Loop on Google Calendar
		CalendarEventEntry bestEntry = null;
		CalendarEventEntry entry;
		int matchCounter, bestMatch = 0;
		final Iterator entryIterator = entries.iterator();
		while (entryIterator.hasNext()) {
			entry = (CalendarEventEntry) entryIterator.next();
			matchCounter = 0;

			// Compare extended UID
			final List extensionList = entry.getExtendedProperty();
			if (uid != null && extensionList != null
					&& !extensionList.isEmpty()) {
				final Iterator extensions = extensionList.iterator();
				while (extensions.hasNext()) {
					final ExtendedProperty extension = (ExtendedProperty) extensions
							.next();
					if (UID_EXTENSION_NAME.equals(extension.getName())
							&& uid.equals(extension.getValue())) {

						// UID found -> 100% match -> stop finding
						if (log.isDebugEnabled()) {
							log.debug("Found event ("
									+ ICalUtilities.getEventTitle(event)
									+ ") in Google Calendar by unique ID.");
						}
						entryIterator.remove();
						return entry;
					}
				}
			}

			// Compare created
			final DateTime published = entry.getPublished();
			if (created != 0 && published != null) {
				final long remoteCreated = published.getValue();
				if (created == remoteCreated) {
					matchCounter++;
				} else {
					if (remoteCreated != 0 && created > remoteCreated) {
						continue;
					}
				}
			}

			// Compare title
			final TextConstruct titleConstruct = entry.getTitle();
			if (titleConstruct != null && title != null) {
				String titleText = titleConstruct.getPlainText();
				if (titleText != null) {
					titleText = ICalUtilities.normalizeLineBreaks(titleText);
					if (titleText.equals(title)) {
						matchCounter++;
					}
				}
			}

			// Compare content
			final Content contentConstruct = entry.getContent();
			if (content != null && contentConstruct instanceof TextContent) {
				final TextContent textContent = (TextContent) contentConstruct;
				String contentText = textContent.getContent().getPlainText();
				if (contentText != null) {
					contentText = ICalUtilities
							.normalizeLineBreaks(contentText);
					if (content.length() != 0 && contentText.length() != 0
							&& contentText.equals(content)) {
						matchCounter++;
					}
				}
			}

			// Compare dates and times
			final String id = entry.getId();
			String entryStart = null;
			String entryEnd = null;
			final String startKey = "s\t" + id;
			final String endKey = "e\t" + id;
			entryStart = (String) dateCache.get(startKey);
			if (startDate != null && entryStart != null) {
				if (startDate.equals(entryStart)) {
					matchCounter++;
				}
			}
			entryEnd = (String) dateCache.get(endKey);
			if (endDate != null && entryEnd != null) {
				if (endDate.equals(entryEnd)) {
					matchCounter++;
				}
			}
			if (entryStart == null || entryEnd == null) {
				final List whenList = entry.getTimes();
				if (whenList.isEmpty()) {
					final Recurrence recurrence = entry.getRecurrence();
					if (recurrence != null) {
						final VEvent holder = parseRecurrence(recurrence);
						if (holder != null) {
							dtStart = holder.getStartDate();
							if (dtStart != null) {
								final DateTime start = toDateTime(dtStart.getDate());
								if (start != null && startDate != null) {
									final boolean entryStartNull = (entryStart == null);
									entryStart = start.toUiString();
									dateCache.put(startKey, entryStart);
									if (entryStart.equals(startDate)
											&& entryStartNull) {
										matchCounter++;
									}
								}
							}
							dtEnd = holder.getEndDate();
							if (dtEnd != null) {
								final DateTime end = toDateTime(dtEnd.getDate());
								if (end != null && endDate != null) {
									final boolean entryEndNull = (entryEnd == null);
									entryEnd = end.toUiString();
									dateCache.put(endKey, entryEnd);
									if (entryEnd.equals(endDate)
											&& entryEndNull) {
										matchCounter++;
									}
								}
							}
						}
					}
				} else {
					final When when = (When) whenList.get(0);
					final DateTime start = when.getStartTime();
					if (start != null && startDate != null) {
						start.setTzShift(new Integer(0));
						final boolean entryStartNull = (entryStart == null);
						entryStart = start.toUiString();
						dateCache.put(startKey, entryStart);
						if (entryStart.equals(startDate) && entryStartNull) {
							matchCounter++;
						}
					}

					final DateTime end = when.getEndTime();
					if (end != null && endDate != null) {
						end.setTzShift(new Integer(0));
						final boolean entryEndNull = (entryEnd == null);
						entryEnd = end.toUiString();
						dateCache.put(endKey, entryEnd);
						if (entryEnd.equals(endDate) && entryEndNull) {
							matchCounter++;
						}
					}
				}
			}

			if (matchCounter > bestMatch) {
				bestMatch = matchCounter;
				bestEntry = entry;
			}
		}
		if (bestMatch < 2) {
			if (log.isDebugEnabled()) {
				log.debug("Event (" + ICalUtilities.getEventTitle(event)
						+ ") not found in Google Calendar.");
			}
			return null;
		}
		if (log.isDebugEnabled()) {
			log.debug("Found event (" + ICalUtilities.getEventTitle(event)
					+ ") in Google Calendar by " + bestMatch
					+ " concordant property.");
		}
		entries.remove(bestEntry);
		return bestEntry;
	}

	private static final VEvent parseRecurrence(final Recurrence recurrence) {
		if (recurrence == null) {
			return null;
		}
		final VEvent event = null;
		try {
			final QuickWriter writer = new QuickWriter(300);
			writer.write("BEGIN:VCALENDAR\r\n");
			writer.write("VERSION:2.0\r\n");
			writer.write("PRODID:DUMMY\r\n");
			writer.write("CALSCALE:GREGORIAN\r\n");
			writer.write("BEGIN:VEVENT\r\n");
			writer.write("UID:DUMMY\r\n");
			writer.write("SUMMARY:DUMMY\r\n");
			writer.write(recurrence.getValue());
			writer.write("\r\nEND:VEVENT\r\n");
			writer.write("END:VCALENDAR\r\n");
			final Calendar calendar = ICalUtilities.parseCalendar(writer.getBytes());
			return ICalUtilities.getEvents(calendar)[0];
		} catch (final Exception ignored) {
			log.debug(ignored);
		}
		return event;
	}

	// --- GOOGLE CONNECTION POOL ---

	private static final HashMap servicePool = new HashMap();
	private static final HashSet invalidCredentials = new HashSet();

	private static final synchronized CalendarService getService(final Request request)
			throws Exception {
		final long now = System.currentTimeMillis();
		PooledGoogleService service;
		service = (PooledGoogleService) servicePool.get(request.url);
		if (service != null) {
			if (now - service.lastUsed > GOOGLE_CONNECTION_TIMEOUT) {

				// Connection timeouted
				servicePool.remove(request.url);
				service = null;
			}
		}
		if (service == null) {

			// Create a new connection
			log.debug("Connecting to Google...");
			service = new PooledGoogleService();
			service.service = new CalendarService(Configurator.VERSION.replace(
					' ', '-'));
			final String key = request.url + '\t' + request.username + '\t'
					+ request.password;
			for (int tries = 0;; tries++) {
				try {
					service.service.setUserCredentials(
							normalizeUsername(request.username),
							request.password);
					invalidCredentials.remove(key);
					break;
				} catch (final InvalidCredentialsException wrongPassword) {
					log.fatal("Invalid Gmail username or password!");
					invalidCredentials.add(key);
					throw wrongPassword;
				} catch (final Exception ioException) {
					if (tries == 5) {
						log.fatal("Connection refused!", ioException);
						invalidCredentials.add(key);
						throw ioException;
					}
					log.debug("Connection refused, reconnecting...");
					Thread.sleep(GOOGLE_RETRY_MILLIS);
				}
			}
			if (servicePool.size() > MAX_POOLED_CONNECTIONS) {
				servicePool.clear();
			}
			servicePool.put(request.url, service);
		}
		service.lastUsed = now;
		return service.service;
	}

	private static final String normalizeUsername(final String username) {
		if (username != null && username.length() > 0) {
			if (username.endsWith("@googlemail.com")
					|| username.endsWith("@gmail")
					|| username.endsWith("@googlemail")) {
				return username.substring(0, username.indexOf('@'))
						+ "@gmail.com";
			}
			if (username.indexOf('@') == -1) {
				log.warn("Malformed username (" + username + "@where)!");
			}
		}
		return username;
	}

	// --- LIST CALENDARS ---

	private static final Properties calendarNames = new Properties();

	public static final String[] getCalendarURLs(final Request request, final File workDir)
			throws Exception {

		// Get service from pool
		if (request.url == null) {
			request.url = request.username;
		}
		CalendarService service = getService(request);

		// Create metafeed URL
		final URL feedUrl = new URL(METAFEED_URL);

		// Send the request and receive the response
		CalendarFeed resultFeed;
		for (int tries = 0;; tries++) {
			try {
				resultFeed = service.getFeed(feedUrl,
						CalendarFeed.class);
				break;
			} catch (final Exception loadError) {
				if (tries == 3) {
					throw loadError;
				}
				log.debug("Connection refused, reconnecting...");

				// Rebuild connection
				Thread.sleep(GOOGLE_RETRY_MILLIS);
				servicePool.clear();
				service = getService(request);
			}
		}

		// Convert to array
		final List entries = resultFeed.getEntries();
		if (entries == null || entries.isEmpty()) {
			return new String[0];
		}
		final LinkedList urls = new LinkedList();
		final Iterator entryIterator = entries.iterator();
		TextConstruct title;
		CalendarEntry entry;
		String url, text;
		Link link;
		int i;
		while (entryIterator.hasNext()) {
			entry = (CalendarEntry) entryIterator.next();
			link = entry.getSelfLink();
			if (link != null) {
				url = link.getHref();
				if (url != null) {
					i = url.indexOf(FEEDS_DEFAULT_PART);
					if (i != -1) {
						url = CALENDAR_ICAL_PART
								+ url
										.substring(i
												+ FEEDS_DEFAULT_PART.length())
								+ PRIVATE_BASIC_PART;
						urls.addLast(url);
						title = entry.getTitle();
						if (title != null) {
							text = title.getPlainText();
							if (text != null) {
								text = text.trim();
								if (text.length() != 0) {
									calendarNames.put(url, text);
								}
							}
						}
					}
				}
			}
		}
		saveCalendarNamesToCache(workDir);
		final String[] array = new String[urls.size()];
		urls.toArray(array);
		return array;
	}

	public static final String getCalendarName(String url, final File workDir) {
		if (url == null || url.length() == 0) {
			return null;
		}
		if (calendarNames.isEmpty()) {
			loadCalendarNamesFromCache(workDir);
		}
		if (url.startsWith(GOOGLE_HTTP_URL)) {
			url = url.substring(GOOGLE_HTTP_URL.length());
		} else {
			if (url.startsWith(GOOGLE_HTTPS_URL)) {
				url = url.substring(GOOGLE_HTTPS_URL.length());
			}
		}
		final String name = (String) calendarNames.get(url);
		if (name != null) {
			return name;
		}
		final int i = url.indexOf("/private");
		if (i != -1) {
			url = url.substring(0, i);
			final Iterator names = calendarNames.entrySet().iterator();
			Map.Entry entry;
			while (names.hasNext()) {
				entry = (Map.Entry) names.next();
				if (((String) entry.getKey()).startsWith(url)) {
					return (String) entry.getValue();
				}
			}
		}
		return null;
	}

	private static final void loadCalendarNamesFromCache(final File workDir) {
		try {
			final File file = new File(workDir, "gcal-names.txt");
			if (!file.isFile()) {
				return;
			}
			final BufferedInputStream in = new BufferedInputStream(
					new FileInputStream(file));
			calendarNames.load(in);
			in.close();
		} catch (final Exception ioException) {
			log.warn("Unable to load 'gcal-names.txt'!", ioException);
		}
	}

	private static final void saveCalendarNamesToCache(final File workDir) {
		try {
			final File file = new File(workDir, "gcal-names.txt");
			final BufferedOutputStream out = new BufferedOutputStream(
					new FileOutputStream(file));
			calendarNames.store(out, "CALENDAR NAME CACHE");
			out.flush();
			out.close();
		} catch (final Exception ioException) {
			log.warn("Unable to save 'gcal-names.txt'!", ioException);
		}
	}

}
