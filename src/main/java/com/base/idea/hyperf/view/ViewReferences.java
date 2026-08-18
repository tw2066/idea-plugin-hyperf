package com.base.idea.hyperf.view;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.base.idea.hyperf.HyperfIcons;
import com.base.idea.hyperf.HyperfStartupActivity;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionLanguageRegistrar;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionProvider;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrarParameter;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.utils.PhpElementsUtil;
import fr.adrienbrault.idea.symfony2plugin.util.MethodMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 视图模板名的补全与跳转。
 *
 * <p>覆盖入口：
 * <ul>
 *   <li>{@code view('name')} 辅助函数（hyperf/view-engine）；</li>
 *   <li>{@code RenderInterface::render()/getContents()} 与 {@code Render} 实现；</li>
 *   <li>{@code FactoryInterface::make()}（view-engine 工厂）。</li>
 * </ul>
 * 视图名按 view-engine 的 {@code Finder} 规则解析：点语法转目录分隔，
 * {@code pkg::name} 走 {@code view.php} 的 namespaces 配置，
 * 依次尝试 blade.php / php / css / html 扩展名。
 */
public class ViewReferences implements GotoCompletionLanguageRegistrar {

    /** 视图渲染相关方法调用（模板名均为第 1 个参数） */
    private static final MethodMatcher.CallToSignature[] VIEW_METHODS = new MethodMatcher.CallToSignature[]{
            new MethodMatcher.CallToSignature("\\Hyperf\\View\\RenderInterface", "render"),
            new MethodMatcher.CallToSignature("\\Hyperf\\View\\RenderInterface", "getContents"),
            new MethodMatcher.CallToSignature("\\Hyperf\\View\\Render", "render"),
            new MethodMatcher.CallToSignature("\\Hyperf\\View\\Render", "getContents"),
            new MethodMatcher.CallToSignature("\\Hyperf\\ViewEngine\\Contract\\FactoryInterface", "make"),
    };

    @Override
    public boolean support(@NotNull Language language) {
        return PhpLanguage.INSTANCE == language;
    }

    @Override
    public void register(GotoCompletionRegistrarParameter registrar) {
        registrar.register(PlatformPatterns.psiElement().withParent(StringLiteralExpression.class), psiElement -> {
            if (!HyperfStartupActivity.isEnabled(psiElement)) {
                return null;
            }
            PsiElement parent = psiElement.getParent();
            if (!(parent instanceof StringLiteralExpression)) {
                return null;
            }
            if (MethodMatcher.getMatchedSignatureWithDepth(parent, VIEW_METHODS) != null) {
                return new ViewProvider(parent);
            }
            if (PhpElementsUtil.isFunctionReference(parent, 0, "view")) {
                return new ViewProvider(parent);
            }
            return null;
        });
    }

    /** 视图名的补全项与跳转目标提供者 */
    private static class ViewProvider extends GotoCompletionProvider {

        ViewProvider(PsiElement element) {
            super(element);
        }

        /** 遍历所有视图根目录，把相对路径转成视图名（含 ns:: 前缀）作为补全项 */
        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {
            Collection<LookupElement> lookupElements = new ArrayList<>();
            for (ViewConfigUtil.ViewRoot root : ViewConfigUtil.getViewRoots(getProject())) {
                String prefix = root.namespace == null ? "" : root.namespace + "::";
                collectViewNames(root.dir, "", prefix, lookupElements);
            }
            return lookupElements;
        }

        /** 递归收集目录下的模板文件，相对路径转点语法视图名（剥掉 blade.php 等扩展名） */
        private void collectViewNames(@NotNull VirtualFile dir, @NotNull String relativePath, @NotNull String prefix,
                                      @NotNull Collection<LookupElement> out) {
            for (VirtualFile child : dir.getChildren()) {
                String path = relativePath.isEmpty() ? child.getName() : relativePath + "/" + child.getName();
                if (child.isDirectory()) {
                    collectViewNames(child, path, prefix, out);
                    continue;
                }
                for (String ext : ViewConfigUtil.EXTENSIONS) {
                    String suffix = "." + ext;
                    if (child.getName().endsWith(suffix)) {
                        String viewName = prefix + path.substring(0, path.length() - suffix.length()).replace('/', '.');
                        out.add(LookupElementBuilder.create(viewName).withIcon(HyperfIcons.VIEW)
                                .withTypeText(child.getName(), true));
                        break;
                    }
                }
            }
        }

        /** 按 Finder 规则解析视图名：ns:: 走命名空间目录，否则走 view_path；逐个扩展名试出真实文件 */
        @NotNull
        @Override
        public Collection<PsiElement> getPsiTargets(StringLiteralExpression element) {
            Set<PsiElement> targets = new HashSet<>();
            String contents = element.getContents();
            if (StringUtil.isEmptyOrSpaces(contents)) {
                return targets;
            }

            String namespace = null;
            String viewName = contents;
            if (contents.contains("::")) {
                String[] segments = contents.split("::", 2);
                namespace = segments[0];
                viewName = segments.length > 1 ? segments[1] : "";
            }
            if (viewName.isEmpty()) {
                return targets;
            }

            String relativePath = viewName.replace('.', '/');
            PsiManager psiManager = PsiManager.getInstance(getProject());
            for (ViewConfigUtil.ViewRoot root : ViewConfigUtil.getViewRoots(getProject())) {
                if (namespace == null != (root.namespace == null)
                        || (namespace != null && !namespace.equals(root.namespace))) {
                    continue;
                }
                for (String ext : ViewConfigUtil.EXTENSIONS) {
                    VirtualFile file = root.dir.findFileByRelativePath(relativePath + "." + ext);
                    if (file != null && !file.isDirectory()) {
                        PsiFile psiFile = psiManager.findFile(file);
                        if (psiFile != null) {
                            targets.add(psiFile);
                        }
                    }
                }
            }
            return targets;
        }
    }
}
