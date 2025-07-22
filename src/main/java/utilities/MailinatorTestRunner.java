package utilities;

import java.io.IOException;
import java.util.List;

public class MailinatorTestRunner {

    public static void main(String[] args) throws IOException, InterruptedException {

        // Initialize the Mailinator API utility with your API token
        MailinatorAPIUtils.initialize("2255b07d83874f3f8eb5701088224b8b"); // Replace with your actual API token

        // ✅ Replace with your actual private domain and inbox name
        String domain = "team308324.testinator.com";  // e.g. "acme.mailinator.com"
        String inbox = "test";                   // e.g. "signup" or "qa-tests"
        String sender = "suraj.j.paul@gmail.com";        // email you want to filter by

        System.out.println("=== Fetching latest 5 messages ===");
        List<MailinatorAPIUtils.Message> latestMessages =
                MailinatorAPIUtils.fetchInboxMessages(domain, inbox, 5);
        printMessages(latestMessages);

        System.out.println("\n=== Fetching messages from sender: " + sender + " ===");
        List<MailinatorAPIUtils.Message> fromSender =
                MailinatorAPIUtils.fetchMessagesFromSender(domain, inbox, sender, 10);
        printMessages(fromSender);

        System.out.println("\n=== Fetching messages from last 2 minutes ===");
        long twoMinutesAgo = System.currentTimeMillis() - (2 * 60 * 1000);
        List<MailinatorAPIUtils.Message> recent =
                MailinatorAPIUtils.fetchInboxMessagesSince(domain, inbox, twoMinutesAgo);
        printMessages(recent);

        System.out.println("\n=== Fetching recent messages from sender in last 1 minute ===");
        List<MailinatorAPIUtils.Message> recentFromSender =
                MailinatorAPIUtils.fetchRecentMessagesFromSender(domain, inbox, sender, 1);
        printMessages(recentFromSender);
    }

    private static void printMessages(List<MailinatorAPIUtils.Message> messages) {
        if (messages.isEmpty()) {
            System.out.println("No messages found.");
            return;
        }
        for (MailinatorAPIUtils.Message msg : messages) {
            System.out.printf(
                    "ID: %s\nFrom: %s\nSubject: %s\nTime: %d\n---\n",
                    msg.getId(), msg.getFrom(), msg.getSubject(), msg.getCreatedAt()
            );
        }
    }
}
