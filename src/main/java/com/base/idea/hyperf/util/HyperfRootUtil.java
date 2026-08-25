package com.base.idea.hyperf.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 解析 Hyperf 应用根目录（即 BASE_PATH 所在目录）。
 *
 * <p>支持 Hyperf 不在项目根的场景（如项目根为 bigdata/、应用在 bigdata/php/），
 * 定位顺序（命中即返回）：
 * <ol>
 *   <li>项目根本身含 {@code bin/hyperf.php}（骨架入口脚本，BASE_PATH 定义点，随仓库提交，
 *       未 composer install 时也可定位）</li>
 *   <li>项目根的一层子目录含 {@code bin/hyperf.php}</li>
 *   <li>项目根本身含 {@code vendor/hyperf}（入口脚本被改名时的兜底）</li>
 *   <li>项目根的一层子目录含 {@code vendor/hyperf}</li>
 * </ol>
 *
 * <p>结果按项目缓存，VFS 结构变化（文件/目录增删）时自动失效重算。
 * 插件内所有需要"项目根"的功能（BASE_PATH 补全、.env 索引、vendor 组件探测等）
 * 统一以此为准，不要直接用 {@code project.getBaseDir()}。
 */
public class HyperfRootUtil {

    private static final Key<CachedValue<VirtualFile>> CACHED_ROOT = Key.create("hyperf.app.root");

    /** 解析 Hyperf 应用根目录；不是 Hyperf 项目（或无法定位）时返回 null */
    @Nullable
    public static VirtualFile resolve(@NotNull Project project) {
        CachedValue<VirtualFile> cached = project.getUserData(CACHED_ROOT);
        if (cached == null) {
            cached = CachedValuesManager.getManager(project).createCachedValue(
                    () -> CachedValueProvider.Result.create(
                            doResolve(project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS),
                    false);
            project.putUserData(CACHED_ROOT, cached);
        }
        return cached.getValue();
    }

    @Nullable
    private static VirtualFile doResolve(@NotNull Project project) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return null;
        }
        if (hasEntryScript(baseDir)) {
            return baseDir;
        }
        VirtualFile childHit = findChildWith(baseDir, true);
        if (childHit != null) {
            return childHit;
        }
        if (hasVendorHyperf(baseDir)) {
            return baseDir;
        }
        return findChildWith(baseDir, false);
    }

    /** 在一层子目录中找 Hyperf 根；entryScript=true 找 bin/hyperf.php，否则找 vendor/hyperf */
    @Nullable
    private static VirtualFile findChildWith(@NotNull VirtualFile baseDir, boolean entryScript) {
        for (VirtualFile child : baseDir.getChildren()) {
            // 跳过 .idea/.git 等隐藏目录
            if (!child.isDirectory() || child.getName().startsWith(".")) {
                continue;
            }
            if (entryScript ? hasEntryScript(child) : hasVendorHyperf(child)) {
                return child;
            }
        }
        return null;
    }

    /** 骨架入口脚本 bin/hyperf.php */
    private static boolean hasEntryScript(@NotNull VirtualFile dir) {
        return VfsUtil.findRelativeFile(dir, "bin", "hyperf.php") != null;
    }

    private static boolean hasVendorHyperf(@NotNull VirtualFile dir) {
        return VfsUtil.findRelativeFile(dir, "vendor", "hyperf") != null;
    }
}
