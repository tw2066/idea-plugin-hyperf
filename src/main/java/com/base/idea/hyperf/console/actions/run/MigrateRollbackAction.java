package com.base.idea.hyperf.console.actions.run;

import org.jetbrains.annotations.NotNull;

/** migrate:rollback — 回滚上一次迁移 */
public class MigrateRollbackAction extends FixedCommandAction {
    @Override
    protected @NotNull String commandName() {
        return "migrate:rollback";
    }
}
