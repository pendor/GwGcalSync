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

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.TimeZone;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.util.CompatibilityHints;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.logger.QuickWriter;

/**
 * Config loader, property setter, and listener starter object.
 *
 * Created: Jan 03, 2007 12:50:56 PM
 *
 * @author Andras Berkes
 */
public final class Configurator {

	// --- COMMON CONSTANTS ---

	protected static final String VERSION = "GwGcalSync 1.0";

	private static final int MAX_CACHE_SIZE = 100;
	private static final SimpleDateFormat BACKUP_FORMAT = new SimpleDateFormat(
			"yyyy-MM-dd");

	// --- SIMPLE CONFIG CONSTANTS ---

	public static final String FEED_CACHE_TIMEOUT = "feed.cache.timeout";
	public static final String HTTP_ENABLED = "http.enabled";
	public static final String ICAL_BACKUP_TIMEOUT = "ical.backup.timeout";
	public static final String PROXY_PORT = "proxy.port";
	public static final String FEED_DUPLICATION_FILTER = "feed.duplication.filter";
	public static final String FEED_ENABLED = "feed.enabled";
	public static final String PROGRESS_ENABLED = "progress.enabled";
	public static final String FEED_EVENT_LENGTH = "feed.event.length";
	public static final String PROXY_HOST = "proxy.host";
	public static final String CACHE_TIMEOUT = "cache.timeout";
	public static final String WORK_DIR = "work.dir";
	public static final String REMOTE_ALARM_TYPES = "remote.alarm.types";

	// --- UTILS ---

	private Properties config = new Properties();

	private final HashMap<String, String> toDoCache = new HashMap<String, String>();
	private final HashSet<String> backupFiles = new HashSet<String>();
	private final File workDirectory;
	private final long calendarCacheTimeout;
	private final long backupTimeout;

	private long backupLastVerified;
	private File configFile;

	// --- SERVICES AND LISTENERS ---

	private Thread fileListener;

	// --- CONSTRUCTOR ---

