package com.base.idea.hyperf.console.actions.gen;

import org.jetbrains.annotations.NotNull;

/** gen:seeder — 生成 Seeder */
public class GenSeederAction extends AbstractGenAction {
    @Override
    protected @NotNull String genCommand() {
        return "gen:seeder";
    }

    @Override
    protected @NotNull String inputTitle() {
        return "New Seeder";
    }
}
