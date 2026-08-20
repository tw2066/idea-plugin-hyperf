package com.base.idea.hyperf.aop;

import com.intellij.lang.Language;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.ClassConstantReference;
import com.jetbrains.php.lang.psi.elements.ConcatenationExpression;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpAttribute;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.jetbrains.php.PhpIndex;
import com.base.idea.hyperf.HyperfStartupActivity;
import fr.adrienbrault.idea.symfony2plugin.Symfony2InterfacesUtil;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionLanguageRegistrar;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionProvider;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrarParameter;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.utils.PhpElementsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AOP 切面类/方法字符串的跳转（不做补全）。
 *
 * <p>{@code $classes} 里的规则形如 {@code 'App\Service\SomeClass'}、
 * {@code 'App\Service\SomeClass::someMethod'}、{@code 'App\Service\SomeClass::*Method'}。
 * 触发位置：
 * <ul>
 *   <li>{@code #[Aspect(classes: [...])]} 注解参数里的字符串；</li>
 *   <li>继承 {@code AbstractAspect} 的类的 {@code $classes}/{@code $annotations} 属性默认值数组里的字符串。</li>
 * </ul>
 * 跳转规则：类部分无通配符时可定位；方法部分精确匹配跳方法、带 {@code *} 通配列出所有
 * 匹配方法（无匹配时退化到类）；类部分带 {@code *} 无法静态枚举，不跳转。
 */
public class AspectReferences implements GotoCompletionLanguageRegistrar {

    private static final String ASPECT_ANNOTATION = "\\Hyperf\\Di\\Annotation\\Aspect";
    private static final String ABSTRACT_ASPECT = "\\Hyperf\\Di\\Aop\\AbstractAspect";

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
            StringLiteralExpression literal = (StringLiteralExpression) parent;
            boolean inAspectContext = isAspectAttributeArgument(literal) || isAspectFieldDefault(literal);
            if (inAspectContext && matchClassConstantConcat(literal) != null) {
                return new AspectConcatProvider(parent);
            }
            if (inAspectContext) {
                return new AspectClassProvider(parent);
            }
            return null;
        });
    }

    /** 字符串是否位于 {@code #[Aspect(...)]} 注解内 */
    private static boolean isAspectAttributeArgument(@NotNull StringLiteralExpression literal) {
        PhpAttribute attribute = PsiTreeUtil.getParentOfType(literal, PhpAttribute.class);
        if (attribute == null) {
            return false;
        }
        String fqn = attribute.getFQN();
        return fqn != null && fqn.equalsIgnoreCase(ASPECT_ANNOTATION);
    }

    /** 字符串是否是 AbstractAspect 子类的 $classes/$annotations 属性默认值数组元素 */
    private static boolean isAspectFieldDefault(@NotNull StringLiteralExpression literal) {
        Field field = PsiTreeUtil.getParentOfType(literal, Field.class);
        if (field == null) {
            return false;
        }
        String fieldName = field.getName();
        if (!"classes".equals(fieldName) && !"annotations".equals(fieldName)) {
            return false;
        }
        PsiElement defaultValue = field.getDefaultValue();
        if (defaultValue == null || !PsiTreeUtil.isAncestor(defaultValue, literal, false)) {
            return false;
        }
        PhpClass phpClass = field.getContainingClass();
        return phpClass != null && new Symfony2InterfacesUtil().isInstanceOf(phpClass, ABSTRACT_ASPECT);
    }

    /**
     * 匹配 {@code Builder::class . '::toSql'} 这类「类常量 + 方法名字符串」拼接。
     *
     * <p>PHP 拼接左结合，字面量须是 concat 的右操作数，左操作数是 {@code Xxx::class}
     * 常量引用（或已解析出类名的子拼接）。返回左操作数解析出的类 FQN，不匹配返回 null。
     */
    @Nullable
    private static String matchClassConstantConcat(@NotNull StringLiteralExpression literal) {
        PsiElement parent = literal.getParent();
        if (!(parent instanceof ConcatenationExpression) || ((ConcatenationExpression) parent).getRightOperand() != literal) {
            return null;
        }
        PsiElement left = ((ConcatenationExpression) parent).getLeftOperand();
        return left == null ? null : evalClassConstant(left);
    }

    /** 左子树求类名：Xxx::class / Xxx::class 常量 / 含类常量的子拼接；解析不出返回 null */
    @Nullable
    private static String evalClassConstant(@NotNull PsiElement expr) {
        if (expr instanceof ClassConstantReference) {
            // 仅 ::class（排除 Foo::OTHER_CONST）
            if (!"class".equals(((ClassConstantReference) expr).getName())) {
                return null;
            }
            return PhpElementsUtil.getClassConstantFqn((ClassConstantReference) expr);
        }
        if (expr instanceof ConcatenationExpression) {
            PsiElement left = ((ConcatenationExpression) expr).getLeftOperand();
            PsiElement right = ((ConcatenationExpression) expr).getRightOperand();
            // 左子树能解析出类名则直接用（拼接字符串如 '' 不影响类名）
            String leftClass = left == null ? null : evalClassConstant(left);
            if (leftClass != null) {
                return leftClass;
            }
            return right == null ? null : evalClassConstant(right);
        }
        return null;
    }

    /** 把类/方法解析为跳转目标加入 out；类带通配符跳过，方法支持精确与 * 通配 */
    private static void addTargets(@NotNull com.intellij.openapi.project.Project project,
                                   @NotNull String classFqn, @Nullable String methodPart,
                                   @NotNull Set<PsiElement> out) {
        for (PhpClass phpClass : PhpIndex.getInstance(project).getAnyByFQN("\\" + classFqn)) {
            if (methodPart == null || methodPart.isEmpty()) {
                out.add(phpClass);
                continue;
            }
            if (methodPart.contains("*")) {
                boolean matched = false;
                Pattern pattern = wildcardToPattern(methodPart);
                for (Method method : phpClass.getMethods()) {
                    if (pattern.matcher(method.getName()).matches()) {
                        out.add(method);
                        matched = true;
                    }
                }
                if (!matched) {
                    out.add(phpClass);
                }
                continue;
            }
            Method method = PhpElementsUtil.getClassMethod(phpClass, methodPart);
            out.add(method != null ? method : phpClass);
        }
    }

    /** {@code *Method} → 全串匹配正则（* 转 .*, 其余逐段 quote） */
    @NotNull
    private static Pattern wildcardToPattern(@NotNull String wildcard) {
        StringBuilder regex = new StringBuilder();
        String[] segments = wildcard.split("\\*", -1);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            if (!segments[i].isEmpty()) {
                regex.append(Pattern.quote(segments[i]));
            }
        }
        return Pattern.compile(regex.toString());
    }

    /** 列出指定类的方法名补全项（排除魔术方法），typeText 标注所属类 */
    private static void addMethodCompletions(@NotNull com.intellij.openapi.project.Project project,
                                             @NotNull String classFqn, @NotNull String prefix,
                                             @NotNull com.intellij.codeInsight.completion.CompletionResultSet resultSet) {
        com.intellij.codeInsight.completion.CompletionResultSet filtered = resultSet.withPrefixMatcher(prefix);
        for (PhpClass phpClass : PhpIndex.getInstance(project).getAnyByFQN("\\" + classFqn)) {
            for (Method method : phpClass.getMethods()) {
                if (method.getName().startsWith("__")) {
                    continue;
                }
                filtered.addElement(com.intellij.codeInsight.lookup.LookupElementBuilder
                        .create(method.getName())
                        .withIcon(method.getIcon(0))
                        .withTypeText(phpClass.getName(), true));
            }
        }
    }

    /** 取补全原始位置所在的字符串字面量（规避补全副本 dummy 占位符） */
    @Nullable
    private static StringLiteralExpression originalLiteral(@NotNull fr.adrienbrault.idea.symfony2plugin.codeInsight.completion.CompletionContributorParameter parameter) {
        PsiElement original = parameter.getCompletionParameters().getOriginalPosition();
        if (original == null || !(original.getParent() instanceof StringLiteralExpression)) {
            return null;
        }
        return (StringLiteralExpression) original.getParent();
    }

    /** caret 在字符串内容内的前缀文本（原文件 offset - 字面量起始 - 前引号宽度） */
    @Nullable
    private static String beforeCaret(@NotNull fr.adrienbrault.idea.symfony2plugin.codeInsight.completion.CompletionContributorParameter parameter,
                                      @Nullable StringLiteralExpression literal) {
        if (literal == null) {
            return null;
        }
        int caretInContent = parameter.getCompletionParameters().getOffset()
                - literal.getTextRange().getStartOffset() - 1;
        String contents = literal.getContents();
        if (caretInContent < 0 || caretInContent > contents.length()) {
            return null;
        }
        return contents.substring(0, caretInContent);
    }

    /** 切面类/方法字符串的跳转目标提供者（仅跳转） */
    private static class AspectClassProvider extends GotoCompletionProvider {

        AspectClassProvider(PsiElement element) {
            super(element);
        }

        /** 仅跳转，不提供补全项 */
        @NotNull
        @Override
        public Collection<com.intellij.codeInsight.lookup.LookupElement> getLookupElements() {
            return java.util.Collections.emptyList();
        }

        /** 整串写法：{@code 'App\Svc\Foo::'} 之后补全该类方法名 */
        @Override
        public void getLookupElements(fr.adrienbrault.idea.symfony2plugin.codeInsight.completion.CompletionContributorParameter parameter) {
            StringLiteralExpression literal = originalLiteral(parameter);
            String beforeCaret = beforeCaret(parameter, literal);
            if (beforeCaret == null) {
                return;
            }
            int separator = beforeCaret.indexOf("::");
            if (separator < 0) {
                return;
            }
            String classPart = beforeCaret.substring(0, separator);
            if (classPart.isEmpty() || classPart.contains("*")) {
                return;
            }
            addMethodCompletions(getProject(), classPart, beforeCaret.substring(separator + 2),
                    parameter.getCompletionResultSet());
        }

        @NotNull
        @Override
        public Collection<PsiElement> getPsiTargets(StringLiteralExpression element) {
            Set<PsiElement> targets = new HashSet<>();
            String contents = element.getContents();

            String classPart = contents;
            String methodPart = null;
            if (contents.contains("::")) {
                String[] segments = contents.split("::", 2);
                classPart = segments[0];
                methodPart = segments.length > 1 ? segments[1] : null;
            }
            // 类部分带通配符无法静态枚举目标
            if (classPart.isEmpty() || classPart.contains("*")) {
                return targets;
            }
            addTargets(getProject(), classPart, methodPart, targets);
            return targets;
        }
    }

    /** {@code Builder::class . '::toSql'} 拼接写法的跳转提供者（仅跳转）：方法名在字符串里，类名取左侧 ::class */
    private static class AspectConcatProvider extends GotoCompletionProvider {

        AspectConcatProvider(PsiElement element) {
            super(element);
        }

        @NotNull
        @Override
        public Collection<com.intellij.codeInsight.lookup.LookupElement> getLookupElements() {
            return java.util.Collections.emptyList();
        }

        /** 拼接写法：{@code Foo::class . '::'} 之后补全左侧类的方法名 */
        @Override
        public void getLookupElements(fr.adrienbrault.idea.symfony2plugin.codeInsight.completion.CompletionContributorParameter parameter) {
            StringLiteralExpression literal = originalLiteral(parameter);
            String beforeCaret = beforeCaret(parameter, literal);
            if (literal == null || beforeCaret == null) {
                return;
            }
            String classFqn = matchClassConstantConcat(literal);
            if (classFqn == null) {
                return;
            }
            String prefix = beforeCaret.startsWith("::") ? beforeCaret.substring(2) : beforeCaret;
            addMethodCompletions(getProject(), classFqn, prefix, parameter.getCompletionResultSet());
        }

        @NotNull
        @Override
        public Collection<PsiElement> getPsiTargets(StringLiteralExpression element) {
            Set<PsiElement> targets = new HashSet<>();
            String classFqn = matchClassConstantConcat(element);
            if (classFqn == null) {
                return targets;
            }
            String methodPart = element.getContents();
            if (methodPart.startsWith("::")) {
                methodPart = methodPart.substring(2);
            }
            addTargets(getProject(), classFqn, methodPart, targets);
            return targets;
        }
    }
}