	public Configurator(final String configPath, final Properties properties,
			final boolean userHome) throws Exception {
		int i;
		// Embedded mode
		config = properties;
		final String workPath = getConfigProperty(WORK_DIR, null);
		workDirectory = new File(workPath);

		// Disable unnecessary INFO messages of the GData API
		try {
			final java.util.logging.Logger logger = java.util.logging.Logger
					.getLogger("com.google");
			logger.setLevel(java.util.logging.Level.WARNING);
		} catch (final Throwable ingored) {
		}

		final Log log = LogFactory.getLog(Configurator.class);

		// Check permission
		if (workDirectory.isDirectory() && !workDirectory.canWrite()) {
			if (System.getProperty("os.name", "unknown").toLowerCase().indexOf(
					"windows") == -1) {
				final String path = workDirectory.getCanonicalPath();
				log.warn("Please check the file permissions on the '"
						+ workDirectory.getCanonicalPath() + "' folder!\r\n"
						+ "Hint: [sudo] chmod -R 777 " + path);
			}
		}

		// Disable all ICS file syntax validators
		CompatibilityHints.setHintEnabled(
				CompatibilityHints.KEY_RELAXED_PARSING, true);
		CompatibilityHints.setHintEnabled(
				CompatibilityHints.KEY_RELAXED_VALIDATION, true);
		CompatibilityHints.setHintEnabled(
				CompatibilityHints.KEY_RELAXED_UNFOLDING, true);
		CompatibilityHints.setHintEnabled(
				CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true);
		CompatibilityHints.setHintEnabled(
				CompatibilityHints.KEY_NOTES_COMPATIBILITY, true);

		// There's no sane reason to enable this, but I guess if your Groupwise is using a self-signed cert....
//		// Disable SSL validation
//		try {
//
//			// Create a trust manager that does not validate certificate chains
//			final javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] { new javax.net.ssl.X509TrustManager() {
//
//				@Override
//        public final java.security.cert.X509Certificate[] getAcceptedIssuers() {
//					return null;
//				}
//
//				@Override
//        public final void checkClientTrusted(
//						final java.security.cert.X509Certificate[] certs,
//						final String authType) {
//				}
//
//				@Override
//        public final void checkServerTrusted(
//						final java.security.cert.X509Certificate[] certs,
//						final String authType) {
//				}
//			} };
//
//			// Install the all-trusting trust manager
//			final javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext
//					.getInstance("SSL");
//			sc.init(null, trustAllCerts, new java.security.SecureRandom());
//			javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc
//					.getSocketFactory());
//		} catch (final Throwable ignored) {
//		}
//
//		// Replace hostname verifier
//		try {
//			final javax.net.ssl.HostnameVerifier hv[] = new javax.net.ssl.HostnameVerifier[] { new javax.net.ssl.HostnameVerifier() {
//
//				@Override
//        public final boolean verify(final String hostName,
//						final javax.net.ssl.SSLSession session) {
//					return true;
//				}
//			} };
//			javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(hv[0]);
//		} catch (final Throwable ignored) {
//		}

		// Setup proxy
		final String proxyHost = getConfigProperty(PROXY_HOST, null);
		final int proxyPort = Integer.valueOf(getConfigProperty(PROXY_PORT, "0"));
		GCalUtilities.initHttpClient(proxyHost, proxyPort);

		// Get iCal cache timeout
		long timeout = getConfigProperty(CACHE_TIMEOUT, 180000L);
		if (timeout < 60000L) {
			log.warn("The enabled minimal cache timeout is '1 min'!");
			timeout = 60000L;
		}
		calendarCacheTimeout = timeout;

		// Get backup file timeout
		timeout = getConfigProperty(ICAL_BACKUP_TIMEOUT, 604800000L);
		if (timeout < 86400000L && timeout != 0) {
			log.warn("The enabled minimal backup timeout is '1 day'!");
			timeout = 86400000L;
		}
		backupTimeout = timeout;

		// Get extended syncronization mode (alarms, url, category, etc)
		System
				.setProperty("gcaldaemon.extended.sync", Boolean.toString(true));

		// Enabled alarm types in the Google Calendar (e.g. 'sms,popup,email')
		System.setProperty("gcaldaemon.remote.alarms", getConfigProperty(REMOTE_ALARM_TYPES, "popup"));//email,sms,

		timeout = getConfigProperty(FEED_CACHE_TIMEOUT, 3600000L);
		if (timeout < 60000L) {
			log.warn("The enabled minimal feed timeout is '1 min'!");
			timeout = 60000L;
		}

		// Delete backup files
		if (backupTimeout == 0) {
			final File backupDirectory = new File(workDirectory, "backup");
			if (backupDirectory.isDirectory()) {
				final File[] backups = backupDirectory.listFiles();
				if (backups != null && backups.length != 0) {
					for (i = 0; i < backups.length; i++) {
						backups[i].delete();
					}
				}
			}
		}

		// Displays time zone
		log.info("Local time zone is " + TimeZone.getDefault().getDisplayName()
				+ ".");

		// Get main thread group
		ThreadGroup mainGroup = Thread.currentThread().getThreadGroup();
		while (mainGroup.getParent() != null) {
			mainGroup = mainGroup.getParent();
		}
	}

	public final File getConfigFile() {
		return configFile;
	}

	// --- COMMON CONFIGURATION PROPERTY GETTERS ---

