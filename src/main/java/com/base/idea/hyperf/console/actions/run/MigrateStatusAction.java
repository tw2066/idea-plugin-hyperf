package com.base.idea.hyperf.console.actions.run;

import org.jetbrains.annotations.NotNull;

/** migrate:status — 查看迁移状态 */
public class MigrateStatusAction extends FixedCommandAction {
    @Override
    protected @NotNull String commandName() {
        return "migrate:status";
    }
}
