package com.naixiaoxin.idea.hyperf.util;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.naixiaoxin.idea.hyperf.HyperfSettings;
import com.naixiaoxin.idea.hyperf.ui.HyperfProjectSettingsForm;
import org.jetbrains.annotations.NotNull;

/**
 * @author NaiXiaoXin(SeanWang) <i@naixiaoxin.com>
 */
public class IdeHelper {

    private static final String NOTIFICATION_GROUP_ID = "Hyperf Plugin";

    public static void notifyEnableMessage(final Project project) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(
                        "Hyperf Plugin",
                        "Enable the Hyperf Plugin <a href=\"enable\">with auto configuration now</a>, open <a href=\"config\">Project Settings</a> or <a href=\"dismiss\">dismiss</a> further messages",
                        NotificationType.INFORMATION
                )
                .setListener((notification1, event) -> {
                    if ("config".equals(event.getDescription())) {
                        HyperfProjectSettingsForm.show(project);
                    } else if ("enable".equals(event.getDescription())) {
                        enablePluginAndConfigure(project);
                        NotificationGroupManager.getInstance()
                                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                                .createNotification("Hyperf Plugin", "Plugin enabled", NotificationType.INFORMATION)
                                .notify(project);
                    } else if ("dismiss".equals(event.getDescription())) {
                        HyperfSettings.getInstance(project).dismissEnableNotification = true;
                    }
                    notification1.expire();
                })
                .notify(project);
    }

    private static void enablePluginAndConfigure(@NotNull Project project) {
        HyperfSettings.getInstance(project).pluginEnabled = true;
    }
}
