package com.base.idea.hyperf.crontab;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.ParameterList;
import com.jetbrains.php.lang.psi.elements.PhpAttribute;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.base.idea.hyperf.HyperfStartupActivity;
import fr.adrienbrault.idea.symfony2plugin.Symfony2InterfacesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Crontab 规则字符串的悬停/快速文档：显示按 Hyperf\Crontab\Parser 语义计算的
 * 最近 5 次执行时间（英文文案）。
 *
 * <p>支持两种写法：{@code #[Crontab(rule: "...")]} 注解参数与
 * {@code (new Crontab())->setRule('...')} 方法调用首参。
 *
 * <p>注册时必须 order="first"：字符串字面量本身会被 PHP 原生文档接管
 * （显示 "xxx": string），排最前才能让本文档优先生效（同 DI 绑定文档的做法）。
 */
public class CrontabRuleDocumentationProvider extends AbstractDocumentationProvider {

    private static final String CRONTAB_ANNOTATION = "\\Hyperf\\Crontab\\Annotation\\Crontab";
    private static final String CRONTAB_CLASS = "\\Hyperf\\Crontab\\Crontab";
    /** rule 在 Crontab 注解构造中的位置（rule,name,type,...） */
    private static final int RULE_POSITION = 0;
    private static final int RUN_COUNT = 5;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file,
                                                              @Nullable PsiElement contextElement, int targetOffset) {
        if (contextElement == null) {
            return null;
        }
        PsiElement parent = contextElement.getParent();
        if (parent instanceof StringLiteralExpression
                && isCrontabRuleString((StringLiteralExpression) parent)) {
            return parent;
        }
        return null;
    }

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        PsiElement literalEl = element instanceof StringLiteralExpression ? element
                : (originalElement != null ? originalElement.getParent() : null);
        if (!(literalEl instanceof StringLiteralExpression)) {
            return null;
        }
        StringLiteralExpression literal = (StringLiteralExpression) literalEl;
        if (!HyperfStartupActivity.isEnabled(literal) || !isCrontabRuleString(literal)) {
            return null;
        }

        String rule = literal.getContents();
        CronExpression expression = CronExpression.parse(rule);
        if (expression == null) {
            return null;
        }
        List<LocalDateTime> runs = expression.nextRuns(LocalDateTime.now(), RUN_COUNT);
        if (runs.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(DocumentationMarkup.DEFINITION_START);
        sb.append(escape(rule));
        sb.append(DocumentationMarkup.DEFINITION_END);
        sb.append(DocumentationMarkup.CONTENT_START);
        sb.append("Next run times:");
        for (LocalDateTime run : runs) {
            sb.append("<br/>&nbsp;&nbsp;").append(run.format(FORMATTER));
        }
        sb.append(DocumentationMarkup.CONTENT_END);
        return sb.toString();
    }

    /** 字符串是否是 crontab 规则：#[Crontab(rule: ...)] 参数 或 ->setRule('...') 首参 */
    private static boolean isCrontabRuleString(@NotNull StringLiteralExpression literal) {
        return isAnnotationRuleArgument(literal) || isSetRuleCallArgument(literal);
    }

    /** 字符串是否是 {@code #[Crontab(...)]} 的 rule 参数值（命名或第 1 个位置参数） */
    private static boolean isAnnotationRuleArgument(@NotNull StringLiteralExpression literal) {
        PhpAttribute attribute = PsiTreeUtil.getParentOfType(literal, PhpAttribute.class);
        if (attribute == null) {
            return false;
        }
        String fqn = attribute.getFQN();
        if (fqn == null || !fqn.equalsIgnoreCase(CRONTAB_ANNOTATION)) {
            return false;
        }
        ParameterList parameterList = attribute.getParameterList();
        if (parameterList == null) {
            return false;
        }
        PsiElement ruleParam = parameterList.getParameter("rule", RULE_POSITION);
        return ruleParam != null && PsiTreeUtil.isAncestor(ruleParam, literal, false);
    }

    /** 字符串是否是 {@code ->setRule('...')} 的第 1 个参数，且宿主类是 \Hyperf\Crontab\Crontab（含子类） */
    private static boolean isSetRuleCallArgument(@NotNull StringLiteralExpression literal) {
        PsiElement parent = literal.getParent();
        if (!(parent instanceof ParameterList)) {
            return false;
        }
        PsiElement[] params = ((ParameterList) parent).getParameters();
        if (params.length == 0 || params[0] != literal) {
            return false;
        }
        PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof MethodReference)) {
            return false;
        }
        MethodReference methodReference = (MethodReference) grandParent;
        if (!"setRule".equals(methodReference.getName())) {
            return false;
        }
        PsiElement resolved = methodReference.resolve();
        if (!(resolved instanceof Method)) {
            return false;
        }
        PhpClass containingClass = ((Method) resolved).getContainingClass();
        return containingClass != null
                && (CRONTAB_CLASS.equalsIgnoreCase(containingClass.getFQN())
                    || new Symfony2InterfacesUtil().isInstanceOf(containingClass, CRONTAB_CLASS));
    }

    private static @NotNull String escape(@NotNull String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
