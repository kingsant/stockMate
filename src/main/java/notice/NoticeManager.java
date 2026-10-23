package notice;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;

public class NoticeManager {

    private static String groupId = "uka Notification Group";

    public static void notify(String title, String content) {
        Notification notification = new Notification(groupId, title, content, NotificationType.INFORMATION);
        Notifications.Bus.notify(notification);
    }


}
