package utilities;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility for accessing Mailinator private-domain inboxes via direct Mailinator API calls.
 */
public final class MailinatorAPIUtils {


    // API token for Mailinator requests (must be set via initialize()).
    private static String apiToken="2255b07d83874f3f8eb5701088224b8b";
    // Reusable HTTP client for making API calls.
    private static HttpClient httpClient;

    // Prevent instantiation.
    private MailinatorAPIUtils() { }

    /** Must be called once before any other methods, with your Mailinator API token. */
    public static void initialize(String apiTokenValue) {
        if (apiTokenValue == null || apiTokenValue.isEmpty()) {
            throw new IllegalArgumentException("API token must not be null or empty");
        }
        apiToken = apiTokenValue;
        // Initialize the HTTP client (can customize with timeouts if needed).
        httpClient = HttpClient.newHttpClient();
    }

    /** Ensure the utility has been initialized with an API token. */
    private static void ensureInitialized() {
        if (apiToken == null) {
            throw new IllegalStateException(
                    "MailinatorAPIUtils not initialized. Call initialize(apiToken) first."
            );
        }
    }

    /**
     * Sort order for inbox fetching (ascending or descending by date).
     */
    public enum Sort {
        ASC("ascending"),
        DESC("descending");
        private final String apiValue;
        Sort(String apiValue) {
            this.apiValue = apiValue;
        }
        public String getApiValue() {
            return apiValue;
        }
    }

    /**
     * Internal representation of a Mailinator message summary.
     */
    public static final class Message {
        private String id;
        private String subject;
        private String from;
        private String origfrom;
        private String to;
        private String domain;
        private long time;
        private long seconds_ago;
        private String from_email_id; // NEW: added for future use

        // Getters for message fields:
        public String getId()       { return id; }
        public String getSubject()  { return subject; }
        public String getFrom()     { return from; }
        public String getOrigFrom() { return origfrom; }
        public String getTo()       { return to; }
        public String getDomain()   { return domain; }
        /** Returns the message timestamp (epoch millis). */
        public long getCreatedAt()  { return time; }
        public long getSecondsAgo() { return seconds_ago; }

        /** NEW: allow overriding the `from` field */
        public void setFrom(String from) {
            this.from = from;
        }

        public void setFromEmailId(){
            this.from_email_id = getFromEmailId();
        }

        // Set email ID from the origfrom field
        public String getFromEmailId() {
            if (from_email_id == null) {
                // Extract email ID from origfrom field, if not already set
                Pattern emailPattern = Pattern.compile("<([^>]+)>");
                Matcher matcher = emailPattern.matcher(origfrom);
                if (matcher.find()) {
                    from_email_id = matcher.group(1); // Extracted email ID
                } else {
                    from_email_id = origfrom; // Fallback to origfrom if no brackets found
                }
            }
            return from_email_id;
        }


    }

    /**
     * Core method to fetch messages from a Mailinator inbox via API.
     * @param domain  your private domain (e.g. "acme.mailinator.com", or "private" for all private domains)
     * @param inbox   inbox name (e.g. "signup", or "*" for all inboxes in the domain)
     * @param limit   max messages to return (Mailinator API default is 50)
     * @param skip    number of messages to skip (for paging)
     * @param sort    sort order (Sort.ASC for oldest first, Sort.DESC for newest first)
     * @return List of Message summaries in the inbox (could be empty if none or on error)
     */
    private static List<Message> fetchInboxMessagesInternal(
            String domain, String inbox, int limit, int skip, Sort sort) throws IOException, InterruptedException {
        ensureInitialized();
        String url = String.format(
                "https://api.mailinator.com/v2/domains/%s/inboxes/%s?limit=%d&skip=%d&sort=%s&token=%s",
                domain, inbox, limit, skip, sort.getApiValue(), apiToken
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();
        // Send the request and get response as a String.
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            // On non-OK HTTP response, return empty list (could throw instead).
            return Collections.emptyList();
        }
        String responseBody = response.body();
        // Parse JSON to extract message summaries.
        JSONObject json = new JSONObject(responseBody);
        JSONArray msgsArray = json.optJSONArray("msgs");
        if (msgsArray == null) {
            return Collections.emptyList();
        }
        List<Message> result = new ArrayList<>();
        for (int i = 0; i < msgsArray.length(); i++) {
            JSONObject msgJson = msgsArray.getJSONObject(i);
            Message msg = new Message();
            msg.id      = msgJson.optString("id", null);
            msg.subject = msgJson.optString("subject", null);
            msg.from    = msgJson.optString("from", null);
            msg.origfrom    = msgJson.optString("origfrom", null);
            msg.to      = msgJson.optString("to", null);
            msg.domain  = msgJson.optString("domain", null);
            msg.time    = msgJson.optLong("time", 0);
            msg.seconds_ago    = msgJson.optLong("seconds_ago", 0);
            // Set from_email_id based on origfrom field
            msg.from_email_id = msg.getFromEmailId();
            result.add(msg);
        }
        return result;
    }

