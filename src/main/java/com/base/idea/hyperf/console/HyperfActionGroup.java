package com.base.idea.hyperf.console;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.base.idea.hyperf.HyperfSettings;
import com.base.idea.hyperf.HyperfStartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Hyperf 菜单分组：仅在插件已启用且菜单开关打开的项目中可见，
 * 避免未启用项目的主菜单栏残留一个空的 Hyperf 菜单。
 */
public class HyperfActionGroup extends DefaultActionGroup implements DumbAware {

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(isMenuVisible(e.getProject()));
    }

    /** 菜单整体可见性：插件启用 + 菜单开关打开（Action 的 update 也用同一判断） */
    public static boolean isMenuVisible(Project project) {
        return project != null
                && HyperfStartupActivity.isEnabled(project)
                && HyperfSettings.getInstance(project).menuEnabled;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
