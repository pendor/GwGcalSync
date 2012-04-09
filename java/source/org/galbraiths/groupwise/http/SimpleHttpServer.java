package org.galbraiths.groupwise.http;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.TimeoutException;

import org.galbraiths.groupwise.calendar.CalendarUpdateThread;
import org.galbraiths.groupwise.model.GroupwiseConfig;
import org.galbraiths.groupwise.util.Closer;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.transport.connect.SocketConnection;

/**
 * Simple HTTP server based on simple framework.
 *
 * @author zbedell
 */
public class SimpleHttpServer implements Container {
  private static final String VERSION = "1.5 (simple)";

  private final CalendarUpdateThread m_thread;
  private SocketConnection m_socket;
  private final GroupwiseConfig m_config;

  public SimpleHttpServer(final GroupwiseConfig p_config, final CalendarUpdateThread p_thread) {
    m_thread = p_thread;
    m_config = p_config;

  }

  public void start() throws IOException {
    m_socket = new SocketConnection(this);
    if(m_config.getListenIp() == null) {
      m_socket.connect(new InetSocketAddress(m_config.getListenPort()));
    } else {
      m_socket.connect(new InetSocketAddress(m_config.getListenIp(), m_config.getListenPort()));
    }
    System.err.println("Now Accepting Connections");
  }

  public void stop() throws IOException {
    m_socket.close();
    m_socket = null;
  }

  @Override
  public void handle(final Request request, final Response response) {
    OutputStream out = null;
    try {
      response.set("Server", "GroupWise Exporter v" + VERSION);
      response.setDate("Date", new Date().getTime());
      response.setDate("Last-Modified", m_thread.getLastModified().getTime());

      try {
        final String sCalendar = m_thread.getVCal();
        response.set("Content-Type", "text/calendar;charset=UTF-8");
        final byte[] bytes = sCalendar.getBytes("UTF8");
        response.setContentLength(bytes.length);
        out = response.getOutputStream();
        out.write(bytes);
        out.close();
      } catch(final TimeoutException ex) {
        // Thrown if calendar isn't ready yet.
        response.setCode(500);
        response.set("Content-Type", "text/plain;charset=UTF-8");
        response.getPrintStream().print("Waiting for calendar refresh...");
        response.close();
      }
    } catch(final IOException e) {
      e.printStackTrace();
    } finally {
      Closer.close(out);
    }
  }
}
