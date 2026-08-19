package com.base.idea.hyperf.console.actions.run;

import org.jetbrains.annotations.NotNull;

/** migrate — 执行数据库迁移 */
public class MigrateAction extends FixedCommandAction {
    @Override
    protected @NotNull String commandName() {
        return "migrate";
    }
}
