package com.base.idea.hyperf.console.actions.gen;

import org.jetbrains.annotations.NotNull;

/** gen:migration — 生成数据库迁移文件（database 包提供，非 devtool 但签名同为必填 name） */
public class GenMigrationAction extends AbstractGenAction {
    @Override
    protected @NotNull String genCommand() {
        return "gen:migration";
    }

    @Override
    protected @NotNull String inputTitle() {
        return "New Migration";
    }
}
