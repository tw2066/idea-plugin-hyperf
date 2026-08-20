package com.base.idea.hyperf.console;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.base.idea.hyperf.HyperfStartupActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Hyperf 菜单命令 Action 基类。
 *
 * <p>子类实现 {@link #buildCommand} 返回 hyperf.php 的参数片段
 * （如 {@code "gen:controller Foo"}），返回 null 表示放弃执行（用户取消输入）。
 * 未启用插件的项目中菜单项整体隐藏。
 */
public abstract class AbstractHyperfCommandAction extends AnAction implements DumbAware {

    @Nullable
    protected abstract String buildCommand(@NotNull Project project, @NotNull AnActionEvent e);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || !HyperfStartupActivity.isEnabled(project)) {
            return;
        }
        String command = buildCommand(project, e);
        if (command == null) {
            return;
        }
        HyperfConsoleRunner.runInTerminal(project, command);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(HyperfActionGroup.isMenuVisible(e.getProject()));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
