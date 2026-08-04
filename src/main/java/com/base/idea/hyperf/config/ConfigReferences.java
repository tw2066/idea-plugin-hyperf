package com.base.idea.hyperf.config;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
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
import com.base.idea.hyperf.stub.ConfigKeyStubIndex;
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
 * 配置键的补全与跳转。
 *
 * <p>覆盖两类调用场景：
 * <ul>
 *   <li>{@code \Hyperf\Contract\ConfigInterface::get()/has()/set()} 方法调用的参数；</li>
 *   <li>全局 {@code config()} 辅助函数的第一个参数。</li>
 * </ul>
 * 命中后提供配置键补全（来自 {@link ConfigKeyStubIndex} 索引）与跳转到定义位置。
 */
public class ConfigReferences implements GotoCompletionLanguageRegistrar {

    /** 匹配 ConfigInterface 的 get/has/set 方法调用 */
    private static MethodMatcher.CallToSignature[] CONFIG = new MethodMatcher.CallToSignature[]{
            new MethodMatcher.CallToSignature("\\Hyperf\\Contract\\ConfigInterface", "get"),
            new MethodMatcher.CallToSignature("\\Hyperf\\Contract\\ConfigInterface", "has"),
            new MethodMatcher.CallToSignature("\\Hyperf\\Contract\\ConfigInterface", "set"),
    };

    @Override
    public boolean support(@NotNull Language language) {
        // 只有PHP才执行
        return PhpLanguage.INSTANCE == language;
    }

    @Override
    public void register(GotoCompletionRegistrarParameter registrar) {
        registrar.register(PlatformPatterns.psiElement(), psiElement -> {
            if (!HyperfStartupActivity.isEnabled(psiElement)) {
                return null;
            }

            PsiElement parent = psiElement.getParent();
            // ConfigInterface::get('key') / has('key')
            if (parent != null && MethodMatcher.getMatchedSignatureWithDepth(parent, CONFIG) != null) {
                return new ConfigKeyProvider(parent);
            }

            // config('key')
            if (parent != null && PhpElementsUtil.isFunctionReference(parent, 0, "config")) {
                return new ConfigKeyProvider(parent);
            }
            return null;
        });
    }

    /** 配置键的补全项与跳转目标提供者 */
    private static class ConfigKeyProvider extends GotoCompletionProvider {

        public ConfigKeyProvider(PsiElement element) {
            super(element);
        }

        /** 从索引收集所有配置键，生成补全列表 */
        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {
            final Collection<LookupElement> lookupElements = new ArrayList<>();
            CollectProjectUniqueKeys ymlProjectProcessor = new CollectProjectUniqueKeys(getProject(), ConfigKeyStubIndex.KEY);
            FileBasedIndex.getInstance().processAllKeys(ConfigKeyStubIndex.KEY, ymlProjectProcessor, getProject());
            for (String key : ymlProjectProcessor.getResult()) {
                lookupElements.add(LookupElementBuilder.create(key).withIcon(HyperfIcons.CONFIG));
            }


            return lookupElements;
        }

        /** 在含该键的配置文件中定位具体的键元素，作为跳转目标 */
        @NotNull
        @Override
        public Collection<PsiElement> getPsiTargets(StringLiteralExpression element) {

            final Set<PsiElement> targets = new HashSet<>();
            final String contents = element.getContents();
            if (StringUtil.isEmptyOrSpaces(contents)) {
                return targets;
            }

            FileBasedIndex.getInstance().getFilesWithKey(ConfigKeyStubIndex.KEY, Collections.singleton(contents), virtualFile -> {
                PsiFile psiFileTarget = PsiManager.getInstance(getProject()).findFile(virtualFile);
                if (psiFileTarget == null) {
                    return true;
                }

                // 重新遍历该配置文件的数组，找到键名与当前内容完全一致的键元素
                psiFileTarget.acceptChildren(new ArrayReturnPsiRecursiveVisitor(ConfigFileUtil.matchConfigFile(getProject(), virtualFile).getKeyPrefix(), (key, psiKey, isRootElement) -> {
                    if (!isRootElement && key.equals(contents)) {
                        targets.add(psiKey);
                    }
                }));

                return true;
            }, GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.allScope(getProject()), PhpFileType.INSTANCE));

            return targets;
        }
    }
}
