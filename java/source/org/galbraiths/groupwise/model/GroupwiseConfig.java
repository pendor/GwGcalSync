package org.galbraiths.groupwise.model;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.galbraiths.groupwise.util.Closer;
import org.galbraiths.groupwise.util.StringUtils;

/**
 * Loads exporter configuration from properties file & also configures logging.
 *
 * @author zbedell
 */
public class GroupwiseConfig {

  private int m_refreshMinutes;
  private String m_url;
  private String m_username;
  private String m_password;
  private int m_retrieveMonths;
  private String m_proxyHost;
  private int m_proxyPort;

  private String m_listenIp;
  private int m_listenPort;

  private String m_gmailUsername;
  private String m_gmailPassword;
  private String m_gmailCalendarName;

  private String m_gmailProxyHost;
  private int m_gmailProxyPort;

  private boolean m_oneShot;

  private final File m_usedConfigFile;

  private static final String DEFAULT_CFG_FILE =
      System.getProperty("user.home") + File.separatorChar + ".gwgcalsync" + File.separatorChar + "settings.properties";

  private static final String LOG_CFG_NAME = "logging.properties";

  private static final String DEFAULT_LOG_CONFIG =
      System.getProperty("user.home") + File.separatorChar + ".gwgcalsync" + File.separatorChar + LOG_CFG_NAME;

  public GroupwiseConfig() throws IOException {
    this(new File(DEFAULT_CFG_FILE), new File(DEFAULT_LOG_CONFIG));
  }

  private GroupwiseConfig(final File p_cfgFile, final File p_logCfgFile) throws IOException {
    loadLoggingConfig(p_logCfgFile);

    if(p_cfgFile.exists()) {
      m_usedConfigFile = new File(p_cfgFile.getParentFile(), "gw.ics");
      final Properties properties = new Properties();
      FileInputStream in = null;
      try {
        in = new FileInputStream(p_cfgFile);
        properties.load(in);

        m_refreshMinutes = Integer.valueOf(properties.getProperty("minutes", "5"));
        m_url = properties.getProperty("groupwise.url");

        if(m_url.endsWith("/")) {
          m_url = m_url.substring(0, m_url.length() - 1);
        }

        m_username = properties.getProperty("groupwise.username");
        m_password = properties.getProperty("groupwise.password");
        m_retrieveMonths = Integer.valueOf(properties.getProperty("months", "24"));
        m_proxyHost = properties.getProperty("groupwise.proxy");
        m_proxyPort = Integer.valueOf(properties.getProperty("groupwise.proxyPort", "0"));
        m_listenIp = properties.getProperty("bind", "127.0.0.1");
        m_listenPort = Integer.valueOf(properties.getProperty("port", "8123"));

        m_gmailUsername = properties.getProperty("gmail.username");
        m_gmailPassword = properties.getProperty("gmail.password");
        m_gmailCalendarName = properties.getProperty("gmail.calendar");
        m_gmailProxyHost = properties.getProperty("gmail.proxy");
        m_gmailProxyPort = Integer.valueOf(properties.getProperty("gmail.proxyPort", "0"));

        m_oneShot = Boolean.getBoolean(properties.getProperty("oneshot", "false"));

      } finally {
        Closer.close(in);
      }
    } else {
      throw new FileNotFoundException("Missing configuration file at $HOME/.groupwiseexporter/settings.properties");
    }
  }

