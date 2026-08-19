package com.base.idea.hyperf.console.actions.run;

import org.jetbrains.annotations.NotNull;

/** describe:routes — 列出全部路由 */
public class DescribeRoutesAction extends FixedCommandAction {
    @Override
    protected @NotNull String commandName() {
        return "describe:routes";
    }
}
