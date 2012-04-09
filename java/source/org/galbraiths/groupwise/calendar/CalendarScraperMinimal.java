package org.galbraiths.groupwise.calendar;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.log4j.Logger;
import org.galbraiths.groupwise.model.GroupwiseConfig;
import org.galbraiths.groupwise.util.StringUtils;
import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.Text;
import org.htmlparser.filters.TagNameFilter;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.NodeIterator;
import org.htmlparser.util.NodeList;

/**
 * Note this class is not thread safe due to the way it caches DateFormat instances.
 *
 * @author zbedell
 */
public class CalendarScraperMinimal {
  private static Logger logger = Logger.getLogger(CalendarScraperMinimal.class);
  private final GroupwiseConfig m_config;

  private String m_userContext;
  private final HttpClient m_client;

  private final DateFormat m_gwDateFormat = new SimpleDateFormat("MMMM d, yyyy");
  private final DateFormat m_gwTimeFormat = new SimpleDateFormat("h:mm a");

  public CalendarScraperMinimal(final GroupwiseConfig p_config) {
    m_config = p_config;

    m_client = new HttpClient();
    m_client.getParams().setParameter("http.protocol.single-cookie-header", true);

    if(StringUtils.notNullOrEmpty(m_config.getProxyHost())) {
      final HostConfiguration conf = m_client.getHostConfiguration();
      conf.setProxy(m_config.getProxyHost(), m_config.getProxyPort());
    }
  }

  /**
   * Converts the GroupWise 7 (and earlier?) *simple* web interface into a series of
   * {@link org.galbraiths.groupwise.calendar.CalendarEvent}s.
   *
   * @param baseUrl
   *          the base of all the requests, such as <code>https://web.mydomain.com/</code>. The trailing slash is required.
   * @param username
   * @param password
   * @return
   * @throws Exception
   */
  public List<CalendarEvent> getCalendarEvents(final int p_months) throws Exception {
    final List<CalendarEvent> calendarEvents = new ArrayList<CalendarEvent>();

    getUserContext();
    authenticateUser();

    final Calendar cal = Calendar.getInstance();

    final int split = 12 * 5;
    final int splitMonths;
    if(p_months < split) {
      splitMonths = p_months / 2;
    } else {
      splitMonths = p_months - split;
    }

    cal.add(Calendar.MONTH, -splitMonths);

    // check all this stuff against the calendar javadoc
    for(int i = 0; i < p_months; i++) {
      final int year = cal.get(Calendar.YEAR);
      final int month = (cal.get(Calendar.MONTH) + 1); // Calendar is 0-based for some odd reason
      logger.info(String.format("Scraping %d/%d...", month, year));
      final List<CalendarEvent> events = getEventLinks(month, year);
      calendarEvents.addAll(events);
      cal.add(Calendar.MONTH, 1);
    }

    return calendarEvents;
  }

  private static void processInvalidResponse(final int p_response, final HttpMethod p_request) throws Exception {
    final String errorMessage = "An invalid response code was returned: " + p_response;
    logger.error(errorMessage);
    logger.error("Request body: " + p_request.getResponseBodyAsString());
    throw new Exception(errorMessage);
  }

  private static String getInputValue(final String p_page, final String p_inputName) {
    int location = 0;
    final String tempPage = p_page.toLowerCase();
    while((location = (tempPage.indexOf("input", location))) != -1) {
      location++;
      final String name = getAttributeValue(p_page, location, "name");
      if(name == null) {
        continue;
      }

      if(name.equals(p_inputName)) {
        final String value = getAttributeValue(p_page, location, "value");
        return value;
      }
    }
    return null;
  }

  private static String getAttributeValue(final String p_page, final int p_inputStart, final String p_attributeName) {
    final String tempPage = p_page.toLowerCase();

    final int value = tempPage.indexOf(p_attributeName + "=", p_inputStart);
    if(value == -1) {
      return null;
    }

    final int valueQuote = value + (p_attributeName + "=").length();
    final char c = tempPage.charAt(valueQuote);
    if((c != '\'') && (c != '"')) {
      return null;
    }

    final int finalQuote = tempPage.indexOf(String.valueOf(c), valueQuote + 1);
    if(finalQuote == -1) {
      return null;
    }

    return p_page.substring(valueQuote + 1, finalQuote);
  }

  private void getUserContext() throws Exception {
    // get the sign-in web page. This is required to obtain some sort of unique session identifier, called the
    // "User.context"
    final GetMethod get = new GetMethod(m_config.getUrl() + "/gw/webacc?User.interface=simple");
    final int response = m_client.executeMethod(get);
    if(response != 200) {
      processInvalidResponse(response, get);
    }
    final String responseBody = get.getResponseBodyAsString();
    m_userContext = getInputValue(responseBody, "User.context");
    if(StringUtils.nullOrEmpty(m_userContext)) {
      throw new Exception("No User.context value found");
    }
  }

  private void authenticateUser() throws Exception {
    final PostMethod post = new PostMethod(m_config.getUrl() + "/gw/webacc");
    final NameValuePair[] pairs = new NameValuePair[] {
        new NameValuePair("User.id", m_config.getUsername()),
        new NameValuePair("User.password", m_config.getPassword()),
        new NameValuePair("User.interface", "simple"),
        new NameValuePair("User.context", m_userContext),
        new NameValuePair("error", "login"),
        new NameValuePair("merge", "main"),
        new NameValuePair("action", "User.Login"),
        new NameValuePair("Url.displayDraftItems", "1")
    };
    post.setRequestBody(pairs);

    final int response = m_client.executeMethod(post);
    if(response != 200) {
      processInvalidResponse(response, post);
    }

//    @SuppressWarnings("unused")
//    final String responseBody = post.getResponseBodyAsString();
  }

