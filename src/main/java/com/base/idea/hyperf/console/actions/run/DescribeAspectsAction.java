package com.base.idea.hyperf.console.actions.run;

import org.jetbrains.annotations.NotNull;

/** describe:aspects — 列出切面 */
public class DescribeAspectsAction extends FixedCommandAction {
    @Override
    protected @NotNull String commandName() {
        return "describe:aspects";
    }
}
