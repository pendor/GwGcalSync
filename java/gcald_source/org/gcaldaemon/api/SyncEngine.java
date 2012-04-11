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
package org.gcaldaemon.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.util.Properties;

import org.gcaldaemon.core.CachedCalendar;
import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.GCalUtilities;
import org.gcaldaemon.core.Request;
import org.gcaldaemon.core.StringUtils;
import org.gcaldaemon.logger.QuickWriter;

/**
 * Embeddable synchronizer engine for Google Calendar. Examples:<br>
 * <br>
 *
 * File workDir = new File("/etc/work");<br>
 * File localCalendar = new File("/etc/calendar.ics");<br>
 * URL remoteCalendar = new URL("http://www.google.com/private ical url");<br>
 * String username = "user@gmail.com";<br>
 * String password = "gmailpassword";<br>
 * <br>
 *
 * SyncEngine engine = new SyncEngine(workDir);<br>
 * engine.synchronize(localCalendar, remoteCalendar, username, password);<br>
 * <br>
 *
 * or<br>
 * <br>
 *
 * PDAConnection pda = new PDAConnection("COM3");<br>
 * File workDir = new File("/etc/work");<br>
 * byte[] icalBytes = pda.loadCalendar();<br>
 * URL remoteCalendar = new URL("http://www.google.com/private ical url");<br>
 * String username = "user@gmail.com";<br>
 * String password = "gmailpassword";<br>
 * <br>
 *
 * SyncEngine engine = new SyncEngine(workDir);<br>
 * icalBytes = engine.synchronize(icalBytes, remoteCalendar, username,
 * password);<br>
 * pda.saveCalendar(icalBytes);<br>
 * <br>
 *
 * WARNING: SyncEngine is NOT a thread-safe object, so web components such as
 * Servlets and JSPs should avoid injecting it and storing it as a shared field.
 * If you wish to use SyncEngine in a multithreaded environment, you will have
 * to create a static synchronization object or a static synchronization block
 * to manage a cached instance.<br>
 * <br>
 *
 * SyncEngine utilizes the logging interface provided by the Commons Logging
 * package. Commons Logging provides a simple and generalized log interface to
 * various logging packages. By using Commons Logging, SyncEngine can be
 * configured for a variety of different logging behaviours. That means the
 * developer will have to make a choice which logging framework to use. To
 * specify a specific logger be used, set this system property:<br>
 * <br>
 *
 * org.apache.commons.logging.Log<br>
 * <br>
 *
 * to one of:<br>
 * <li>org.apache.commons.logging.impl.SimpleLog
 * <li>org.apache.commons.logging.impl.AvalonLogger
 * <li>org.apache.commons.logging.impl.Jdk13LumberjackLogger
 * <li>org.apache.commons.logging.impl.Jdk14Logger
 * <li>org.apache.commons.logging.impl.Log4JLogger
 * <li>org.apache.commons.logging.impl.LogKitLogger
 * <li>org.apache.commons.logging.impl.NoOpLog
 * <li>org.gcaldaemon.logger.DefaultLog<br>
 *
 * By default, the SyncEngine will use the DefaultLog framework. DefaultLog is a
 * simple implementation of the Log interface that sends all log messages to
 * "System.out". Configuration example:
 * System.setProperty("org.apache.commons.logging.Log",
 * "org.apache.commons.logging.impl.Jdk14Logger");
 *
 * Created: Jan 22, 2008 12:50:56 PM
 *
 * @author Andras Berkes
 */
public final class SyncEngine {

	// --- CONSTANTS ---

	private static final String GOOGLE_HTTPS_URL = "https://www.google.com";

	// --- INTERNAL VARIABLES ---

	/**
	 * Contains the engine's registry, ToDo items, and backups.
	 */
	private final File m_workDir;

	/**
	 * Container of the engine's configuration.
	 */
	private final Properties m_properties;

	/**
	 * Internal configurator and calendar synchronizer utility (cached
	 * instance).
	 */
	private Configurator m_configurator;

	/**
	 * Property that indicates the properties in the engine's configuration have
	 * been changed.
	 */
	private boolean m_configChanged;

	// --- CONSTRUCTORS ---

	/**
	 * Default constructor, the SyncEngine will use 'USER_HOME/.gcaldaemon'
	 * folder as working directory.
	 *
	 * @throws FileNotFoundException
	 *             unable to open or create the 'USER_HOME/.gcaldaemon'
	 *             directory (file permission problem)
	 */
	public SyncEngine() throws FileNotFoundException {

		// 'null' means 'USER_HOME/.gcaldaemon'
		this(null);
	}

