package com.base.idea.hyperf.console.actions.run;

import org.jetbrains.annotations.NotNull;

/** queue:flush — 清空异步队列失败消息（需 hyperf/async-queue） */
public class QueueFlushAction extends FixedCommandAction {
    @Override
    protected @NotNull String commandName() {
        return "queue:flush";
    }
}
