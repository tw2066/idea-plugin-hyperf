package com.base.idea.hyperf.console.actions.gen;

import org.jetbrains.annotations.NotNull;

/** gen:middleware — 生成中间件 */
public class GenMiddlewareAction extends AbstractGenAction {
    @Override
    protected @NotNull String genCommand() {
        return "gen:middleware";
    }

    @Override
    protected @NotNull String inputTitle() {
        return "New Middleware";
    }
}
