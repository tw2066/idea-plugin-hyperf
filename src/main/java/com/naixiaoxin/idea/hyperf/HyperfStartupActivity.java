package com.naixiaoxin.idea.hyperf;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.naixiaoxin.idea.hyperf.util.IdeHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Replaces the legacy HyperfProjectComponent (ProjectComponent was removed from the platform).
 */
public class HyperfStartupActivity implements StartupActivity.DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        notifyPluginEnableDialog(project);
    }

    public static boolean isEnabled(Project project) {
        return HyperfSettings.getInstance(project).pluginEnabled;
    }

    public static boolean isEnabled(@Nullable PsiElement psiElement) {
        return psiElement != null && isEnabled(psiElement.getProject());
    }

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
