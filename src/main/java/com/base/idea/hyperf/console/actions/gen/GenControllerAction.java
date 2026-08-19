package com.base.idea.hyperf.console.actions.gen;

import org.jetbrains.annotations.NotNull;

/** gen:controller — 生成控制器（默认命名空间 App\Controller） */
public class GenControllerAction extends AbstractGenAction {
    @Override
    protected @NotNull String genCommand() {
        return "gen:controller";
    }

    @Override
    protected @NotNull String inputTitle() {
        return "New Controller";
    }
}
