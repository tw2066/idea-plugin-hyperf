package com.base.idea.hyperf.console.actions.run;

import org.jetbrains.annotations.NotNull;

/** server:watch — 文件变更热重启（需 hyperf/watcher） */
public class ServerWatchAction extends FixedCommandAction {
    @Override
    protected @NotNull String commandName() {
        return "server:watch";
    }
}
