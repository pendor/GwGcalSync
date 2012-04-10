package org.galbraiths.groupwise.calendar;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.galbraiths.groupwise.util.StringUtils;
import org.gcaldaemon.api.RemoteCalendar;
import org.gcaldaemon.api.SyncEngine;
import org.gcaldaemon.core.Configurator;

/**
 * Pushes a calendar to Gmail.
 *
 * @author zbedell
 */
public class GmailPublisher {
  private static Log logger = LogFactory.getLog(GmailPublisher.class);

  private final String m_username;
  private final String m_password;
  private final String m_calName;


  private final RemoteCalendar m_cal;
  private final SyncEngine m_engine;

  public GmailPublisher(final String p_username, final String p_password, final String p_calName) throws IOException {
    this(p_username, p_password, p_calName, null, -1);
  }

  public GmailPublisher(final String p_username, final String p_password, final String p_calName, final String p_proxyHost, final int p_proxyPort) throws IOException {
    m_username = p_username;
    m_password = p_password;
    m_calName = p_calName;

    m_engine = new SyncEngine();

    if(StringUtils.notNullOrEmpty(p_proxyHost) && p_proxyPort > 0) {
      m_engine.setConfigProperty(Configurator.PROXY_HOST, p_proxyHost);
      m_engine.setConfigProperty(Configurator.PROXY_PORT, Integer.toString(p_proxyPort));
    }

    RemoteCalendar theOne = null;
    try {
      final RemoteCalendar[] cals = m_engine.listCalendars(m_username, m_password);
      for(final RemoteCalendar cal : cals) {
        if(cal.getName().equals(m_calName)) {
          theOne = cal;
          break;
        }
      }
      m_cal = theOne;
    } catch(final Exception ex) {
      throw new IOException("Error configuring calendar", ex);
    }

    if(m_cal == null) {
      throw new IOException("Couldn't find calendar named in config file.");
    }
  }


  /** Push the ICS file to gmail.
   * @throws IOException */
  public void push(final File p_ical) throws IOException {
    try {
      m_engine.synchronize(p_ical, m_cal.getURL(), m_username, m_password);
      logger.info("Sync to gmail completed at " + new Date().toString());
    } catch(final Exception ex) {
      throw new IOException("Error synchronizing calendar", ex);
    }
  }
}
