package com.base.idea.hyperf.cache;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.base.idea.hyperf.HyperfIcons;
import com.base.idea.hyperf.HyperfStartupActivity;
import com.base.idea.hyperf.stub.CacheListenerStubIndex;
import com.base.idea.hyperf.stub.processor.CollectProjectUniqueKeys;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionLanguageRegistrar;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionProvider;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrarParameter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 缓存监听器名字的补全与双向跳转。
 *
 * <p>配对两侧：
 * <ul>
 *   <li>注解声明：{@code #[Cacheable(listener: "user-update")]} / {@code #[FailCache(listener: "...")]}；</li>
 *   <li>事件触发：{@code new DeleteListenerEvent("user-update", $arguments)}。</li>
 * </ul>
 * 监听名是纯字符串约定（运行时经 {@code CacheListenerCollector} 配对，写错静默无效），
 * 这里借 {@link CacheListenerStubIndex} 实现两侧互跳与名字补全。
 */
public class CacheListenerReferences implements GotoCompletionLanguageRegistrar {

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
            if (parent instanceof StringLiteralExpression
                    && CacheListenerUtil.isCacheListenerString((StringLiteralExpression) parent)) {
                return new CacheListenerProvider(parent);
            }
            return null;
        });
    }

    /** 监听器名字的补全项与跳转目标提供者 */
    private static class CacheListenerProvider extends GotoCompletionProvider {

        CacheListenerProvider(PsiElement element) {
            super(element);
        }

        /** 从索引收集所有监听器名作为补全项 */
        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {
            Collection<LookupElement> lookupElements = new ArrayList<>();
            CollectProjectUniqueKeys processor = new CollectProjectUniqueKeys(getProject(), CacheListenerStubIndex.KEY);
            FileBasedIndex.getInstance().processAllKeys(CacheListenerStubIndex.KEY, processor, getProject());
            for (String key : processor.getResult()) {
                lookupElements.add(LookupElementBuilder.create(key).withIcon(HyperfIcons.CACHE_LISTENER));
            }
            return lookupElements;
        }

        /**
         * 跳到配对的另一侧：事件侧（使用）只跳注解声明，注解侧（声明）列出全部事件使用点，
         * 避免同侧多个事件字符串互相干扰。
         */
        @NotNull
        @Override
        public Collection<PsiElement> getPsiTargets(StringLiteralExpression element) {
            Set<PsiElement> targets = new HashSet<>();
            String contents = element.getContents();
            if (StringUtil.isEmptyOrSpaces(contents)) {
                return targets;
            }
            // 当前所在侧 → 目标侧的判定函数
            boolean fromEventSide = CacheListenerUtil.isDeleteListenerEventArgument(element);

            FileBasedIndex.getInstance().getFilesWithKey(CacheListenerStubIndex.KEY, Collections.singleton(contents), virtualFile -> {
                PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
                if (psiFile == null) {
                    return true;
                }
                psiFile.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitElement(@NotNull PsiElement e) {
                        if (!(e instanceof StringLiteralExpression)
                                || !contents.equals(((StringLiteralExpression) e).getContents())) {
                            super.visitElement(e);
                            return;
                        }
                        StringLiteralExpression literal = (StringLiteralExpression) e;
                        boolean targetSide = fromEventSide
                                ? CacheListenerUtil.isAnnotationListenerArgument(literal)
                                : CacheListenerUtil.isDeleteListenerEventArgument(literal);
                        if (targetSide && !e.isEquivalentTo(element)) {
                            targets.add(e);
                        }
                        super.visitElement(e);
                    }
                });
                return true;
            }, GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.allScope(getProject()), PhpFileType.INSTANCE));

            return targets;
        }
    }
}
