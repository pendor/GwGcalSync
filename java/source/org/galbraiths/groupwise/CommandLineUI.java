package org.galbraiths.groupwise;

import java.io.FileNotFoundException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.galbraiths.groupwise.calendar.CalendarUpdateThread;
import org.galbraiths.groupwise.http.SunHttpServer;
import org.galbraiths.groupwise.model.GroupwiseConfig;

/**
 * Main class.
 *
 * @author zbedell
 */
public class CommandLineUI { // NO_UCD
  private static Log logger = LogFactory.getLog("GwGcalSync");

  public static void main(final String[] args) throws Exception {
    final GroupwiseConfig config;
    try {
      config = new GroupwiseConfig();
    } catch(final FileNotFoundException ex) {
      logger.fatal(ex.getMessage());
      logger.fatal("Please copy the default settings file into a folder in your home directory and edit it appropriately.");
      return;
    }

    final CalendarUpdateThread calUpdate = new CalendarUpdateThread(config);
    if(config.isOneShot()) {
      logger.info("Set for one-shot mode.  Will exit after sync.");
      calUpdate.scanOnce();
    } else {
      logger.info("Starting update thread...");
      calUpdate.start();

      if(config.isHttpServerEnabled()) {
        logger.info("Starting HTTP Server on " + config.getListenIp() + ":" + config.getListenPort());
        final SunHttpServer server = new SunHttpServer(config, calUpdate);
        server.start();
      }
    }
  }
}
