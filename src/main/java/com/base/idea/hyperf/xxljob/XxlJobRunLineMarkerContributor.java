package com.base.idea.hyperf.xxljob;

import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpAttribute;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.base.idea.hyperf.HyperfStartupActivity;
import com.base.idea.hyperf.HyperfIcons;
import com.base.idea.hyperf.console.HyperfConsoleRunner;
import com.base.idea.hyperf.util.PsiElementUtils;
import fr.adrienbrault.idea.symfony2plugin.Symfony2InterfacesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * XXL-JOB 任务的行标记运行按钮（交互同 CommandRunLineMarkerContributor）。
 *
 * <p>覆盖 hyperf/xxl-job-incubator 的两种 Bean 模式：
 * <ul>
 *   <li>类形式：实现 {@code JobHandlerInterface}（一般继承 AbstractJobHandler）且类上带
 *       {@code #[XxlJob('name')]} —— 按钮挂在类名上</li>
 *   <li>方法形式：任意方法上带 {@code #[XxlJob('name')]} —— 按钮挂在方法名上</li>
 * </ul>
 *
 * <p>handler 名恒为注解 value 值（框架注册时没有类名/方法名兜底），value 为空不显示按钮。
 * 点击先弹 --params 输入框（留空=不带参数），在内置 Terminal 执行
 * {@code php bin/hyperf.php execute:xxl-job --handler=<name> [--params='...']}。
 */
public class XxlJobRunLineMarkerContributor extends RunLineMarkerContributor {

    private static final String XXL_JOB_ANNOTATION = "\\Hyperf\\XxlJob\\Annotation\\XxlJob";
    private static final String JOB_HANDLER_INTERFACE = "\\Hyperf\\XxlJob\\Handler\\JobHandlerInterface";

    @Override
    public @Nullable Info getInfo(@NotNull PsiElement element) {
        // 只处理类名/方法名标识符本身（每个叶子元素都会回调，先廉价过滤）
        PsiElement parent = element.getParent();
        String handlerName;
        if (parent instanceof Method && ((Method) parent).getNameIdentifier() == element) {
            handlerName = resolveHandlerName(((Method) parent).getAttributes());
        } else if (parent instanceof PhpClass && ((PhpClass) parent).getNameIdentifier() == element) {
            PhpClass phpClass = (PhpClass) parent;
            // 类形式未实现 JobHandlerInterface 时框架启动即抛异常，视为无效任务不显示按钮
            if (!new Symfony2InterfacesUtil().isInstanceOf(phpClass, JOB_HANDLER_INTERFACE)) {
                return null;
            }
            handlerName = resolveHandlerName(phpClass.getAttributes());
        } else {
            return null;
        }
        if (handlerName == null || handlerName.isEmpty()) {
            return null;
        }
        Project project = element.getProject();
        if (!HyperfStartupActivity.isEnabled(project)) {
            return null;
        }
        return new Info(
                HyperfIcons.RUN,
                new AnAction[]{new RunXxlJobAction(project, handlerName)},
                e -> "Run XXL-JOB handler '" + handlerName + "'"
        );
    }

    /** handler 名 = 注解 value（位置参数 0 或命名参数 value:），与框架注册逻辑一致，无兜底 */
    private static @Nullable String resolveHandlerName(@NotNull Collection<PhpAttribute> attributes) {
        for (PhpAttribute attribute : attributes) {
            String fqn = attribute.getFQN();
            if (fqn == null || !fqn.equalsIgnoreCase(XXL_JOB_ANNOTATION) || attribute.getParameterList() == null) {
                continue;
            }
            return stringValue(PsiElementUtils.getParameter(attribute.getParameterList(), "value", 0));
        }
        return null;
    }

    private static @Nullable String stringValue(@Nullable PsiElement element) {
        if (element instanceof StringLiteralExpression) {
            return ((StringLiteralExpression) element).getContents();
        }
        if (element == null) {
            return null;
        }
        // 命名参数包装等情况，向下找字符串字面量
        StringLiteralExpression literal = PsiTreeUtil.findChildOfType(element, StringLiteralExpression.class);
        return literal == null ? null : literal.getContents();
    }

    /** 点击行标记执行 execute:xxl-job；先弹 --params 输入框（留空=不带参数直接执行） */
    private static class RunXxlJobAction extends AnAction {

        private final Project project;
        private final String handlerName;

        RunXxlJobAction(@NotNull Project project, @NotNull String handlerName) {
            super("Run '" + handlerName + "'");
            this.project = project;
            this.handlerName = handlerName;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            String params = Messages.showInputDialog(
                    project,
                    "Executor params (--params, optional):",
                    "Run XXL-JOB " + handlerName,
                    null
            );
            if (params == null) {
                return;
            }
            params = params.trim();
            String commandLine = "execute:xxl-job --handler=" + handlerName;
            if (!params.isEmpty()) {
                commandLine += " --params=" + shellQuote(params);
            }
            HyperfConsoleRunner.runInTerminal(project, commandLine);
        }

        /** 优先包单引号（JSON 里的双引号安全）；含单引号则包双引号；两种都含则保持原样交给用户 */
        static @NotNull String shellQuote(@NotNull String value) {
            if (!value.contains("'")) {
                return "'" + value + "'";
            }
            if (!value.contains("\"")) {
                return "\"" + value + "\"";
            }
            return value;
        }
    }
}
