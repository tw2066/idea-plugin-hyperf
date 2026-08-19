package com.base.idea.hyperf.console.actions.gen;

import org.jetbrains.annotations.NotNull;

/** gen:process — 生成自定义进程 */
public class GenProcessAction extends AbstractGenAction {
    @Override
    protected @NotNull String genCommand() {
        return "gen:process";
    }

    @Override
    protected @NotNull String inputTitle() {
        return "New Process";
    }
}