  private void loadLoggingConfig(final File p_logCfgFile) throws IOException {
    BufferedReader rdr = null;
    ByteArrayInputStream bis = null;
    try {
      new File(p_logCfgFile.getParentFile(), "logs").mkdirs();

      final InputStream in;
      if(p_logCfgFile.exists()) {
        in = new FileInputStream(p_logCfgFile);
      } else {
        in = GroupwiseConfig.class.getResourceAsStream("/" + LOG_CFG_NAME);
      }

      if(in == null) {
        System.err.println("Couldn't get logging.properties from classloader?");
        return;
      }

      // Read in the props file we have & replace the 'work.dir' parameter.
      rdr = new BufferedReader(new InputStreamReader(in));
      final StringBuilder sbProps = new StringBuilder();
      String line;
      while((line = rdr.readLine()) != null) {
        sbProps.append(line).append('\n');
      }

      // Wrap it in a new input stream so we can stuff it into jul.
      final String sReplaced = sbProps.toString().replaceAll("\\$\\{work\\.dir\\}", p_logCfgFile.getParent());
      bis = new ByteArrayInputStream(sReplaced.getBytes("UTF-8"));
      LogManager.getLogManager().readConfiguration(bis);
    } finally {
      Closer.close(rdr);
      Closer.close(bis);
    }
  }

  public boolean isGmailEnabled() {
    return StringUtils.notNullOrEmpty(m_gmailUsername) && StringUtils.notNullOrEmpty(m_gmailPassword) && StringUtils.notNullOrEmpty(m_gmailCalendarName);
  }

  public boolean isHttpServerEnabled() {
    return StringUtils.notNullOrEmpty(m_listenIp) || m_listenPort > 0;
  }

  public File getCalendarCache() {
    return m_usedConfigFile;
  }

  public int getRefreshMinutes() {
    return this.m_refreshMinutes;
  }
  public void setRefreshMinutes(final int p_refreshMinutes) {
    this.m_refreshMinutes = p_refreshMinutes;
  }
  public String getUrl() {
    return this.m_url;
  }
  public void setUrl(final String p_url) {
    this.m_url = p_url;
  }
  public String getUsername() {
    return this.m_username;
  }
  public void setUsername(final String p_username) {
    this.m_username = p_username;
  }
  public String getPassword() {
    return this.m_password;
  }
  public void setPassword(final String p_password) {
    this.m_password = p_password;
  }
  public int getRetrieveMonths() {
    return this.m_retrieveMonths;
  }
  public void setRetrieveMonths(final int p_retrieveMonths) {
    this.m_retrieveMonths = p_retrieveMonths;
  }
  public String getProxyHost() {
    return this.m_proxyHost;
  }
  public void setProxyHost(final String p_proxyHost) {
    this.m_proxyHost = p_proxyHost;
  }
  public int getProxyPort() {
    return this.m_proxyPort;
  }
  public void setProxyPort(final int p_proxyPort) {
    this.m_proxyPort = p_proxyPort;
  }
  public String getListenIp() {
    return this.m_listenIp;
  }
  public void setListenIp(final String p_listenIp) {
    this.m_listenIp = p_listenIp;
  }
  public int getListenPort() {
    return this.m_listenPort;
  }
  public void setListenPort(final int p_listenPort) {
    this.m_listenPort = p_listenPort;
  }

  public String getGmailUsername() {
    return this.m_gmailUsername;
  }

  public void setGmailUsername(final String p_gmailUsername) {
    this.m_gmailUsername = p_gmailUsername;
  }

  public String getGmailPassword() {
    return this.m_gmailPassword;
  }

  public void setGmailPassword(final String p_gmailPassword) {
    this.m_gmailPassword = p_gmailPassword;
  }

  public String getGmailCalendarName() {
    return this.m_gmailCalendarName;
  }

  public void setGmailCalendarName(final String p_gmailCalendarName) {
    this.m_gmailCalendarName = p_gmailCalendarName;
  }

  public String getGmailProxyHost() {
    return this.m_gmailProxyHost;
  }

  public void setGmailProxyHost(final String p_gmailProxyHost) {
    this.m_gmailProxyHost = p_gmailProxyHost;
  }

  public int getGmailProxyPort() {
    return this.m_gmailProxyPort;
  }

  public void setGmailProxyPort(final int p_gmailProxyPort) {
    this.m_gmailProxyPort = p_gmailProxyPort;
  }

  public boolean isOneShot() {
    return m_oneShot;
  }

  public void setOneShot(final boolean oneShot) {
    this.m_oneShot = oneShot;
  }
}
