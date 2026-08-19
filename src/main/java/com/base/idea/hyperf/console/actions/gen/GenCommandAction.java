package com.base.idea.hyperf.console.actions.gen;

import org.jetbrains.annotations.NotNull;

/** gen:command — 生成命令行命令类 */
public class GenCommandAction extends AbstractGenAction {
    @Override
    protected @NotNull String genCommand() {
        return "gen:command";
    }

    @Override
    protected @NotNull String inputTitle() {
        return "New Command";
    }
}
