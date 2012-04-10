package org.galbraiths.groupwise.calendar;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

import org.galbraiths.groupwise.util.StringUtils;


public class VcalendarExporter {
  public static CharSequence getVcalendar(final List<CalendarEvent> calendarEvents) {
    final DateFormat fullDate = new SimpleDateFormat("yyyyMMdd'T'HHmm'00'");

    final StringBuffer sb = new StringBuffer();

    sb.append("BEGIN:VCALENDAR\n")
      .append("VERSION:2.0\n")
      .append("PRODID:-//Ben Galbraith//Groupwise Scraper//EN\n")
      .append("CALSCALE:GREGORIAN\n")
      .append("METHOD:PUBLISH\n")
//    .append("BEGIN:VTIMEZONE\n")
//    .append("TZID:US/Mountain\n")
//    .append("LAST-MODIFIED:20050516T200736Z\n")
//    .append("BEGIN:DAYLIGHT\n")
//    .append("DTSTART:20040404T090000\n")
//    .append("TZOFFSETTO:-0600\n")
//    .append("TZOFFSETFROM:+0000\n")
//    .append("TZNAME:MDT\n")
//    .append("END:DAYLIGHT\n")
//    .append("BEGIN:STANDARD\n")
//    .append("DTSTART:20041031T020000\n")
//    .append("TZOFFSETTO:-0700\n")
//    .append("TZOFFSETFROM:-0600\n")
//    .append("TZNAME:MST\n")
//    .append("END:STANDARD\n")
//    .append("BEGIN:DAYLIGHT\n")
//    .append("DTSTART:20050403T010000\n")
//    .append("TZOFFSETTO:-0600\n")
//    .append("TZOFFSETFROM:-0700\n")
//    .append("TZNAME:MDT\n")
//    .append("END:DAYLIGHT\n")
//    .append("END:VTIMEZONE\n")
    // FIXME: Don't hard code this...
      .append("BEGIN:VTIMEZONE\n")
      .append("TZID:America/New_York\n")
      .append("BEGIN:DAYLIGHT\n")
      .append("TZOFFSETFROM:-0500\n")
      .append("RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=2SU\n")
      .append("DTSTART:20070311T020000\n")
      .append("TZNAME:EDT\n")
      .append("TZOFFSETTO:-0400\n")
      .append("END:DAYLIGHT\n")
      .append("BEGIN:STANDARD\n")
      .append("TZOFFSETFROM:-0400\n")
      .append("RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=1SU\n")
      .append("DTSTART:20071104T020000\n")
      .append("TZNAME:EST\n")
      .append("TZOFFSETTO:-0500\n")
      .append("END:STANDARD\n")
      .append("END:VTIMEZONE\n");

    for(final CalendarEvent event : calendarEvents) {
      sb
        .append("BEGIN:VEVENT\n")

        .append("DTSTART;TZID=America/New_York:")
        .append(fullDate.format(event.getEventStart()))
        .append("\n")

        .append("DTEND;TZID=America/New_York:")
        .append(fullDate.format(event.getEventStop()))
        .append("\n")

        .append("SUMMARY:")
        .append(event.getDescription())
        .append("\n");

      if(StringUtils.notNullOrEmpty(event.getLocation())) {
        sb.append("DESCRIPTION:")
          .append("Location: ")
          .append(event.getLocation()).append("\\N\\N")
          .append("Attendees: ");
        listToString(sb, event.getAttendees());
        sb.append("\\N\\N")
          .append("\n");
      }

      final String eventUid = String.valueOf(event.hashCode());
      sb.append("UID:").append(eventUid).append("\n");

      sb.append("BEGIN:VALARM\n")
        .append("ACTION:AUDIO\n")
        .append("X-WR-ALARMUID:").append(eventUid).append("ALARM\n")
        .append("ATTACH;VALUE=URI:Basso\n")
        .append("TRIGGER:-PT10M\n")
        .append("END:VALARM\n")

        .append("END:VEVENT\n");
    }

    sb.append("END:VCALENDAR\n");

    return sb;
  }

  private static void listToString(final StringBuffer p_sb, final List<String> list) {
    if(list != null) {
      for(int i = 0; i < list.size(); i++) {
        if(i > 0) {
          p_sb.append(", ");
        }
        p_sb.append(list.get(i));
      }
    }
  }
}
