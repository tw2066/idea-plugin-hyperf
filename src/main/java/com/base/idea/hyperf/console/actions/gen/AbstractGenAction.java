package com.base.idea.hyperf.console.actions.gen;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.base.idea.hyperf.console.AbstractHyperfCommandAction;
import com.base.idea.hyperf.util.IdeHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * devtool gen:* 命令基类：先弹输入框收集类名（gen 命令的 name 参数是必填且无交互提问），
 * 再拼装 {@code gen:xxx <Name>} 交给终端执行。
 */
public abstract class AbstractGenAction extends AbstractHyperfCommandAction {

    /** gen 命令名，如 "gen:controller" */
    protected abstract @NotNull String genCommand();

    /** 输入框标题，如 "New Controller" */
    protected abstract @NotNull String inputTitle();

    /** 类名/表名合法性校验（防止注入 shell 元字符）；默认允许 PHP 类名与命名空间分隔符 */
    protected boolean isValidName(@NotNull String name) {
        return name.matches("[A-Za-z0-9_\\\\]+");
    }

    @Override
    protected @Nullable String buildCommand(@NotNull Project project, @NotNull AnActionEvent e) {
        String name = Messages.showInputDialog(project, "Class name:", inputTitle(), null);
        if (name == null) {
            return null;
        }
        name = name.trim();
        if (StringUtil.isEmptyOrSpaces(name)) {
            return null;
        }
        if (!isValidName(name)) {
            IdeHelper.notifyWarning(project, "Invalid name: " + name);
            return null;
        }
        return genCommand() + " " + name;
    }
}
