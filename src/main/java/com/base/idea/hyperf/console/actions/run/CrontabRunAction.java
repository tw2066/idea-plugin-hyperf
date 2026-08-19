package com.base.idea.hyperf.console.actions.run;

import org.jetbrains.annotations.NotNull;

/** crontab:run — 手动触发执行所有到期定时任务 */
public class CrontabRunAction extends FixedCommandAction {
    @Override
    protected @NotNull String commandName() {
        return "crontab:run";
    }
}