	private final String getConfigProperty(final String name, final String defaultValue) {
		String value = config.getProperty(name, defaultValue);
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

	private final boolean getConfigProperty(final String name, final boolean defaultValue) {
		final String bool = config.getProperty(name, Boolean.toString(defaultValue))
				.toLowerCase();
		return "true".equals(bool) || "on".equals(bool) || "1".equals(bool);
	}

	private final long getConfigProperty(final String name, final long defaultValue)
			throws Exception {
		final String number = config.getProperty(name, Long.toString(defaultValue));
		try {
			return StringUtils.stringToLong(number);
		} catch (final Exception malformed) {
			throw new IllegalArgumentException("Malformed numeric parameter ("
					+ name + ")!");
		}
	}


	// --- GLOBAL CALENDAR CACHE ---

	private final HashMap<String,CachedCalendar> calendarCache = new HashMap<String,CachedCalendar>();

	public final synchronized CachedCalendar getCalendar(final Request request)
			throws Exception {
		CachedCalendar calendar = calendarCache.get(request.url);
		final boolean isSyncJob = request.url.endsWith(".ics");
		final long now = System.currentTimeMillis();
		if (calendar != null) {
			final long timeOut = calendarCacheTimeout;
			if (now - calendar.lastModified >= timeOut) {
				calendarCache.remove(request.url);
			} else {

				// Return calendar from cache
				return calendar;
			}
		}
		calendar = new CachedCalendar();


			// Load calendar from Google
			calendar.body = GCalUtilities.loadCalendar(request);
		if (calendarCache.size() >= MAX_CACHE_SIZE) {
			calendarCache.clear();
		}

		// Load todo block
		calendar.toDoBlock = loadToDoBlock(request);
		calendar.filePath = request.filePath;
		calendar.lastModified = now;
		calendarCache.put(request.url, calendar);
		if (backupTimeout != 0 && isSyncJob) {

			// Do the daily backup
			calendar.url = request.url;
			if (now - backupLastVerified > 3600000L) {
				backupLastVerified = now;
				backupFiles.clear();
			}
			if (!backupFiles.contains(request.url)) {
				backupFiles.add(request.url);
				manageBackups(calendar, now);
			}
		}
		return calendar;
	}

	// --- ON-DEMAND SYNCHRONIZER ---

	public final synchronized void synchronizeNow(final Request request)
			throws Exception {

		// Find error marker
		final String content = StringUtils.decodeToString(request.body,
				StringUtils.UTF_8);
		if (content.indexOf(GCalUtilities.ERROR_MARKER) != -1) {
			return;
		}

		// Save to-do block
		final String toDoBlock = saveToDoBlock(request, content);

		// Create calendar container
		final long now = System.currentTimeMillis();
		final CachedCalendar calendar = new CachedCalendar();
		calendar.body = request.body;
		calendar.lastModified = now;

		final boolean isSyncJob = request.url.endsWith(".ics");
		if (isSyncJob) {

			// Load calendar from Google
			calendar.previousBody = GCalUtilities.loadCalendar(request);
		}

		// Verify loaded ics file
		final char[] chars = new char[Math.min(calendar.previousBody.length, 100)];
		for (int i = 0; i < chars.length; i++) {
			chars[i] = (char) calendar.previousBody[i];
		}
		if ((new String(chars)).indexOf(GCalUtilities.ERROR_MARKER) != -1) {
			return;
		}

		// Store other properties
		calendar.username = request.username;
		calendar.password = request.password;
		calendar.filePath = request.filePath;
		calendar.method = request.method;
		calendar.url = request.url;
		calendar.toDoBlock = toDoBlock;
		if (calendarCache.size() >= MAX_CACHE_SIZE) {
			calendarCache.clear();
		}
		calendarCache.put(request.url, calendar);

		// Do the daily backup
		if (backupTimeout != 0 && isSyncJob) {
			calendar.url = request.url;
			if (now - backupLastVerified > 3600000L) {
				backupLastVerified = now;
				backupFiles.clear();
			}
			if (!backupFiles.contains(request.url)) {
				backupFiles.add(request.url);
				manageBackups(calendar, now);
			}
		}

		// Notify file listener (save new calendar file)
		if (request.method != null && fileListener != null) {
			final Method wakeUp = fileListener.getClass().getMethod("wakeUp",
					new Class[0]);
			wakeUp.invoke(fileListener, new Object[0]);
		}
	}

	// --- BACKUP HANDLER ---

	private final void manageBackups(final CachedCalendar calendar, final long now)
			throws Exception {

		// Get backup dir
		final File backupDirectory = new File(workDirectory, "backup");
		if (!backupDirectory.isDirectory()) {
			backupDirectory.mkdirs();
		}

		// Cleanup backup directory
		if (backupFiles.size() == 1) {
			final String[] files = backupDirectory.list();
			File backup;
			for(final String file : files) {
				backup = new File(backupDirectory, file);
				if (now - backup.lastModified() > backupTimeout) {
					backup.delete();
				}
			}
		}

		// Generate backup file names (2007-05-12-ical-3947856328.bak)
		final String hashCode = Long.toString(Math.abs(calendar.url.hashCode()));
		final String date = BACKUP_FORMAT.format(new Date(now));
		final String icalFileName = date + "-ical-" + hashCode + ".ics";
		final String gcalFileName = date + "-gcal-" + hashCode + ".ics";
		final File icalBackupFile = new File(backupDirectory, icalFileName);
		final File gcalBackupFile = new File(backupDirectory, gcalFileName);

		// Save Google backup
		byte[] bytes = calendar.toByteArray();
		saveBackup(gcalBackupFile, bytes);

		// Save local backup
		if (calendar.filePath == null) {
			return;
		}
		final File localFile = new File(calendar.filePath);
		if (!localFile.isFile()) {
			return;
		}
		if (icalBackupFile.exists()) {
			return;
		}
		RandomAccessFile in = null;
		try {
			in = new RandomAccessFile(localFile, "r");
			bytes = new byte[(int) localFile.length()];
			in.readFully(bytes);
			in.close();
			saveBackup(icalBackupFile, bytes);
		} catch (final Exception ioException) {
			if (in != null) {
				in.close();
			}
		}
	}

	private static final void saveBackup(final File backup, final byte[] bytes) {
		if (!backup.exists()) {
			FileOutputStream out = null;
			try {
				if (bytes == null) {
					return;
				}
				final char[] header = new char[Math.min(bytes.length, 1024)];
				for (int i = 0; i < header.length; i++) {
					header[i] = (char) bytes[i];
				}
				final String test = new String(header);
				if (test.indexOf(GCalUtilities.ERROR_MARKER) != -1) {
					return;
				}
				out = new FileOutputStream(backup);
				out.write(bytes);
				out.flush();
				out.close();
			} catch (final Exception ioException) {
				if (out != null) {
					try {
						out.close();
					} catch (final Exception ignored) {
					}
				}
			}
		}
	}

	// --- TO-DO HANDLERS ---

	private final String saveToDoBlock(final Request p_request, final String p_content)
			throws Exception {
	  String content = p_content;
		final int s = content.indexOf(Component.VTODO);
		final int e = content.lastIndexOf(Component.VTODO);
		if (s == -1 || e == -1) {
			getToDoFile(p_request).delete();
			toDoCache.remove(p_request.url);
			return null;
		}
		content = content.substring(s, e);

		// Crop todo block from ical file
		String toDoBlock;
		if (content.indexOf(Component.VEVENT) == -1) {

			// Fast solution
			toDoBlock = "BEGIN:" + content + "VTODO\r\n";
		} else {

			// Slow and safe solution
			final Calendar calendar = ICalUtilities.parseCalendar(p_request.body);
			final VToDo[] toDoArray = ICalUtilities.getToDos(calendar);
			final QuickWriter writer = new QuickWriter();
			for(final VToDo element : toDoArray) {
				writer.write(element.toString());
			}
			toDoBlock = writer.toString();
		}

		// Compare with cached instance
		if (toDoBlock.equals(toDoCache.get(p_request.url))) {
			return toDoBlock;
		}

		// Save block
		toDoCache.put(p_request.url, toDoBlock);
		final byte[] toDoBytes = StringUtils.encodeString(toDoBlock,
				StringUtils.UTF_8);
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(getToDoFile(p_request));
			fos.write(toDoBytes);
		} finally {
			if (fos != null) {
				fos.close();
			}
		}
		return toDoBlock;
	}

