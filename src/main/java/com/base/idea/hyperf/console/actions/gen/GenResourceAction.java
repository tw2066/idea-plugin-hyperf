package com.base.idea.hyperf.console.actions.gen;

import org.jetbrains.annotations.NotNull;

/** gen:resource — 生成 API Resource */
public class GenResourceAction extends AbstractGenAction {
    @Override
    protected @NotNull String genCommand() {
        return "gen:resource";
    }

    @Override
    protected @NotNull String inputTitle() {
        return "New Resource";
    }
}
