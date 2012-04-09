package org.galbraiths.groupwise.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.galbraiths.groupwise.util.Closer;

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

  private static final String DEFAULT_CFG_FILE =
      System.getProperty("user.home") + File.separatorChar + ".groupwiseexporter" + File.separatorChar + "settings.properties";

  /** Default filename is relative to where ever cfg file is. */
  private static final String DEFAULT_LOG4J_CONFIG = "log4j.properties";

  public GroupwiseConfig() throws IOException {
    this(new File(DEFAULT_CFG_FILE));
  }

  public GroupwiseConfig(final File p_cfgFile) throws IOException {
    System.setProperty("log4j.configuration", new File(p_cfgFile.getParentFile(), DEFAULT_LOG4J_CONFIG).toURI().toString());

    final Properties properties = new Properties();
    if(p_cfgFile.exists()) {
      FileInputStream in = null;
      try {
        in = new FileInputStream(p_cfgFile);
        properties.load(in);

        m_refreshMinutes = Integer.valueOf(properties.getProperty("minutes", "5"));
        m_url = properties.getProperty("url");

        if(m_url.endsWith("/")) {
          m_url = m_url.substring(0, m_url.length() - 1);
        }

        m_username = properties.getProperty("username");
        m_password = properties.getProperty("password");
        m_retrieveMonths = Integer.valueOf(properties.getProperty("months", "24"));
        m_proxyHost = properties.getProperty("proxy");
        m_proxyPort = Integer.valueOf(properties.getProperty("proxyPort", "0"));
        m_listenIp = properties.getProperty("bind");
        m_listenPort = Integer.valueOf(properties.getProperty("port", "8123"));

      } finally {
        Closer.close(in);
      }
    } else {
      throw new IOException("Missing configuration file at $HOME/.groupwiseexporter/settings.properties");
    }
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



}
