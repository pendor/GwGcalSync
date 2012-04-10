package org.galbraiths.groupwise;

import java.io.FileNotFoundException;

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
    final GroupwiseConfig config;
    try {
      config = new GroupwiseConfig();
    } catch(final FileNotFoundException ex) {
      System.err.println(ex.getMessage());
      System.err.println("Please copy the default settings file into a folder in your home directory and edit it appropriately.");
      return;
    }

    final CalendarUpdateThread calUpdate = new CalendarUpdateThread(config);
    calUpdate.start();

    if(config.isHttpServerEnabled()) {
      final SunHttpServer server = new SunHttpServer(config, calUpdate);
      server.start();
    }
  }
}
