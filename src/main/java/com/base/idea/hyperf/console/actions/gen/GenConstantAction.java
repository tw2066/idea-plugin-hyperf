package com.base.idea.hyperf.console.actions.gen;

import org.jetbrains.annotations.NotNull;

/** gen:constant — 生成常量类（--type 可选 const/enum，终端中可自补） */
public class GenConstantAction extends AbstractGenAction {
    @Override
    protected @NotNull String genCommand() {
        return "gen:constant";
    }

    @Override
    protected @NotNull String inputTitle() {
        return "New Constant";
    }
}
