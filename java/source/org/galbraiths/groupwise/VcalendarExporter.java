package org.galbraiths.groupwise;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;


public class VcalendarExporter {
  public static String getVcalendar(final List<CalendarEvent> calendarEvents) {
    final DateFormat fullDate = new SimpleDateFormat("yyyyMMdd'T'HHmm'00'");

    final StringBuffer sb = new StringBuffer();

    sb.append("BEGIN:VCALENDAR\n");
    sb.append("VERSION:2.0\n");
    sb.append("PRODID:-//Ben Galbraith//Groupwise Scraper//EN\n");
    sb.append("CALSCALE:GREGORIAN\n");
    sb.append("METHOD:PUBLISH\n");
//    sb.append("BEGIN:VTIMEZONE\n");
//    sb.append("TZID:US/Mountain\n");
//    sb.append("LAST-MODIFIED:20050516T200736Z\n");
//    sb.append("BEGIN:DAYLIGHT\n");
//    sb.append("DTSTART:20040404T090000\n");
//    sb.append("TZOFFSETTO:-0600\n");
//    sb.append("TZOFFSETFROM:+0000\n");
//    sb.append("TZNAME:MDT\n");
//    sb.append("END:DAYLIGHT\n");
//    sb.append("BEGIN:STANDARD\n");
//    sb.append("DTSTART:20041031T020000\n");
//    sb.append("TZOFFSETTO:-0700\n");
//    sb.append("TZOFFSETFROM:-0600\n");
//    sb.append("TZNAME:MST\n");
//    sb.append("END:STANDARD\n");
//    sb.append("BEGIN:DAYLIGHT\n");
//    sb.append("DTSTART:20050403T010000\n");
//    sb.append("TZOFFSETTO:-0600\n");
//    sb.append("TZOFFSETFROM:-0700\n");
//    sb.append("TZNAME:MDT\n");
//    sb.append("END:DAYLIGHT\n");
//    sb.append("END:VTIMEZONE\n");
    // FIXME: Don't hard code this...
    sb.append("BEGIN:VTIMEZONE");
    sb.append("TZID:America/New_York");
    sb.append("BEGIN:DAYLIGHT");
    sb.append("TZOFFSETFROM:-0500");
    sb.append("RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=2SU");
    sb.append("DTSTART:20070311T020000");
    sb.append("TZNAME:EDT");
    sb.append("TZOFFSETTO:-0400");
    sb.append("END:DAYLIGHT");
    sb.append("BEGIN:STANDARD");
    sb.append("TZOFFSETFROM:-0400");
    sb.append("RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=1SU");
    sb.append("DTSTART:20071104T020000");
    sb.append("TZNAME:EST");
    sb.append("TZOFFSETTO:-0500");
    sb.append("END:STANDARD");
    sb.append("END:VTIMEZONE");


    for(int i = 0; i < calendarEvents.size(); i++) {
      final CalendarEvent event = calendarEvents.get(i);
      sb.append("BEGIN:VEVENT\n");

      sb.append("DTSTART;TZID=America/New_York:");
      sb.append(fullDate.format(event.getEventStart()));
      sb.append("\n");

      sb.append("DTEND;TZID=America/New_York:");
      sb.append(fullDate.format(event.getEventStop()));
      sb.append("\n");

      sb.append("SUMMARY:");
      sb.append(event.getDescription());
      sb.append("\n");

      if(StringUtils.notNullOrEmpty(event.getLocation())) {
        sb.append("DESCRIPTION:");
        sb.append("Location: " + event.getLocation() + "\\N\\N");
        sb.append("Attendees: " + listToString(event.getAttendees()) + "\\N\\N");

        sb.append("\n");
      }

      final String eventUid = String.valueOf(event.hashCode());
      sb.append("UID:" + eventUid + "\n");

      sb.append("BEGIN:VALARM\n");
      sb.append("ACTION:AUDIO\n");
      sb.append("X-WR-ALARMUID:" + eventUid + "ALARM\n");
      sb.append("ATTACH;VALUE=URI:Basso\n");
      sb.append("TRIGGER:-PT10M\n");
      sb.append("END:VALARM\n");

      sb.append("END:VEVENT\n");
    }

    sb.append("END:VCALENDAR\n");

    return sb.toString();
  }

  private static String listToString(final List<String> list) {
    if(list == null) {
      return "";
    }
    final StringBuffer sb = new StringBuffer();
    for(int i = 0; i < list.size(); i++) {
      if(i > 0) {
        sb.append(", ");
      }
      final String name = list.get(i);
      sb.append(name);
    }
    return sb.toString();
  }
}
