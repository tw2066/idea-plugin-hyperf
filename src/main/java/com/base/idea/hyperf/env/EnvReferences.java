package com.base.idea.hyperf.env;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.base.idea.hyperf.HyperfIcons;
import com.base.idea.hyperf.HyperfSettings;
import com.base.idea.hyperf.HyperfStartupActivity;
import com.base.idea.hyperf.stub.EnvKeyStubIndex;
import com.base.idea.hyperf.stub.processor.CollectProjectUniqueKeys;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionLanguageRegistrar;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionProvider;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrarParameter;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.utils.PhpElementsUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * {@code env()} 环境变量键的补全与跳转。
 *
 * <p>覆盖全局 {@code env('KEY')}（含 {@code Hyperf\Support\env()}）第一个字符串参数。
 * 补全键来自 {@link EnvKeyStubIndex} 对项目根 {@code .env} 文件的索引；
 * 跳转定位到 .env 文件中该键所在行。
 */
public class EnvReferences implements GotoCompletionLanguageRegistrar {

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
            // 设置里关闭 env 功能则整体短路
            if (!HyperfSettings.getInstance(psiElement.getProject()).envEnabled) {
                return null;
            }
            PsiElement parent = psiElement.getParent();
            // env('KEY') 第一个参数
            if (parent instanceof StringLiteralExpression && PhpElementsUtil.isFunctionReference(parent, 0, "env")) {
                return new EnvKeyProvider(parent);
            }
            return null;
        });
    }

    /** 环境变量键的补全项与跳转目标提供者 */
    private static class EnvKeyProvider extends GotoCompletionProvider {

        EnvKeyProvider(PsiElement element) {
            super(element);
        }

        /** 补全项尾部展示的变量值最大长度，超出截断 */
        private static final int VALUE_DISPLAY_MAX = 50;

        /** 从索引收集所有 .env 键（含值），生成补全列表 */
        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {
            final Collection<LookupElement> lookupElements = new ArrayList<>();
            CollectProjectUniqueKeys processor = new CollectProjectUniqueKeys(getProject(), EnvKeyStubIndex.KEY);
            FileBasedIndex.getInstance().processAllKeys(EnvKeyStubIndex.KEY, processor, getProject());
            for (String key : processor.getResult()) {
                LookupElementBuilder builder = LookupElementBuilder.create(key).withIcon(HyperfIcons.ENV);
                String value = getIndexedValue(key);
                if (!value.isEmpty()) {
                    if (value.length() > VALUE_DISPLAY_MAX) {
                        value = value.substring(0, VALUE_DISPLAY_MAX) + "…";
                    }
                    builder = builder.withTailText(" = " + value, true);
                }
                lookupElements.add(builder);
            }
            return lookupElements;
        }

        /** 取该键在索引中的第一个值；无值返回空串 */
        @NotNull
        private String getIndexedValue(@NotNull String key) {
            Collection<String> values = FileBasedIndex.getInstance().getValues(
                    EnvKeyStubIndex.KEY, key, GlobalSearchScope.allScope(getProject()));
            for (String value : values) {
                return value == null ? "" : value;
            }
            return "";
        }

        /** 定位含该键的 .env 文件，跳到键所在行的行首元素 */
        @NotNull
        @Override
        public Collection<PsiElement> getPsiTargets(StringLiteralExpression element) {
            final String contents = element.getContents();
            if (StringUtil.isEmptyOrSpaces(contents)) {
                return Collections.emptyList();
            }

            final Set<PsiElement> targets = new HashSet<>();

            FileBasedIndex.getInstance().getFilesWithKey(EnvKeyStubIndex.KEY, Collections.singleton(contents), virtualFile -> {
                PsiManager psiManager = PsiManager.getInstance(getProject());
                com.intellij.psi.PsiFile psiFile = psiManager.findFile(virtualFile);
                if (psiFile == null) {
                    return true;
                }

                // 逐行找到键所在行，用 findElementAt 定位到键名起始处作为跳转目标
                String text = psiFile.getText();
                String[] lines = text.split("\\R", -1);
                int offset = 0;
                for (String line : lines) {
                    String key = EnvKeyStubIndex.parseKey(line);
                    if (contents.equals(key)) {
                        // 定位到键名第一个字符（跳过前导空白 / "export "）
                        int keyOffset = offset + line.indexOf(contents);
                        PsiElement target = psiFile.findElementAt(keyOffset);
                        targets.add(target != null ? target : psiFile);
                        break;
                    }
                    offset += line.length() + 1;
                }

                return true;
            }, GlobalSearchScope.allScope(getProject()));

            return targets;
        }
    }
}
