package com.base.idea.hyperf.console.actions.run;

import org.jetbrains.annotations.NotNull;

/** describe:listeners — 列出事件与监听器 */
public class DescribeListenersAction extends FixedCommandAction {
    @Override
    protected @NotNull String commandName() {
        return "describe:listeners";
    }
}
