package com.base.idea.hyperf.console.actions.run;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.base.idea.hyperf.console.AbstractHyperfCommandAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 无参快捷命令基类：子类只需返回命令名，如 "describe:routes" */
public abstract class FixedCommandAction extends AbstractHyperfCommandAction {

    protected abstract @NotNull String commandName();

    @Override
    protected @Nullable String buildCommand(@NotNull Project project, @NotNull AnActionEvent e) {
        return commandName();
    }
}
