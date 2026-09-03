package com.base.idea.hyperf.command;

import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.MethodReference;
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

/**
 * Hyperf 命令类的行标记运行按钮（等同 PhpStorm 测试类的绿色运行图标）。
 *
 * <p>挂在命令类名上：无参数命令点击直接在 Terminal 执行
 * {@code php bin/hyperf.php <name>}；检测到参数（signature 含 {@code {...}}、
 * 注解带 arguments/options、覆写了 getArguments()/getOptions()）则先弹输入框。
 *
 * <p>命令名解析顺序（同框架生效优先级）：{@code $signature} 首个 token → 构造函数
 * {@code parent::__construct('xx')} 首参 → {@code #[Command(name: ...)]} → {@code $name} 属性默认值。
 */
public class CommandRunLineMarkerContributor extends RunLineMarkerContributor {

    private static final String COMMAND_CLASS = "\\Hyperf\\Command\\Command";
    private static final String COMMAND_ANNOTATION = "\\Hyperf\\Command\\Annotation\\Command";

    @Override
    public @Nullable Info getInfo(@NotNull PsiElement element) {
        // 只处理类名标识符本身（每个叶子元素都会回调，先廉价过滤）
        PsiElement parent = element.getParent();
        if (!(parent instanceof PhpClass) || ((PhpClass) parent).getNameIdentifier() != element) {
            return null;
        }
        PhpClass phpClass = (PhpClass) parent;
        Project project = element.getProject();
        if (!HyperfStartupActivity.isEnabled(project)) {
            return null;
        }
        if (!new Symfony2InterfacesUtil().isInstanceOf(phpClass, COMMAND_CLASS)) {
            return null;
        }
        String commandName = resolveCommandName(phpClass);
        if (commandName == null || commandName.isEmpty()) {
            return null;
        }
        boolean withArgs = hasParameters(phpClass);
        return new Info(
                HyperfIcons.RUN,
                new AnAction[]{new RunCommandAction(project, commandName, withArgs)},
                e -> "Run command '" + commandName + "'"
        );
    }

    /**
     * 命令名，按框架生效优先级：$signature 首个 token（构造函数里 signature 会无条件覆盖
     * name）→ 构造函数 parent::__construct('xx') 首参 → #[Command(name)] → $name 属性默认值。
     */
    private static @Nullable String resolveCommandName(@NotNull PhpClass phpClass) {
        String signature = fieldStringValue(phpClass.findOwnFieldByName("signature", false));
        if (signature != null && !signature.isEmpty()) {
            return signature.split("\\s+")[0];
        }

        String constructorName = constructorParentCallName(phpClass);
        if (constructorName != null && !constructorName.isEmpty()) {
            return constructorName;
        }

        for (PhpAttribute attribute : phpClass.getAttributes()) {
            String fqn = attribute.getFQN();
            if (fqn == null || !fqn.equalsIgnoreCase(COMMAND_ANNOTATION)) {
                continue;
            }
            if (attribute.getParameterList() == null) {
                continue;
            }
            String name = stringValue(PsiElementUtils.getParameter(attribute.getParameterList(), "name", 0));
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }

        return fieldStringValue(phpClass.findOwnFieldByName("name", false));
    }

    /** 类构造函数里 parent::__construct('xx') 的首个字符串参数 */
    private static @Nullable String constructorParentCallName(@NotNull PhpClass phpClass) {
        com.jetbrains.php.lang.psi.elements.Method constructor = phpClass.findOwnMethodByName("__construct");
        if (constructor == null) {
            return null;
        }
        for (MethodReference call : PsiTreeUtil.findChildrenOfType(constructor, MethodReference.class)) {
            if (!"__construct".equals(call.getName())) {
                continue;
            }
            PsiElement classRef = call.getClassReference();
            if (classRef == null || !"parent".equals(classRef.getText())) {
                continue;
            }
            PsiElement[] params = call.getParameters();
            if (params.length > 0) {
                return stringValue(params[0]);
            }
        }
        return null;
    }

    /** 是否检测到参数定义（决定点击后直接执行还是先弹输入框） */
    private static boolean hasParameters(@NotNull PhpClass phpClass) {
        String signature = fieldStringValue(phpClass.findOwnFieldByName("signature", false));
        if (signature != null && signature.contains("{")) {
            return true;
        }
        for (PhpAttribute attribute : phpClass.getAttributes()) {
            String fqn = attribute.getFQN();
            if (fqn == null || !fqn.equalsIgnoreCase(COMMAND_ANNOTATION) || attribute.getParameterList() == null) {
                continue;
            }
            if (hasArrayItems(PsiElementUtils.getParameter(attribute.getParameterList(), "arguments", 1))
                    || hasArrayItems(PsiElementUtils.getParameter(attribute.getParameterList(), "options", 2))) {
                return true;
            }
        }
        // 覆写了 getArguments()/getOptions() 即认为有参数（静态分析返回数组内容不可靠，宁多弹框）
        if (phpClass.findOwnMethodByName("getArguments") != null
                || phpClass.findOwnMethodByName("getOptions") != null) {
            return true;
        }
        // configure() 里 $this->addArgument()/addOption() 定义参数
        for (MethodReference call : PsiTreeUtil.findChildrenOfType(phpClass, MethodReference.class)) {
            String methodName = call.getName();
            if ("addArgument".equals(methodName) || "addOption".equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    /** 注解参数是否为非空数组字面量 */
    private static boolean hasArrayItems(@Nullable PsiElement parameter) {
        if (parameter == null) {
            return false;
        }
        ArrayCreationExpression array = parameter instanceof ArrayCreationExpression
                ? (ArrayCreationExpression) parameter
                : PsiTreeUtil.findChildOfType(parameter, ArrayCreationExpression.class);
        return array != null && array.getHashElements().iterator().hasNext();
    }

    private static @Nullable String fieldStringValue(@Nullable Field field) {
        if (field == null) {
            return null;
        }
        return stringValue(field.getDefaultValue());
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

    /** 点击行标记执行命令；withArgs 时先弹参数输入框（留空=不带参数直接执行） */
    private static class RunCommandAction extends AnAction {

        private final Project project;
        private final String commandName;
        private final boolean withArgs;

        RunCommandAction(@NotNull Project project, @NotNull String commandName, boolean withArgs) {
            super("Run '" + commandName + "'");
            this.project = project;
            this.commandName = commandName;
            this.withArgs = withArgs;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            String commandLine = commandName;
            if (withArgs) {
                String args = Messages.showInputDialog(
                        project,
                        "Arguments & options (optional):",
                        "Run " + commandName,
                        null
                );
                if (args == null) {
                    return;
                }
                args = args.trim();
                if (!args.isEmpty()) {
                    commandLine += " " + args;
                }
            }
            HyperfConsoleRunner.runInTerminal(project, commandLine);
        }
    }
}
