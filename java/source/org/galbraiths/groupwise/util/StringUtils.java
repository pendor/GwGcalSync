package org.galbraiths.groupwise.util;

public class StringUtils {
  public static boolean nullOrEmpty(final CharSequence s) {
    return (s == null) || s.length() == 0;
  }

  public static boolean notNullOrEmpty(final CharSequence s) {
    return !nullOrEmpty(s);
  }
}
