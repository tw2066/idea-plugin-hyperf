package com.base.idea.hyperf.console.actions.gen;

import org.jetbrains.annotations.NotNull;

/** gen:class — 生成普通类 */
public class GenClassAction extends AbstractGenAction {
    @Override
    protected @NotNull String genCommand() {
        return "gen:class";
    }

    @Override
    protected @NotNull String inputTitle() {
        return "New Class";
    }
}
