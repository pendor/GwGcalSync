package org.galbraiths.groupwise.calendar;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.galbraiths.groupwise.model.GroupwiseConfig;
import org.galbraiths.groupwise.util.Sleep;
import org.galbraiths.groupwise.util.StringUtils;

public class CalendarUpdateThread extends Thread {
  private static final String ERR = "Waiting for calendar refresh...";

  private String m_calendar = "";
  private Date m_lastModified = new Date();

  private final GroupwiseConfig m_config;
  private final Lock m_lock = new ReentrantLock();
  private final CalendarScraperMinimal m_scraper;

  public CalendarUpdateThread(final GroupwiseConfig p_cfg) {
    super("Calendar refresh thread");
    m_config = p_cfg;
    m_scraper = new CalendarScraperMinimal(m_config);
  }

  public String getVCal() throws TimeoutException {
    boolean locked = false;
    try {
      if(m_lock.tryLock(10, TimeUnit.SECONDS)) {
        locked = true;
        if(StringUtils.nullOrEmpty(m_calendar)) {
          throw new TimeoutException(ERR);
        }
        return m_calendar;
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

  public Date getLastModified() {
    return m_lastModified;
  }

  @Override
  public void run() {
    while(true) {
      boolean locked = false;
      try {
        final String cal = VcalendarExporter.getVcalendar(m_scraper.getCalendarEvents(m_config.getRetrieveMonths()));
        if(!m_calendar.equals(cal)) {
          m_lock.lock();
          locked = true;

          if(StringUtils.nullOrEmpty(m_calendar)) {
            // First time...
            System.err.println("Calendar now available");
          }

          m_calendar = cal;
          m_lastModified = new Date();
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
