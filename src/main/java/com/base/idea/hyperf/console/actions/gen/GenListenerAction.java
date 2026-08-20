package com.base.idea.hyperf.console.actions.gen;

import org.jetbrains.annotations.NotNull;

/** gen:listener — 生成事件监听器 */
public class GenListenerAction extends AbstractGenAction {
    @Override
    protected @NotNull String genCommand() {
        return "gen:listener";
    }

    @Override
    protected @NotNull String inputTitle() {
        return "New Listener";
    }
}