	private final String loadToDoBlock(final Request request) throws Exception {
		String toDoBlock = toDoCache.get(request.url);
		if (toDoBlock != null) {
			return toDoBlock;
		}
		final File file = getToDoFile(request);
		if (file.exists()) {
			RandomAccessFile raf = null;
			try {
				raf = new RandomAccessFile(file, "r");
				final byte[] bytes = new byte[(int) raf.length()];
				raf.readFully(bytes);
				raf.close();
				toDoBlock = StringUtils
						.decodeToString(bytes, StringUtils.UTF_8);
				if (toDoCache.size() > MAX_CACHE_SIZE) {
					toDoCache.clear();
				}
				toDoCache.put(request.url, toDoBlock);
				return toDoBlock;
			} catch (final Exception ioError) {
			  Closer.close(raf);
				file.delete();
			}
		}
		return null;
	}

	private final File getToDoFile(final Request request) throws Exception {
		final String hash = Integer.toHexString(request.url.hashCode());
		String prefix;
		if (request.url.endsWith(".ics")) {
			prefix = "gcal";
			final int e = request.url.indexOf('%');
			if (e != -1) {
				final int s = request.url.lastIndexOf('/', e);
				if (s != -1) {
					prefix = request.url.substring(s, e).replace('.', '-')
							.replace('_', '-');
				}
			}
		} else {
			prefix = request.url.replace('/', ' ').replace(':', ' ').replace(
					'.', ' ');
			if (prefix.startsWith("http")) {
				prefix = prefix.substring(4);
			}
			if (prefix.startsWith("s ")) {
				prefix = prefix.substring(2);
			}
			prefix = prefix.trim();
			if (prefix.startsWith("www ")) {
				prefix = prefix.substring(4);
			}
			prefix = prefix.trim();
			final int e = prefix.indexOf(' ');
			if (e != -1) {
				prefix = prefix.substring(0, e);
			}
		}
		final File todoDirectory = new File(workDirectory, "todo");
		if (!todoDirectory.isDirectory()) {
			todoDirectory.mkdirs();
		}
		return new File(todoDirectory, prefix + '-' + hash + ".ics");
	}

	public final File getWorkDirectory() {
		return workDirectory;
	}
}
