package org.galbraiths.groupwise;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.transport.connect.SocketConnection;

public class CommandLineUI {
  private static String m_calendar = "";
  private static Date m_lastModified = new Date();
  private static final String VERSION = "1.0";
  private final static Lock m_httpLock = new ReentrantLock();

  public static void main(final String[] args) throws Exception {
    final String appDirLocation = System.getProperty("user.home") + File.separator + ".groupwiseexporter";
    System.setProperty("log4j.configuration", new File(appDirLocation + "/log4j.properties").toURI().toString());

    final File appDir = new File(appDirLocation);

    // load preferences
    final Properties properties = new Properties();
    final File propFile = new File(appDir, "settings.properties");
    if(propFile.exists()) {
      final FileInputStream in = new FileInputStream(propFile);
      properties.load(in);
      in.close();
    } else {
      System.err.println("Missing configuration file at $HOME/.groupwiseexporter/settings.properties");
      return;
    }

    final int minutes = Integer.valueOf(properties.getProperty("minutes", "5"));
    final String url = properties.getProperty("url");
    final String username = properties.getProperty("username");
    final String password = properties.getProperty("password");
    final int months = Integer.valueOf(properties.getProperty("months", "24"));
    final String proxy1 = properties.getProperty("proxy");
    final String listen = properties.getProperty("bind");
    final int port = Integer.valueOf(properties.getProperty("port", "8123"));

    new Thread("Calendar refresh thread") {
      @Override
      public void run() {
        final CalendarScraperMinimal minimal = new CalendarScraperMinimal(url, username, password, proxy1);
        while(true) {
          boolean locked = false;
          try {
            final String cal = VcalendarExporter.getVcalendar(minimal.getCalendarEvents(months));
            if(!m_calendar.equals(cal)) {
              m_httpLock.lock();
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
              m_httpLock.unlock();
            }
          }
          Sleep.sleepMinutes(minutes);
        }
      }
    }.start();

    new SocketConnection(new Container() {
      @Override
      public void handle(final Request request, final Response response) {
        OutputStream out = null;
        boolean locked = false;
        try {
          response.set("Server", "GroupWise Exporter v" + VERSION);
          response.setDate("Date", new Date().getTime());
          response.setDate("Last-Modified", m_lastModified.getTime());

          if(StringUtils.nullOrEmpty(m_calendar) || !m_httpLock.tryLock(10, TimeUnit.SECONDS)) {
            response.setCode(500);
            response.set("Content-Type", "text/plain;charset=UTF-8");
            response.getPrintStream().print("Waiting for calendar refresh...");
            response.close();
          } else {
            locked = true;
            response.set("Content-Type", "text/calendar;charset=UTF-8");
            final byte[] bytes = m_calendar.getBytes("UTF8");

            // Don't need the lock while we're streaming the response.
            m_httpLock.unlock();
            locked = false;

            response.setContentLength(bytes.length);
            out = response.getOutputStream();
            out.write(bytes);
            out.close();
          }

        } catch(final IOException e) {
          e.printStackTrace();
        } catch(final InterruptedException ex) {
          ex.printStackTrace();
        } finally {
          if(locked) {
            m_httpLock.unlock();
          }
          Closer.close(out);
        }
      }
    }).connect(listen == null ? new InetSocketAddress(port) : new InetSocketAddress(listen, port));

    System.err.println("Now Accepting Connections");
  }
}