    /** Fetch up to {@code limit} messages from the inbox, newest first by default. */
    public static List<Message> fetchInboxMessages(String domain, String inbox, int limit) throws IOException, InterruptedException {
        return fetchInboxMessagesInternal(domain, inbox, limit, 0, Sort.DESC);
    }

    /** Fetch up to 50 messages from the inbox (default limit), newest first. */
    public static List<Message> fetchInboxMessages(String domain, String inbox) throws IOException, InterruptedException {
        return fetchInboxMessagesInternal(domain, inbox, 50, 0, Sort.DESC);
    }

    /**
     * Fetches the full JSON for a single message, including all headers.
     */
    public static JSONObject fetchMessageDetails(String domain,
                                                 String inbox,
                                                 String messageId)
            throws IOException, InterruptedException {

        String url = String.format(
                "https://api.mailinator.com/v2/domains/%s/inboxes/%s/messages/%s?token=%s",
                domain, inbox, messageId, apiToken
        );

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return new JSONObject(resp.body());
    }


    /**
     * Fetch messages from a given sender email, up to {@code limit}, in the specified inbox.
     * (Case-insensitive match on the sender's email string.)
     */
    public static List<Message> fetchMessagesFromSenderName(String domain, String inbox,
                                                            String senderEmail, int limit) throws IOException, InterruptedException {
        return fetchInboxMessagesInternal(domain, inbox, limit, 0, Sort.DESC)
                .stream()
                .filter(m -> m.getFrom() != null
                        && senderEmail != null
                        && senderEmail.equalsIgnoreCase(m.getFrom()))
                .collect(Collectors.toList());
    }

    public static List<Message> fetchMessagesFromSender(String domain,
                                                        String inbox,
                                                        String senderEmail,
                                                        int limit)
            throws IOException, InterruptedException {
        List<Message> summaries = fetchInboxMessagesInternal(domain, inbox, limit, 0, Sort.DESC);
        Pattern emailInBrackets = Pattern.compile("<([^>]+)>");
        List<Message> result = new ArrayList<>();

        for (Message summary : summaries) {
            JSONObject details = fetchMessageDetails(domain, inbox, summary.getId());
            JSONArray fromArr = details
                    .getJSONObject("headers")
                    .getJSONArray("from");
            String rawFrom = fromArr.length() > 0
                    ? fromArr.getString(0)
                    : details.optString("from", "");

            // extract email between < >
            Matcher m = emailInBrackets.matcher(rawFrom);
            String email = m.find() ? m.group(1) : rawFrom;

            if (senderEmail.equalsIgnoreCase(email)) {
                // optionally overwrite the summary's from field
                summary.setFrom(email);
                result.add(summary);
            }
        }
        return result;
    }


    /** Same as above, using default limit of 50 messages. */
    public static List<Message> fetchMessagesFromSender(String domain, String inbox,
                                                        String senderEmail) throws IOException, InterruptedException {
        return fetchMessagesFromSender(domain, inbox, senderEmail, 50);
    }

    /**
     * Fetch messages that arrived on or after the given timestamp ({@code sinceMillis}), up to {@code limit}.
     */
    public static List<Message> fetchInboxMessagesSince(String domain, String inbox,
                                                        long sinceMillis, int limit) throws IOException, InterruptedException {
        return fetchInboxMessagesInternal(domain, inbox, limit, 0, Sort.DESC)
                .stream()
                .filter(m -> {
                    // Keep messages with createdAt >= sinceMillis
                    return m.getCreatedAt() >= sinceMillis;
                })
                .collect(Collectors.toList());
    }

    /** Fetch messages from {@code sinceMillis} until now (default limit 50). */
    public static List<Message> fetchInboxMessagesSince(String domain, String inbox, long sinceMillis) throws IOException, InterruptedException {
        return fetchInboxMessagesSince(domain, inbox, sinceMillis, 50);
    }

    /**
     * Fetch messages from a given sender that arrived within the last {@code minutesAgo} minutes.
     */
    public static List<Message> fetchRecentMessagesFromSender(String domain, String inbox,
                                                              String senderEmail, long minutesAgo) throws IOException, InterruptedException {
        long cutoff = Instant.now().minus(Duration.ofMinutes(minutesAgo)).toEpochMilli();
        return fetchMessagesFromSender(domain, inbox, senderEmail, 50).stream()
                .filter(m -> m.getCreatedAt() >= cutoff)
                .collect(Collectors.toList());
    }
}
