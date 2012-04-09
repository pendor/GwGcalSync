package org.galbraiths.groupwise;

public class StringUtils {
  public static boolean nullOrEmpty(final String s) {
    return (s == null) || "".equals(s);
  }

  public static boolean notNullOrEmpty(final String s) {
    return !nullOrEmpty(s);
  }
}
