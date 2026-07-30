package com.naixiaoxin.idea.hyperf.util;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.naixiaoxin.idea.hyperf.HyperfSettings;
import com.naixiaoxin.idea.hyperf.ui.HyperfProjectSettingsForm;
import org.jetbrains.annotations.NotNull;

/**
 * IDE 通知气泡工具。
 *
 * <p>在项目启动时弹出 "是否启用 Hyperf 插件" 的气球通知，
 * 提供 "立即启用" / "打开设置" / "不再提示" 三个操作链接。
 * 通知组 "Hyperf Plugin" 在 plugin.xml 中注册。
 *
 * @author NaiXiaoXin(SeanWang) <i@naixiaoxin.com>
 */
public class IdeHelper {

    /** 通知组 ID，需与 plugin.xml 中的 notificationGroup 一致 */
    private static final String NOTIFICATION_GROUP_ID = "Hyperf Plugin";

    /**
     * 弹出启用插件的提示气泡。
     * 仅在插件未启用且用户未选择 "不再提示" 时由启动活动调用。
     */
    public static void notifyEnableMessage(final Project project) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(
                        "Hyperf Plugin",
                        "Enable the Hyperf Plugin <a href=\"enable\">with auto configuration now</a>, open <a href=\"config\">Project Settings</a> or <a href=\"dismiss\">dismiss</a> further messages",
                        NotificationType.INFORMATION
                )
                // 处理气泡中的链接点击
                .setListener((notification1, event) -> {
                    if ("config".equals(event.getDescription())) {
                        // 打开插件设置页
                        HyperfProjectSettingsForm.show(project);
                    } else if ("enable".equals(event.getDescription())) {
                        // 直接启用并提示
                        enablePluginAndConfigure(project);
                        NotificationGroupManager.getInstance()
                                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                                .createNotification("Hyperf Plugin", "Plugin enabled", NotificationType.INFORMATION)
                                .notify(project);
                    } else if ("dismiss".equals(event.getDescription())) {
                        // 记住用户选择，以后不再提示
                        HyperfSettings.getInstance(project).dismissEnableNotification = true;
                    }
                    notification1.expire();
                })
                .notify(project);
    }

    /** 将当前项目的 pluginEnabled 置为 true */
    private static void enablePluginAndConfigure(@NotNull Project project) {
        HyperfSettings.getInstance(project).pluginEnabled = true;
    }
}
