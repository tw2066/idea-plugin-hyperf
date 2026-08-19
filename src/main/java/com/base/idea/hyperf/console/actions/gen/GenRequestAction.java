package com.base.idea.hyperf.console.actions.gen;

import org.jetbrains.annotations.NotNull;

/** gen:request — 生成表单验证请求类 */
public class GenRequestAction extends AbstractGenAction {
    @Override
    protected @NotNull String genCommand() {
        return "gen:request";
    }

    @Override
    protected @NotNull String inputTitle() {
        return "New Request";
    }
}
