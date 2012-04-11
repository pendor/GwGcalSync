package org.gcaldaemon.core;

import java.io.Closeable;
import java.io.IOException;

/**
 * Safely closes Closeable's.
 *
 * @author zbedell
 */
final class Closer {

  public static void close(final Closeable... p_cl) {
    if(p_cl != null) {
      try {
        for(final Closeable cl : p_cl) {
          if(cl != null) {
            cl.close();
          }
        }
      } catch(final IOException ex) {
        // Do nothing.
      }
    }
  }
}
