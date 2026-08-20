package com.base.idea.hyperf.console.actions.gen;

import org.jetbrains.annotations.NotNull;

/** gen:job — 生成 Job */
public class GenJobAction extends AbstractGenAction {
    @Override
    protected @NotNull String genCommand() {
        return "gen:job";
    }

    @Override
    protected @NotNull String inputTitle() {
        return "New Job";
    }
}