	/**
	 * Creates a SyncEngine instance with the given working directory. Google
	 * Calendar - unlike Sunbird/Lightning - does not support ToDo (Task) items.
	 * Therefore GCALDaemon stores ToDo items in a local file storage. This
	 * storage is the 'working directory'.
	 *
	 * @param workDir
	 *            working directory (contains the engine's registry, ToDo items,
	 *            and backups)
	 *
	 * @throws FileNotFoundException
	 *             unable to open or create the specified working directory
	 *             (file permission problem)
	 */
	private SyncEngine(final File p_workDir) throws FileNotFoundException {
	  File workDir = p_workDir;
		// Init working directory
		if (workDir == null) {
			String home = System.getProperty("user.home");
			if (home == null || home.length() == 0) {
				home = "/";
			}
			workDir = new File(home, ".gcaldaemon");
		}
		if (!workDir.isDirectory() && !workDir.mkdirs()) {

			// File permission problem
			throw new FileNotFoundException(String.valueOf(workDir));
		}
		this.m_workDir = workDir;
		final String workDirPath = workDir.getAbsolutePath();
		m_properties = new Properties();
		m_configChanged = true;

		// Set default engine properties (synchronizer)
		m_properties.put(Configurator.WORK_DIR, workDirPath);
		m_properties.put(Configurator.CACHE_TIMEOUT, "180000");
		m_properties.put(Configurator.HTTP_ENABLED, "false");
		m_properties.put(Configurator.PROGRESS_ENABLED, "false");
		m_properties.put(Configurator.ICAL_BACKUP_TIMEOUT, "604800000");
		m_properties.put(Configurator.REMOTE_ALARM_TYPES, "popup");//email,sms,

		// Set default engine properties (RSS/ATOM feed converter)
//		try {
//
//			// Verify classpath
//			Class.forName("com.sun.syndication.io.SyndFeedInput");
//
//			// Enable feed converter
//			m_properties.put(Configurator.FEED_ENABLED, "true");
//			m_properties.put(Configurator.FEED_CACHE_TIMEOUT, "3600000");
//			m_properties.put(Configurator.FEED_EVENT_LENGTH, "2700000");
//			m_properties.put(Configurator.FEED_DUPLICATION_FILTER, "70");
//		} catch (final Throwable ignored) {

			// Disable feed converter
			m_properties.put(Configurator.FEED_ENABLED, "false");
//		}

//		// Init default logger (DefaultLog). DefaultLog is an implementation of
//		// the Log interface that sends all enabled log messages, for all
//		// defined loggers, to System.out.
//		if (System.getProperty("org.apache.commons.logging.Log") == null) {
//
//			// You can override this setting with the
//			// "org.apache.commons.logging.Log" system property.
//			// Available log factories:
//			//
//			// 1) org.apache.commons.logging.impl.SimpleLog
//			// 2) org.apache.commons.logging.impl.AvalonLogger
//			// 3) org.apache.commons.logging.impl.Jdk13LumberjackLogger
//			// 4) org.apache.commons.logging.impl.Jdk14Logger
//			// 5) org.apache.commons.logging.impl.Log4JLogger
//			// 6) org.apache.commons.logging.impl.LogKitLogger
//			// 7) org.apache.commons.logging.impl.NoOpLog
//			// 8) org.gcaldaemon.logger.DefaultLog
//			//
//			System.setProperty("org.apache.commons.logging.Log",
//					"org.gcaldaemon.logger.DefaultLog");
//		}
	}

	// --- GOOGLE CALENDAR INFO ---

