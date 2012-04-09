package org.galbraiths.groupwise.util;

public class Sleep {

  public static void sleepSeconds(final int p_sec) {
    try {
      Thread.sleep(p_sec * 1000);
    } catch(final InterruptedException ex) {
    }
  }

  public static void sleepMinutes(final int p_min) {
    sleepSeconds(p_min * 60);
  }

}
