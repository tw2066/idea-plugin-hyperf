package com.base.idea.hyperf.console.actions.gen;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.base.idea.hyperf.console.AbstractHyperfCommandAction;
import com.base.idea.hyperf.util.IdeHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * gen:model — 按数据表生成模型。
 *
 * <p>与其他 gen 命令不同：table 参数可选（不传则为连接池下所有表生成），
 * 空输入不是取消而是"生成全部"，所以不用 AbstractGenAction 的必填逻辑。
 */
public class GenModelAction extends AbstractHyperfCommandAction {

    @Override
    protected @Nullable String buildCommand(@NotNull Project project, @NotNull AnActionEvent e) {
        String table = Messages.showInputDialog(project, "Table name (empty = all tables):", "New Model", null);
        if (table == null) {
            return null;
        }
        table = table.trim();
        if (table.isEmpty()) {
            return "gen:model";
        }
        if (!table.matches("[A-Za-z0-9_]+")) {
            IdeHelper.notifyWarning(project, "Invalid table name: " + table);
            return null;
        }
        return "gen:model " + table;
    }
}
