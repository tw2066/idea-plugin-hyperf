package com.base.idea.hyperf;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.psi.PsiElement;
import com.base.idea.hyperf.util.HyperfRootUtil;
import com.base.idea.hyperf.util.IdeHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 项目启动钩子（替代旧版 ProjectComponent，后者已从平台移除）。
 *
 * <p>职责：
 * <ul>
 *   <li>项目打开时检测是否为 Hyperf 项目（能定位到应用根：bin/hyperf.php 或 vendor/hyperf，含子目录场景），
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
     * <p>除显式启用外，能定位到 Hyperf 应用根（bin/hyperf.php 或 vendor/hyperf，
     * 含在一层子目录中的情况）也视为 Hyperf 项目并启用索引，
     * 避免用户未点启用时索引完全为空。
     */
    public static boolean isEnabledForIndex(@Nullable Project project) {
        if (project == null) {
            return false;
        }

        if (isEnabled(project)) {
            return true;
        }

        return HyperfRootUtil.resolve(project) != null;
    }

    /** 若为 Hyperf 项目且插件未启用、用户未关闭提示，则弹出"启用插件"通知 */
    private void notifyPluginEnableDialog(Project project) {
        if (isEnabled(project) || HyperfSettings.getInstance(project).dismissEnableNotification) {
            return;
        }
        if (HyperfRootUtil.resolve(project) != null) {
            IdeHelper.notifyEnableMessage(project);
        }
    }
}