	/**
	 * Lists Google Calendars in the account specified by the given
	 * username/password.
	 *
	 * @param username
	 *            full name of the user (eg. "username@gmail.com" or
	 *            "username@mydomain.org")
	 * @param password
	 *            Gmail password (in unencrypted, plain text format)
	 *
	 * @return array of the remote calendars
	 *
	 * @throws Exception
	 *             any exception (eg. i/o, invalid param, invalid password)
	 *
	 * @see #loadCalendar
	 * @see #synchronize
	 */
	public final RemoteCalendar[] listCalendars(final String username, final String password)
			throws Exception {

		// Verify required parameters
		if (username == null || username.length() == 0) {
			throw new NullPointerException("username = null");
		}
		if (username.indexOf('@') == -1) {
			throw new IllegalArgumentException("invalid username");
		}
		if (password == null || password.length() == 0) {
			throw new NullPointerException("password = null");
		}

		// Create (or reinitialize) the cached instance
		if (m_configChanged) {
			m_configChanged = false;
			m_configurator = new Configurator(null, m_properties, false);
		}

		// Create request container
		final Request request = new Request();
		request.username = username;
		request.password = password;

		// Load paths
		final String[] paths = GCalUtilities.getCalendarURLs(request, m_workDir);

		// Convert to RemoteCalendar array
		final RemoteCalendar[] array = new RemoteCalendar[paths.length];
		String path;
		URL url;
		for (int i = 0; i < paths.length; i++) {
			path = paths[i];
			url = new URL(GOOGLE_HTTPS_URL + path);
			array[i] = new RemoteCalendar(GCalUtilities.getCalendarName(path, m_workDir), url);
		}
		return array;
	}

	// --- MAIN SYNCHRONIZER / FEED CONVERTER METHODS ---

