package org.galbraiths.groupwise;

import org.galbraiths.groupwise.calendar.CalendarUpdateThread;
import org.galbraiths.groupwise.http.SunHttpServer;
import org.galbraiths.groupwise.model.GroupwiseConfig;

/**
 * Simple command line launcher.
 *
 * @author zbedell
 */
public class CommandLineUI {
  public static void main(final String[] args) throws Exception {
    final GroupwiseConfig config = new GroupwiseConfig();

    final CalendarUpdateThread calUpdate = new CalendarUpdateThread(config);
    calUpdate.start();

    //final SimpleHttpServer server = new SimpleHttpServer(config, calUpdate);
    final SunHttpServer server = new SunHttpServer(config, calUpdate);
    server.start();
  }
}
