package com.base.idea.hyperf.translation;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.base.idea.hyperf.HyperfIcons;
import com.base.idea.hyperf.HyperfStartupActivity;
import com.base.idea.hyperf.HyperfSettings;
import com.base.idea.hyperf.stub.TranslationKeyStubIndex;
import com.base.idea.hyperf.stub.processor.CollectProjectUniqueKeys;
import com.base.idea.hyperf.util.ArrayReturnPsiRecursiveVisitor;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionLanguageRegistrar;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionProvider;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrarParameter;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.utils.PhpElementsUtil;
import fr.adrienbrault.idea.symfony2plugin.util.MethodMatcher;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 翻译键的补全与跳转。
 *
 * <p>覆盖两类调用场景：
 * <ul>
 *   <li>{@code \Hyperf\Contract\TranslatorInterface::trans()} 方法调用的参数；</li>
 *   <li>全局 {@code trans()} / {@code __()} 辅助函数的第一个参数。</li>
 * </ul>
 * 仅在项目安装了 hyperf/translation（vendor/hyperf/translation 存在）时启用。
 * 跳转时优先返回当前配置语言目录下的定义。
 */
public class TranslationReferences implements GotoCompletionLanguageRegistrar {

    /** 匹配 TranslatorInterface 的 trans 方法调用 */
    private static MethodMatcher.CallToSignature[] TRANSLATION_KEY = new MethodMatcher.CallToSignature[]{
            new MethodMatcher.CallToSignature("\\Hyperf\\Contract\\TranslatorInterface", "trans"),
    };

    @Override
    public boolean support(@NotNull Language language) {
        return PhpLanguage.INSTANCE == language;
    }

    @Override
    public void register(GotoCompletionRegistrarParameter registrar) {
        registrar.register(PlatformPatterns.psiElement(), psiElement -> {
            if (!HyperfStartupActivity.isEnabled(psiElement)) {
                return null;
            }
            // only install hyperf/translation
            // 未安装翻译组件则不提供翻译键功能
            VirtualFile baseDir = psiElement.getProject().getBaseDir();
            if (baseDir == null) {
                return null;
            }
            if (VfsUtil.findRelativeFile(baseDir, "vendor", "hyperf", "translation") == null
            ) {
                return null;
            }
            PsiElement parent = psiElement.getParent();
            // TranslatorInterface::trans('key') 或 trans('key') / __('key')
            if (parent != null && (
                    MethodMatcher.getMatchedSignatureWithDepth(parent, TRANSLATION_KEY) != null || PhpElementsUtil.isFunctionReference(parent, 0, "trans", "__")
            )) {
                return new TranslationKey(parent);
            }
            return null;
        });
    }

    /** 翻译键的补全项与跳转目标提供者 */
    public static class TranslationKey extends GotoCompletionProvider {

        public TranslationKey(PsiElement element) {
            super(element);
        }

        /** 从索引收集所有翻译键，生成补全列表 */
        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {
            final Collection<LookupElement> lookupElements = new ArrayList<>();

            CollectProjectUniqueKeys ymlProjectProcessor = new CollectProjectUniqueKeys(getProject(), TranslationKeyStubIndex.KEY);
            FileBasedIndex.getInstance().processAllKeys(TranslationKeyStubIndex.KEY, ymlProjectProcessor, getProject());
            for (String key : ymlProjectProcessor.getResult()) {
                lookupElements.add(LookupElementBuilder.create(key).withIcon(HyperfIcons.TRANSLATION));
            }

            return lookupElements;
        }

        /**
         * 在含该键的翻译文件中定位键元素作为跳转目标。
         * 当前配置语言（translationLang）目录下的定义排在前面。
         */
        @NotNull
        @Override
        public Collection<PsiElement> getPsiTargets(StringLiteralExpression element) {
            final String contents = element.getContents();
            if (StringUtil.isEmptyOrSpaces(contents)) {
                return Collections.emptyList();
            }

            // 当前语言目录的路径片段，用于优先匹配
            final String priorityTemplate = "/" + HyperfSettings.getInstance(element.getProject()).translationLang + "/";

            final Set<PsiElement> priorityTargets = new LinkedHashSet<>();
            final Set<PsiElement> targets = new LinkedHashSet<>();

            FileBasedIndex.getInstance().getFilesWithKey(TranslationKeyStubIndex.KEY, Collections.singleton(contents), virtualFile -> {
                PsiFile psiFileTarget = PsiManager.getInstance(getProject()).findFile(virtualFile);
                if (psiFileTarget == null) {
                    return true;
                }

                String namespace = TranslationUtil.getNamespaceFromFilePath(virtualFile.getPath(),getProject());
                if (namespace == null) {
                    return true;
                }

                psiFileTarget.acceptChildren(new ArrayReturnPsiRecursiveVisitor(namespace, (key, psiKey, isRootElement) -> {
                    if (!isRootElement && key.equalsIgnoreCase(contents)) {
                        // 当前语言的定义优先
                        if (virtualFile.getPath().contains(priorityTemplate)) {
                            priorityTargets.add(psiKey);
                        } else {
                            targets.add(psiKey);
                        }
                    }
                }));

                return true;
            }, GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.allScope(getProject()), PhpFileType.INSTANCE));

            // 优先目标在前，其余追加在后
            priorityTargets.addAll(targets);
            return priorityTargets;

        }
    }
}