	/**
	 * Synchronizes a remote Google Calendar to a local iCalendar (RFC 2445)
	 * file. If the local calendar file does not exists, the SyncEngine will
	 * download and save the original iCalendar file (with ToDo entries) without
	 * any synchronization. Creates daily backups of all Google AND local
	 * calendars into the 'backup' subdirectory (under the working directory).
	 *
	 * @param localCalendar
	 *            local calendar file
	 * @param remoteCalendar
	 *            Google Calendar's private ICAL URL
	 *            ("https://www.google.com/calendar/ical/.../basic.ics"), or the
	 *            RSS/ATOM feed's URL (= feed converter mode)
	 * @param username
	 *            full name of the user (eg. "username@gmail.com" or
	 *            "username@mydomain.org"), this value is optional in feed
	 *            converter mode
	 * @param password
	 *            Gmail password (in unencrypted, plain text format), this value
	 *            is optional in feed converter mode
	 *
	 * @throws Exception
	 *             any exception (eg. i/o, invalid param, invalid calendar
	 *             syntax, etc)
	 *
	 * @see #getCacheTimeout
	 * @see #setCacheTimeout
	 */
	public final void synchronize(final File localCalendar, final URL remoteCalendar,
			final String username, final String password) throws Exception {

		// Verify required parameters
		if (localCalendar == null) {
			throw new NullPointerException("localCalendar = null");
		}
		if (remoteCalendar == null) {
			throw new NullPointerException("remoteCalendar = null");
		}
		final String path = remoteCalendar.getPath();

		// Synchronizer mode
		if (username == null || username.length() == 0) {
			throw new NullPointerException("username = null");
		}
		if (username.indexOf('@') == -1) {
			throw new IllegalArgumentException("invalid username");
		}
		if (password == null || password.length() == 0) {
			throw new NullPointerException("password = null");
		}

		// Load local calendar file
		byte[] bytes = null;
		if (localCalendar.isFile()) {
			RandomAccessFile file = null;
			try {
				file = new RandomAccessFile(localCalendar, "r");
				bytes = new byte[(int) localCalendar.length()];
				file.readFully(bytes);
			} finally {
				if (file != null) {
					file.close();
				}
			}
		}

		// Create (or reinitialize) the cached instance
		if (m_configChanged) {
			m_configChanged = false;
			m_configurator = new Configurator(null, m_properties, false);
		}

		// Create request container
		final Request request = new Request();
		request.body = bytes;
		request.url = path;
		request.username = username;
		request.password = password;
		request.filePath = localCalendar.getAbsolutePath();

		// Do synchronization (if the 'localCalendar' is defined)
		if (bytes != null && bytes.length != 0) {
			m_configurator.synchronizeNow(request);
		}

		// Return the modified calendar (with ToDo entries)
		final CachedCalendar calendar = m_configurator.getCalendar(request);
		bytes = calendar.toByteArray();

		// Save new content into the calendar file
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(localCalendar);
			out.write(bytes);
			out.flush();
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	// --- PRIVATE PROPERTY GETTERS/SETTERS ---

	/**
	 * Puts a String property into the engine's configuration.
	 *
	 * @param key
	 *            name of the config property
	 * @param value
	 *            value of the config property
	 */
	public final void setConfigProperty(final String key, final String value) {
		// Compare to the previous value
		final String previous = m_properties.getProperty(key, "");
		if (previous.equals(value)) {
			return;
		}

		// Set new value
		m_properties.setProperty(key, value);
		m_configChanged = true;
	}

	/**
	 * Puts a numeric (long) property into the engine's configuration.
	 *
	 * @param key
	 *            name of the config property
	 * @param value
	 *            value of the config property
	 *
	 * @see #setConfigProperty
	 */
	private final void setConfigProperty(final String key, final long value) {
		setConfigProperty(key, Long.toString(value));
	}

	/**
	 * Sets the value of the 'remote.alarm.types' property.
	 *
	 * @param enableEmail
	 *            enables/disables email alerts (or null)
	 * @param enableSMS
	 *            enables/disables sms alerts (or null)
	 * @param enablePopup
	 *            enables/disables popups (or null)
	 *
	 * @see #setConfigProperty
	 */
	private final void setConfigProperty(final Boolean enableEmail,
			final Boolean enableSMS, final Boolean enablePopup) {
		final String value = m_properties.getProperty(Configurator.REMOTE_ALARM_TYPES,
				"email,sms,popup");
		boolean email = value.indexOf("mail") != -1;
		boolean sms = value.indexOf("sms") != -1;
		boolean popup = value.indexOf("pop") != -1;
		if (enableEmail != null) {
			email = enableEmail.booleanValue();
		}
		if (enableSMS != null) {
			email = enableSMS.booleanValue();
		}
		if (enablePopup != null) {
			email = enablePopup.booleanValue();
		}
		if (!email && !sms && !popup) {
			email = true;
			sms = true;
			popup = true;
		}
		final QuickWriter alarm = new QuickWriter(20);
		if (email) {
			alarm.write("email,");
		}
		if (sms) {
			alarm.write("sms,");
		}
		if (popup) {
			alarm.write("popup,");
		}
		alarm.setLength(alarm.length() - 1);
		setConfigProperty(Configurator.REMOTE_ALARM_TYPES, alarm.toString());
	}

	/**
	 * Searches for the property with the specified key in the configuration.
	 * The method returns the default value argument if the property is not
	 * found (or empty).
	 *
	 * @param key
	 *            name of the config property
	 * @param defaultValue
	 *            a default value
	 *
	 * @return the value in the config with the specified key value
	 *
	 * @see #getConfigProperty
	 */
	private final String getConfigProperty(final String key, final String defaultValue) {
		String value = m_properties.getProperty(key, defaultValue);
		if (value == null) {
			return defaultValue;
		} else {
			value = value.trim();
			if (value.length() == 0) {
				return defaultValue;
			}
		}
		return value;
	}

	/**
	 * Searches for a boolean property with the specified key in the
	 * configuration.
	 *
	 * @param key
	 *            name of the config property
	 * @param defaultValue
	 *            default boolean value
	 *
	 * @return the value in the config with the specified key value
	 *
	 * @see #getConfigProperty
	 */
	private final boolean getConfigProperty(final String key, final boolean defaultValue) {
		final String bool = m_properties.getProperty(key,
				Boolean.toString(defaultValue)).toLowerCase();
		return "true".equals(bool) || "on".equals(bool) || "1".equals(bool);
	}

	/**
	 * Searches for a numeric (long) property with the specified key in the
	 * configuration.
	 *
	 * @param key
	 *            name of the config property
	 * @param defaultValue
	 *            default long value
	 *
	 * @return the value in the config with the specified key value
	 *
	 * @see #getConfigProperty
	 */
	private final long getConfigProperty(final String key, final long defaultValue) {
		final String number = m_properties
				.getProperty(key, Long.toString(defaultValue));
		try {
			return StringUtils.stringToLong(number);
		} catch (final Exception ignored) {
		}
		return defaultValue;
	}

	// --- PUBLIC PROPERTY GETTERS/SETTERS [SYNCHRONIZER] ---

	/**
	 * Returns the value of the 'cache.timeout' property (= calendar timeout in
	 * the local cache). The default value is '60000';
	 *
	 * @return cache timeout in milliseconds (eg. 60000 = 1 minute)
	 *
	 * @see #setCacheTimeout
	 * @see #getConfigProperty
	 */
	public final long getCacheTimeout() {
		return getConfigProperty(Configurator.CACHE_TIMEOUT, 60000L);
	}

	/**
	 * Sets the value of the 'cache.timeout' property (= calendar timeout in the
	 * local cache). Minimum (and default) value is 60000 milliseconds;
	 *
	 * @param millis
	 *            cache timeout in milliseconds (eg. 60000 = 1 minute)
	 *
	 * @see #getCacheTimeout
	 * @see #setConfigProperty
	 */
	public final void setCacheTimeout(final long millis) {

		// Verification (60000...n)
		if (millis < 60000L) {
			throw new IllegalArgumentException("cache.timeout < 1 min");
		}
		setConfigProperty(Configurator.CACHE_TIMEOUT, millis);
	}

	/**
	 * Returns the value of the 'ical.backup.timeout' property (= backup file
	 * timeout in the working directory). Default is 604800000 = one week, 0 =
	 * disable backups.
	 *
	 * @return backup timeout in milliseconds (eg. 604800000 = 1 week)
	 *
	 * @see #setIcalBackupTimeout
	 * @see #getConfigProperty
	 */
	public final long getIcalBackupTimeout() {
		return getConfigProperty(Configurator.ICAL_BACKUP_TIMEOUT, 604800000L);
	}

	/**
	 * Sets the value of the 'ical.backup.timeout' property (= backup file
	 * timeout in the working directory). Default is 604800000 = one week, 0 =
	 * disable backups.
	 *
	 * @param millis
	 *            backup timeout in milliseconds (eg. 604800000 = 1 week)
	 *
	 * @see #getIcalBackupTimeout
	 * @see #setConfigProperty
	 */
	public final void setIcalBackupTimeout(final long millis) {

		// Verification (0 or 86400000...n)
		if (millis < 86400000L && millis != 0) {
			throw new IllegalArgumentException("ical.backup.timeout < 1 day");
		}
		setConfigProperty(Configurator.ICAL_BACKUP_TIMEOUT, millis);
	}

	/**
	 * Returns true if the Email alarm type is enabled. The default value is
	 * 'true'.
	 *
	 * @return true or false (true = enabled)
	 *
	 * @see #setEmailAlarmsEnabled
	 * @see #getConfigProperty
	 */
	public final boolean getEmailAlarmsEnabled() {
		final String value = getConfigProperty(Configurator.REMOTE_ALARM_TYPES,
				"email,sms,popup");
		return (value.indexOf("email") != -1);
	}

	/**
	 * Enables/disables the Email alarm type. The default value is 'true'.
	 *
	 * @param enable
	 *            true or false (true = enabled)
	 *
	 * @see #getEmailAlarmsEnabled
	 * @see #setConfigProperty
	 */
	public final void setEmailAlarmsEnabled(final boolean enable) {
		setConfigProperty(Boolean.valueOf(enable), null, null);
	}

	/**
	 * Returns true if the SMS alarm type is enabled. The default value is
	 * 'true'.
	 *
	 * @return true or false (true = enabled)
	 *
	 * @see #setSMSAlarmsEnabled
	 * @see #getConfigProperty
	 */
	public final boolean getSMSAlarmsEnabled() {
		final String value = getConfigProperty(Configurator.REMOTE_ALARM_TYPES,
				"email,sms,popup");
		return (value.indexOf("sms") != -1);
	}

	/**
	 * Enables/disables the SMS alarm type. The default value is 'true'.
	 *
	 * @param enable
	 *            true or false (true = enabled)
	 *
	 * @see #getSMSAlarmsEnabled
	 * @see #setConfigProperty
	 */
	public final void setSMSAlarmsEnabled(final boolean enable) {
		setConfigProperty(null, Boolean.valueOf(enable), null);
	}

	/**
	 * Returns true if the 'popup' alarm type is enabled. The default value is
	 * 'true'.
	 *
	 * @return true or false (true = enabled)
	 *
	 * @see #setPopupAlarmsEnabled
	 * @see #getConfigProperty
	 */
	public final boolean getPopupAlarmsEnabled() {
		final String value = getConfigProperty(Configurator.REMOTE_ALARM_TYPES,
				"email,sms,popup");
		return (value.indexOf("popup") != -1);
	}

	/**
	 * Enables/disables the 'popup' alarm type. The default value is 'true'.
	 *
	 * @param enable
	 *            true or false (true = enabled)
	 *
	 * @see #getPopupAlarmsEnabled
	 * @see #setConfigProperty
	 */
	public final void setPopupAlarmsEnabled(final boolean enable) {
		setConfigProperty(null, null, Boolean.valueOf(enable));
	}

	// --- PUBLIC PROPERTY GETTERS/SETTERS [FEED CONVERTER] ---

	/**
	 * Make sure the RSS/ATOM converter is available.
	 *
	 * @throws UnsupportedOperationException
	 *             feed converter unavailable (missing JARs)
	 */
	private final void checkFeedConverter()
			throws UnsupportedOperationException {
		if (!getConfigProperty(Configurator.FEED_ENABLED, true)) {
			throw new UnsupportedOperationException(
					"feed converter unavailable, check classpath");
		}
	}

	/**
	 * Returns the value of the 'feed.cache.timeout' property (= timeout of the
	 * RSS files in the memory cache). The default value is '3600000' (= 1
	 * hour).
	 *
	 * @return cache timeout in milliseconds (eg. 60000 = 1 minute)
	 *
	 * @see #setFeedCacheTimeout
	 * @see #getConfigProperty
	 */
	public final long getFeedCacheTimeout() {
		return getConfigProperty(Configurator.FEED_CACHE_TIMEOUT, 3600000L);
	}

	/**
	 * Sets the value of the 'feed.cache.timeout' property (= timeout of the RSS
	 * files in the memory cache). The default value is '3600000' (= 1 hour).
	 *
	 * @param millis
	 *            cache timeout in milliseconds (min. 60000 = 1 minute)
	 *
	 * @see #getFeedCacheTimeout
	 * @see #setConfigProperty
	 */
	public final void setFeedCacheTimeout(final long millis) {

		// Make sure the RSS/ATOM converter is available
		checkFeedConverter();

		// Verification (60000...n)
		if (millis < 60000L) {
			throw new IllegalArgumentException("feed.cache.timeout < 1 min");
		}
		setConfigProperty(Configurator.FEED_CACHE_TIMEOUT, millis);
	}

	/**
	 * Returns the value of the 'feed.event.length' property (= length of the
	 * converted feed events in calendar). The default value is '2700000' (= 45
	 * minutes).
	 *
	 * @return event length in milliseconds (eg. 60000 = 1 minute)
	 *
	 * @see #setFeedEventLength
	 * @see #getConfigProperty
	 */
	public final long getFeedEventLength() {
		return getConfigProperty(Configurator.FEED_EVENT_LENGTH, 2700000L);
	}

	/**
	 * Sets the value of the 'feed.event.length' property (= length of the
	 * converted feed events in calendar). The default value is '2700000' (= 45
	 * minutes).
	 *
	 * @param millis
	 *            event length in milliseconds (min 60000 = 1 minute)
	 *
	 * @see #getFeedEventLength
	 * @see #setConfigProperty
	 */
	public final void setFeedEventLength(final long millis) {

		// Make sure the RSS/ATOM converter is available
		checkFeedConverter();

		// Verification (60000...n)
		if (millis < 60000L) {
			throw new IllegalArgumentException("feed.event.length < 1 min");
		}
		setConfigProperty(Configurator.FEED_EVENT_LENGTH, millis);
	}

	/**
	 * Returns the value of the 'feed.duplication.filter' property (=
	 * sensitivity of the RSS duplication filter, 50 = very sensitive, 100 =
	 * disabled). The default value is '70'.
	 *
	 * @return sensitivity (40 - 100)
	 *
	 * @see #setFeedDuplicationFilter
	 * @see #getConfigProperty
	 */
	public final int getFeedDuplicationFilter() {
		return (int) getConfigProperty(Configurator.FEED_DUPLICATION_FILTER, 70);
	}

	/**
	 * Sets the value of the 'feed.duplication.filter' property (= sensitivity
	 * of the RSS duplication filter, 50 = very sensitive, 100 = disabled). The
	 * default value is '70'.
	 *
	 * @param percent
	 *            sensitivity (40 - 100)
	 *
	 * @see #getFeedDuplicationFilter
	 * @see #setConfigProperty
	 */
	public final void setFeedDuplicationFilter(final int percent) {

		// Make sure the RSS/ATOM converter is available
		checkFeedConverter();

		// Verification (40...100)
		if (percent < 40) {
			throw new IllegalArgumentException("feed.duplication.filter < 40");
		}
		if (percent > 100) {
			throw new IllegalArgumentException("feed.duplication.filter > 100");
		}
		setConfigProperty(Configurator.FEED_DUPLICATION_FILTER, percent);
	}

}
