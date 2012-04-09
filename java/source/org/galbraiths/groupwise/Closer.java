package org.galbraiths.groupwise;

import java.io.Closeable;
import java.io.IOException;
import java.util.zip.ZipFile;

/**
 * Safely closes Closeable's.
 *
 * @author zbedell
 */
public final class Closer {

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

  public static void close(final ZipFile p_cl) {
    if(p_cl != null) {
      try {
        p_cl.close();
      } catch(final IOException ex) {
        // Do nothing.
      }
    }
  }
}
