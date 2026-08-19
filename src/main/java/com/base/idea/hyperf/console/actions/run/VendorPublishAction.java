package com.base.idea.hyperf.console.actions.run;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.base.idea.hyperf.console.AbstractHyperfCommandAction;
import com.base.idea.hyperf.util.IdeHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** vendor:publish — 发布包配置；package 为必填参数，弹输入框收集 */
public class VendorPublishAction extends AbstractHyperfCommandAction {

    @Override
    protected @Nullable String buildCommand(@NotNull Project project, @NotNull AnActionEvent e) {
        String pkg = Messages.showInputDialog(project, "Package name (e.g. hyperf/amqp):", "Vendor Publish", null);
        if (pkg == null) {
            return null;
        }
        pkg = pkg.trim();
        if (StringUtil.isEmptyOrSpaces(pkg)) {
            return null;
        }
        if (!pkg.matches("[A-Za-z0-9_./-]+")) {
            IdeHelper.notifyWarning(project, "Invalid package name: " + pkg);
            return null;
        }
        return "vendor:publish " + pkg;
    }
}
