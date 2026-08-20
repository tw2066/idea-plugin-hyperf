package com.base.idea.hyperf.console.actions.gen;

import org.jetbrains.annotations.NotNull;

/** gen:aspect — 生成 AOP 切面 */
public class GenAspectAction extends AbstractGenAction {
    @Override
    protected @NotNull String genCommand() {
        return "gen:aspect";
    }

    @Override
    protected @NotNull String inputTitle() {
        return "New Aspect";
    }
}
