package com.base.idea.hyperf.di;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.php.lang.psi.elements.ArrayHashElement;
import com.jetbrains.php.lang.psi.elements.ClassConstantReference;
import com.base.idea.hyperf.stub.DiBindingStubIndex;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.utils.PhpElementsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 {@link DiBindingStubIndex} 计算接口的生效绑定，逐条模拟运行时规则：
 *
 * <ol>
 *   <li>项目 {@code config/autoload/dependencies.php} 无条件生效
 *       （DefinitionSourceFactory 用 {@code array_replace} 覆盖 vendor，不看权重）；</li>
 *   <li>vendor 包之间按 {@code composer.lock} 的 packages → packages-dev 声明顺序，
 *       模拟 {@code ProviderConfig::merge}：已有绑定非 PriorityDefinition 时新绑定无条件覆盖；
 *       已有是 PriorityDefinition 时普通绑定被丢弃、同为 PriorityDefinition 仅权重更高才替换。</li>
 * </ol>
 */
public class DiBindingResolver {

    /** 一条绑定：解析值 + 来源（项目 dependencies.php / vendor 包名） */
    private static class Binding {
        final DiBindingValue value;
        final boolean projectFile;
        @Nullable
        final String packageName;

        Binding(DiBindingValue value, boolean projectFile, @Nullable String packageName) {
            this.value = value;
            this.projectFile = projectFile;
            this.packageName = packageName;
        }
    }

    /** 接口的生效绑定；无绑定返回 null */
    @Nullable
    public static DiBindingValue resolveWinningBinding(@NotNull Project project, @NotNull String interfaceFqn) {
        List<Binding> bindings = collectBindings(project, interfaceFqn);
        if (bindings.isEmpty()) {
            return null;
        }

        // 第 1 层：项目 dependencies.php 无条件覆盖（DefinitionSourceFactory::array_replace）
        for (Binding binding : bindings) {
            if (binding.projectFile) {
                return binding.value;
            }
        }

        // 第 2 层：vendor 之间按 lock 顺序模拟 ProviderConfig::merge
        Map<String, Integer> lockRank = loadLockRank(project);
        bindings.sort((a, b) -> Integer.compare(
                a.packageName == null ? Integer.MAX_VALUE : lockRank.getOrDefault(a.packageName, Integer.MAX_VALUE),
                b.packageName == null ? Integer.MAX_VALUE : lockRank.getOrDefault(b.packageName, Integer.MAX_VALUE)));

        Binding current = null;
        for (Binding next : bindings) {
            if (current == null) {
                current = next;
                continue;
            }
            if (!current.value.priorityDefinition) {
                // 已有绑定非 PriorityDefinition：新绑定无条件覆盖
                current = next;
                continue;
            }
            // 已有是 PriorityDefinition：普通绑定被丢弃；同为 PD 仅权重更高才替换（同权重保留先注册者）
            if (next.value.priorityDefinition && next.value.priority > current.value.priority) {
                current = next;
            }
        }
        return current == null ? null : current.value;
    }

    /** 收集指定接口的全部绑定（按文件定位，PSI 解析值形态） */
    @NotNull
    private static List<Binding> collectBindings(@NotNull Project project, @NotNull String interfaceFqn) {
        List<Binding> bindings = new ArrayList<>();
        FileBasedIndex.getInstance().getFilesWithKey(DiBindingStubIndex.KEY, Collections.singleton(interfaceFqn),
                virtualFile -> {
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                    if (psiFile == null) {
                        return true;
                    }
                    String path = virtualFile.getPath().replace('\\', '/');
                    boolean projectFile = path.endsWith("/config/autoload/dependencies.php");
                    String packageName = null;
                    int vendorPos = path.indexOf("/vendor/");
                    if (vendorPos >= 0) {
                        String[] segments = path.substring(vendorPos + 8).split("/");
                        if (segments.length >= 2) {
                            packageName = segments[0] + "/" + segments[1];
                        }
                    }
                    final String finalPackageName = packageName;
                    psiFile.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
                        @Override
                        public void visitElement(@NotNull PsiElement e) {
                            if (e instanceof ArrayHashElement) {
                                ArrayHashElement hashElement = (ArrayHashElement) e;
                                if (hashElement.getKey() instanceof ClassConstantReference
                                        && interfaceFqn.equals(PhpElementsUtil.getClassConstantFqn(
                                                (ClassConstantReference) hashElement.getKey()))) {
                                    DiBindingValue value = DiBindingValue.parse(hashElement.getValue());
                                    if (value != null) {
                                        bindings.add(new Binding(value, projectFile, finalPackageName));
                                    }
                                }
                            }
                            super.visitElement(e);
                        }
                    });
                    return true;
                }, GlobalSearchScope.allScope(project));
        return bindings;
    }

    /** composer.lock 包名 → 声明位次（正则扫 name 字段的文档序，与 packages → packages-dev 顺序一致） */
    private static final Pattern LOCK_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    /** 包序缓存：lock 文件路径 + 修改时间 → 名次表（避免每次悬停重解析数 MB 的 lock） */
    private static String cachedLockPath;
    private static long cachedLockStamp = -1;
    private static Map<String, Integer> cachedRank = Collections.emptyMap();

    @NotNull
    private static synchronized Map<String, Integer> loadLockRank(@NotNull Project project) {
        VirtualFile baseDir = project.getBaseDir();
        VirtualFile lockFile = baseDir == null ? null : baseDir.findChild("composer.lock");
        if (lockFile == null) {
            return Collections.emptyMap();
        }
        String path = lockFile.getPath();
        long stamp = lockFile.getTimeStamp();
        if (path.equals(cachedLockPath) && stamp == cachedLockStamp) {
            return cachedRank;
        }
        Map<String, Integer> rank = new HashMap<>();
        try {
            String content = new String(lockFile.contentsToByteArray(), StandardCharsets.UTF_8);
            Matcher matcher = LOCK_NAME.matcher(content);
            int position = 0;
            while (matcher.find()) {
                rank.putIfAbsent(matcher.group(1), position++);
            }
        } catch (IOException ignored) {
            return Collections.emptyMap();
        }
        cachedLockPath = path;
        cachedLockStamp = stamp;
        cachedRank = rank;
        return rank;
    }
}
