package com.base.idea.hyperf;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.base.idea.hyperf.util.IdeHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 项目启动钩子（替代旧版 ProjectComponent，后者已从平台移除）。
 *
 * <p>职责：
 * <ul>
 *   <li>项目打开时检测是否为 Hyperf 项目（含 vendor/hyperf 与 app 目录），
 *       若插件未启用则弹出"启用插件"通知；</li>
 *   <li>提供静态 {@code isEnabled(...)} 判断，所有补全/跳转功能在执行前
 *       都会先调用它，未启用则整体短路。</li>
 * </ul>
 */
public class HyperfStartupActivity implements StartupActivity.DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        notifyPluginEnableDialog(project);
    }

    /** 当前项目是否已启用插件 */
    public static boolean isEnabled(Project project) {
        return HyperfSettings.getInstance(project).pluginEnabled;
    }

    /** 根据 PSI 元素所在项目判断是否已启用插件 */
    public static boolean isEnabled(@Nullable PsiElement psiElement) {
        return psiElement != null && isEnabled(psiElement.getProject());
    }

    /**
     * 索引阶段判断插件是否可用。
     *
     * <p>除显式启用外，若项目根目录存在 vendor/hyperf 也视为 Hyperf 项目并启用索引，
     * 避免用户未点启用时索引完全为空。
     */
    public static boolean isEnabledForIndex(@Nullable Project project) {
        if (project == null) {
            return false;
        }

        if (isEnabled(project)) {
            return true;
        }

        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return false;
        }
        return VfsUtil.findRelativeFile(baseDir, "vendor", "hyperf") != null;
    }

    /** 若为 Hyperf 项目且插件未启用、用户未关闭提示，则弹出"启用插件"通知 */
    private void notifyPluginEnableDialog(Project project) {
        if (isEnabled(project) || HyperfSettings.getInstance(project).dismissEnableNotification) {
            return;
        }
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return;
        }
        if (VfsUtil.findRelativeFile(baseDir, "app") != null
                && VfsUtil.findRelativeFile(baseDir, "vendor", "hyperf") != null) {
            IdeHelper.notifyEnableMessage(project);
        }
    }
}
