package org.galbraiths.groupwise.calendar;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CalendarEvent {
  private Date m_eventStart;
  private Date m_eventStop;
  private String m_description;
  private String m_location;
  private final List<String> m_attendees;

  public CalendarEvent() {
    m_attendees = new ArrayList<String>();
  }

  public Date getEventStart() {
    return m_eventStart;
  }

  public void setEventStart(final Date eventStart) {
    this.m_eventStart = eventStart;
  }

  public Date getEventStop() {
    return m_eventStop;
  }

  public void setEventStop(final Date eventStop) {
    this.m_eventStop = eventStop;
  }

  public String getDescription() {
    return m_description;
  }

  public void setDescription(final String description) {
    this.m_description = description;
  }

  public String getLocation() {
    return m_location;
  }

  public void setLocation(final String location) {
    this.m_location = location;
  }

  public List<String> getAttendees() {
    return m_attendees;
  }
}
