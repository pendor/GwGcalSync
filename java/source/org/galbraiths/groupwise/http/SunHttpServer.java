package org.galbraiths.groupwise.http;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.galbraiths.groupwise.calendar.CalendarUpdateThread;
import org.galbraiths.groupwise.model.GroupwiseConfig;
import org.galbraiths.groupwise.util.Closer;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Http server implemented using com.sun classes.
 *
 * @author zbedell
 */
public class SunHttpServer implements HttpHandler {

  private static final String VERSION = "1.5 (simple)";

  private final CalendarUpdateThread m_thread;
  private final GroupwiseConfig m_config;
  private final DateFormat m_dateFormat;

  private HttpServer m_server;

  public SunHttpServer(final GroupwiseConfig p_config, final CalendarUpdateThread p_thread) {
    m_thread = p_thread;
    m_config = p_config;

    final String pattern = "EEE, dd MMM yyyy HH:mm:ss zzz";
    final TimeZone gmtTZ = TimeZone.getTimeZone("GMT");
    m_dateFormat = new SimpleDateFormat(pattern, Locale.US);
    m_dateFormat.setTimeZone(gmtTZ);
  }

  public void start() throws IOException {
    final InetSocketAddress addr;
    if(m_config.getListenIp() == null) {
      addr = new InetSocketAddress(m_config.getListenPort());
    } else {
      addr = new InetSocketAddress(m_config.getListenIp(), m_config.getListenPort());
    }

    m_server = HttpServer.create(addr, 0);
    m_server.createContext("/", this);
    m_server.setExecutor(new ThreadPoolExecutor(1, 2, 5, TimeUnit.MINUTES, new SynchronousQueue<Runnable>()));
    m_server.start();

    System.err.println("Now Accepting Connections");
  }

  /**
   * @throws IOException for compat with Simple version
   */
  public void stop() throws IOException {
    m_server.stop(0);
  }

  @Override
  public void handle(final HttpExchange exchange) throws IOException {
    String contentType;
    int resCode;
    byte[] bytes;

    try {
      bytes = m_thread.getVCal().getBytes("UTF8");
      resCode = 200;
      contentType = "text/calendar;charset=UTF-8";
    } catch(final TimeoutException ex) {
      // Thrown if calendar isn't ready yet.
      resCode = 500;
      bytes = ex.getMessage().getBytes("UTF8");
      contentType = "text/plain;charset=UTF-8";
    }

    OutputStream out = null;
    try {
      final Headers head = exchange.getResponseHeaders();
      head.add("Server", "GroupWise Exporter v" + VERSION);
      head.add("Last-Modified", m_dateFormat.format(m_thread.getLastModified()));
      head.add("Content-Type", contentType);
      exchange.sendResponseHeaders(resCode, bytes.length);
      out = exchange.getResponseBody();
      out.write(bytes);
    } finally {
      Closer.close(out);
    }
  }
}