  @SuppressWarnings("deprecation")
  private List<CalendarEvent> getEventLinks(final int month, final int year) throws Exception {
    final Calendar calendar = Calendar.getInstance();
    calendar.set(year, month - 1, 1);
    calendar.add(Calendar.DAY_OF_YEAR, -1);
    final Date date = calendar.getTime();
    final long time = date.getTime();
    GetMethod get = new GetMethod(m_config.getUrl() + "/gw/webacc?User.context=" + m_userContext
        + "&action=Calendar.Search&Calendar.startDate=" + time + "&Calendar.durationType=Month&merge=calendar");

    int response = m_client.executeMethod(get);
    if(response != 200) {
      processInvalidResponse(response, get);
    }

    final String responseBody = get.getResponseBodyAsString();
    final Node[] links = Parser.createParser(responseBody, null).extractAllNodesThatAre(LinkTag.class);
    final List<CalendarEvent> events = new ArrayList<CalendarEvent>(links.length);
    final Set<String> eventURLs = new HashSet<String>(links.length);
    for(final Node link2 : links) {
      final LinkTag link = (LinkTag) link2;
      final String href = link.getAttribute("href");
      if(href.indexOf("Item.Read") != -1) {
        final String url = link.getLink();
        if(eventURLs.contains(url)) {
          continue;
        }
        eventURLs.add(url);
        get = new GetMethod(m_config.getUrl() + url);
        response = m_client.executeMethod(get);
        if(response != 200) {
          processInvalidResponse(response, get);
        }

        String mode = null;
        final Map<String,String> values = new HashMap<String,String>();

        final Parser parser = Parser.createParser(get.getResponseBodyAsString(), null);
        final NodeIterator cells = parser.extractAllNodesThatMatch(new TagNameFilter("td")).elements();
        while(cells.hasMoreNodes()) {
          final Node cell = cells.nextNode();
          final NodeList list = cell.getChildren();
          if(list == null) {
            continue;
          }

          final NodeIterator it = list.elements();
          while(it.hasMoreNodes()) {
            final Node child = it.nextNode();
            if(child instanceof Text) {
              final String text = convertTextToString((Text) child);
              if(text.equals("Subject:")) {
                mode = text;
                continue;
              } else if(text.equals("Date:")) {
                mode = text;
                continue;
              } else if(text.equals("Time:")) {
                mode = text;
                continue;
              } else if(text.equals("To:")) {
                mode = text;
                continue;
              } else if(text.equals("Location:")) {
                mode = text;
                continue;
              }

              if(text.equals("")) {
                continue;
              }

              if(mode != null) {
                values.put(mode, text);
                mode = null;
              }
            }
          }
        }

        // build the calendar event and add it to the list
        final CalendarEvent event = new CalendarEvent();
        event.setLocation(values.get("Location:"));
        event.setDescription(values.get("Subject:"));

        Date eventDate = null;
        try {
          final String[] gwDate = values.get("Date:").split(" - ");
          eventDate = m_gwDateFormat.parse(gwDate[1]);
        } catch(final Exception e) {
          logger.error("Couldn't parse Date field", e);
          logger.error(printFields(values));
          continue;
        }
        Date startTime = null;
        Date endTime = null;
        try {
          final String sT = values.get("Time:");
          if(sT == null) {
            startTime = new Date(0, 0, 0, 0, 0, 0);
            endTime = new Date(0,0,0,23,59,59);
          } else {
            final String[] times = sT.split(" - ");
            startTime = m_gwTimeFormat.parse(times[0]);
            endTime = m_gwTimeFormat.parse(times[1]);
          }
        } catch(final Exception e) {
          logger.error("Couldn't parse Time field", e);
          logger.error(printFields(values));
          continue;
        }

        final Date eventStart = new Date(eventDate.getTime());
        final Date eventStop = new Date(eventDate.getTime());
        eventStart.setHours(startTime.getHours());
        eventStart.setMinutes(startTime.getMinutes());
        eventStop.setHours(endTime.getHours());
        eventStop.setMinutes(endTime.getMinutes());

        event.setEventStart(eventStart);
        event.setEventStop(eventStop);

        final String to = values.get("To:");
        if(to != null) {
          final String[] attendees = to.split(", ");
          for(final String attendee : attendees) {
            event.getAttendees().add(attendee);
          }
        }

        events.add(event);
      }
    }

    return events;
  }

  private String printFields(final Map<String, String> p_values) {
    final StringBuffer sb = new StringBuffer();
    final Iterator<String> it = p_values.keySet().iterator();
    while(it.hasNext()) {
      final String key = it.next();
      sb.append(key);
      sb.append(":");
      sb.append(p_values.get(key));
      if(it.hasNext()) {
        sb.append(", ");
      }
    }
    return sb.toString();
  }

  private String convertTextToString(final Text child) {
    String text = child.getText();
    text = text.replaceAll("\\&nbsp\\;", " ");
    return text.trim();
  }
}
