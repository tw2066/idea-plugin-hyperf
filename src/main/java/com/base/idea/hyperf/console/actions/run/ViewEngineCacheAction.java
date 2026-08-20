package com.base.idea.hyperf.console.actions.run;

import org.jetbrains.annotations.NotNull;

/** gen:view-engine-cache — 预编译视图模板缓存（需 hyperf/view-engine） */
public class ViewEngineCacheAction extends FixedCommandAction {
    @Override
    protected @NotNull String commandName() {
        return "gen:view-engine-cache";
    }
}
