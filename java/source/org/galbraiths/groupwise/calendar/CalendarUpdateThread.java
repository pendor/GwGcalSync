package org.galbraiths.groupwise.calendar;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.galbraiths.groupwise.model.GroupwiseConfig;
import org.galbraiths.groupwise.util.Closer;
import org.galbraiths.groupwise.util.Sleep;
import org.galbraiths.groupwise.util.StringUtils;

public class CalendarUpdateThread extends Thread {
  private static Log logger = LogFactory.getLog(CalendarUpdateThread.class);
  private static final String ERR = "Waiting for calendar refresh...";

  private Date m_lastModified = new Date();

  private final GroupwiseConfig m_config;
  private final Lock m_lock = new ReentrantLock();
  private final CalendarScraperMinimal m_scraper;
  private final GmailPublisher m_gmailPub;

  public CalendarUpdateThread(final GroupwiseConfig p_cfg) throws IOException {
    super("Calendar refresh thread");
    m_config = p_cfg;
    m_scraper = new CalendarScraperMinimal(m_config);

    if(p_cfg.isGmailEnabled()) {
      m_gmailPub = new GmailPublisher(m_config.getGmailUsername(), m_config.getGmailPassword(), m_config.getGmailCalendarName(),
          m_config.getGmailProxyHost(), m_config.getGmailProxyPort());
    } else {
      m_gmailPub = null;
    }

  }

  public CharSequence getVCal() throws TimeoutException {
    boolean locked = false;
    try {
      if(m_lock.tryLock(10, TimeUnit.SECONDS)) {
        locked = true;
        final CharSequence sCal = getCalendar();
        if(StringUtils.nullOrEmpty(sCal)) {
          throw new TimeoutException(ERR);
        }
        return sCal;
      } else {
        throw new TimeoutException(ERR);
      }
    } catch(final InterruptedException ex) {
      throw new TimeoutException(ERR);
    } finally {
      if(locked) {
        m_lock.unlock();
      }
    }
  }

  public CharSequence getCalendar() {
    final File cache = m_config.getCalendarCache();
    if(!cache.exists()) {
      return ERR;
    }

    BufferedReader rdr = null;
    try {
      final StringBuffer sb = new StringBuffer((int)cache.length());
      rdr = new BufferedReader(new FileReader(cache));
      String s;
      while((s = rdr.readLine()) != null) {
        sb.append(s).append("\n");
      }
      return sb;
    } catch(final IOException ex) {
      ex.printStackTrace();
      return ERR;
    } finally {
      Closer.close(rdr);
    }
  }

  public void setCalendar(final CharSequence p_cal) {
    BufferedWriter bw = null;
    try {
      final File cache = m_config.getCalendarCache();
      final File tmp = File.createTempFile("gwtmp", ".ics");

      bw = new BufferedWriter(new FileWriter(tmp));
      bw.write(p_cal.toString());
      Closer.close(bw);

      tmp.renameTo(cache);

    } catch(final IOException ex) {
      ex.printStackTrace();
    } finally {
      Closer.close(bw);
    }
  }

  public Date getLastModified() {
    return m_lastModified;
  }

  @Override
  public void run() {
    logger.info("Starting first scrape.  Calendar data will be available shortly...");
    boolean bFirst = true;
    for(;;) {
      boolean locked = false;
      try {
        final CharSequence cal = VcalendarExporter.getVcalendar(m_scraper.getCalendarEvents(m_config.getRetrieveMonths()));
        if(!getCalendar().equals(cal)) {
          m_lock.lock();
          locked = true;
          setCalendar(cal);
          m_lastModified = new Date();

          if(bFirst) {
            // First time...
            bFirst = false;
            logger.info("Calendar data now available.");
          }

          if(m_gmailPub != null) {
            m_gmailPub.push(m_config.getCalendarCache());
          }
        }
      } catch(final Exception e) {
        e.printStackTrace();
      } finally {
        if(locked) {
          m_lock.unlock();
        }
      }
      Sleep.sleepMinutes(m_config.getRefreshMinutes());
    }
  }
}
