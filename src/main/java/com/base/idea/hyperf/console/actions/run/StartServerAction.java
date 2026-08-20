package com.base.idea.hyperf.console.actions.run;

import org.jetbrains.annotations.NotNull;

/** start — 启动 Hyperf 服务器（终端内长跑，Ctrl+C 停止） */
public class StartServerAction extends FixedCommandAction {
    @Override
    protected @NotNull String commandName() {
        return "start";
    }
}
