package com.base.idea.hyperf.validation;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.ArrayHashElement;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpAttribute;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpReturn;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.base.idea.hyperf.HyperfIcons;
import com.base.idea.hyperf.HyperfSettings;
import com.base.idea.hyperf.HyperfStartupActivity;
import com.base.idea.hyperf.util.HyperfRootUtil;
import fr.adrienbrault.idea.symfony2plugin.Symfony2InterfacesUtil;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionLanguageRegistrar;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionProvider;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrarParameter;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.completion.CompletionContributorParameter;
import fr.adrienbrault.idea.symfony2plugin.util.MethodMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Hyperf 验证器规则名的补全（不做跳转）。
 *
 * <p>规则是框架预定义的字符串（如 {@code required|max:255}、{@code exists:table,column}），
 * 由 {@code Validator} 通过 {@code validate{Studly}} 方法分派；这里内置一份静态规则表提供提示。
 *
 * <p>触发位置（判定「当前字符串是不是验证规则数组的 value」）：
 * <ul>
 *   <li>{@code FormRequest::rules()} 返回数组里的规则字符串值；</li>
 *   <li>{@code ValidatorFactoryInterface::make()/validate()} 第 2 个参数（规则数组）里的字符串值；</li>
 *   <li>{@code FormRequest::$scenes} 属性里字符串键对应的规则字符串值。</li>
 * </ul>
 * 仅在项目安装 hyperf/validation（vendor/hyperf/validation 存在）且在设置中开启时启用。
 */
public class ValidationReferences implements GotoCompletionLanguageRegistrar {

    /** 匹配 ValidatorFactory 的 make/validate 方法调用（规则数组是第 2 个参数，index=1） */
    private static final MethodMatcher.CallToSignature[] VALIDATE = new MethodMatcher.CallToSignature[]{
            new MethodMatcher.CallToSignature("\\Hyperf\\Validation\\Contract\\ValidatorFactoryInterface", "make"),
            new MethodMatcher.CallToSignature("\\Hyperf\\Validation\\Contract\\ValidatorFactoryInterface", "validate"),
            new MethodMatcher.CallToSignature("\\Hyperf\\Validation\\ValidatorFactory", "make"),
            new MethodMatcher.CallToSignature("\\Hyperf\\Validation\\ValidatorFactory", "validate"),
    };

    /** FormRequest 基类 FQN，用于判断所在类是否为表单请求 */
    private static final String FORM_REQUEST = "\\Hyperf\\Validation\\Request\\FormRequest";

    /** DTO 验证注解 FQN：#[Validation('required|string')]，其 $rule 参数即规则字符串 */
    private static final String DTO_VALIDATION_ANNOTATION = "\\Hyperf\\DTO\\Annotation\\Validation\\Validation";

    /**
     * 内置验证规则表：[0]=规则名，[1]=参数占位符提示（tailText，空=无参数），[2]=中文说明（typeText），
     * [3]=选中后是否补 ":"（"1"=带参数规则，选中自动插入冒号便于填参；"0"/可选参数项=不插入）。
     *
     * <p>规则名取自 vendor/hyperf/validation 的 ValidatesAttributes 中的 validate* 方法
     * （lower_snake 形式），并附带 normalizeRule 的别名 int/bool；中文说明取自 validation 文档。
     * 带「可选参数」的规则（如 integer:strict、alpha:ascii）拆成基础项与带参项两条独立补全。
     */
    static final String[][] RULES = {
            {"accepted", "", "必须是 yes、on、1 或 true（同意协议）", "0"},
            {"accepted_if", "anotherfield,value,...", "另一字段等于指定值时必须是 yes、on、1 或 true", "1"},
            {"active_url", "", "必须是有 A 或 AAAA 记录的有效域名", "0"},
            {"after", "date", "必须是指定日期之后的值", "1"},
            {"after_or_equal", "date", "必须大于等于指定日期", "1"},
            {"alpha", "", "必须是字母（含中文）", "0"},
            {"alpha:ascii", "", "必须是 ASCII 字母（a-z、A-Z）", "0"},
            {"alpha_dash", "", "字母、数字、破折号和下划线（含中文）", "0"},
            {"alpha_dash:ascii", "", "ASCII 字母、数字、破折号和下划线", "0"},
            {"alpha_num", "", "必须是字母或数字（含中文）", "0"},
            {"alpha_num:ascii", "", "必须是 ASCII 字母或数字", "0"},
            {"array", "", "必须是 PHP 数组", "0"},
            {"array:key", "key,...", "必须是数组且至少包含指定的键", "0"},
            {"ascii", "", "必须完全是 7 位 ASCII 字符", "0"},
            {"bail", "", "首个规则失败后停止后续验证", "0"},
            {"before", "date", "必须是指定日期之前的值", "1"},
            {"before_or_equal", "date", "必须小于等于指定日期", "1"},
            {"between", "min,max", "大小必须在 min 和 max 之间", "1"},
            {"bool", "", "别名 → boolean", "0"},
            {"bool:strict", "", "别名 → boolean:strict", "0"},
            {"boolean", "", "必须可转为布尔值（true/false/1/0/\"1\"/\"0\"）", "0"},
            {"boolean:strict", "", "必须可转为布尔值（仅 true/false）", "0"},
            {"confirmed", "", "必须有匹配的 foo_confirmation 字段", "0"},
            {"contains", "value,...", "必须包含指定值", "1"},
            {"date", "", "必须是有效日期", "0"},
            {"date_equals", "date", "必须等于指定日期", "1"},
            {"date_format", "format", "必须匹配指定日期格式", "1"},
            {"decimal", "min,max", "必须是数值且含指定小数位数", "1"},
            {"declined", "", "必须是 no、off、0 或 false", "0"},
            {"declined_if", "anotherfield,value,...", "另一字段等于指定值时必须是 no、off、0 或 false", "1"},
            {"different", "field", "必须与指定字段的值不同", "1"},
            {"digits", "value", "必须是数字且长度等于 value", "1"},
            {"digits_between", "min,max", "数字长度必须介于 min 和 max 之间", "1"},
            {"dimensions", "min_width=100,ratio=3/2", "图片尺寸必须满足约束", "1"},
            {"distinct", "", "数组字段不能包含重复值", "0"},
            {"distinct:strict", "", "数组字段不能包含重复值（严格比较）", "0"},
            {"distinct:ignore_case", "", "数组字段不能包含重复值（忽略大小写）", "0"},
            {"doesnt_end_with", "value,...", "不能以给定值之一结尾", "1"},
            {"doesnt_start_with", "value,...", "不能以给定值之一开头", "1"},
            {"email", "", "必须是格式正确的邮箱地址", "0"},
            {"email:rfc", "", "邮箱（rfc 校验风格）", "0"},
            {"email:dns", "", "邮箱（含 DNS 校验）", "0"},
            {"email:strict", "", "邮箱（strict 校验风格）", "0"},
            {"email:filter", "", "邮箱（filter 校验风格）", "0"},
            {"ends_with", "value,...", "必须以某个给定值结尾", "1"},
            {"exclude", "", "validate/validated 中排除该字段", "0"},
            {"exclude_if", "anotherfield,value", "另一字段等于 value 时排除该字段", "1"},
            {"exclude_unless", "anotherfield,value", "除非另一字段等于 value，否则排除该字段", "1"},
            {"exclude_with", "field", "指定字段存在时排除该字段", "1"},
            {"exclude_without", "field", "指定字段不存在时排除该字段", "1"},
            {"exists", "table,column", "必须存在于指定数据表", "1"},
            {"extensions", "ext,...", "文件扩展名必须在给定列表中", "1"},
            {"file", "", "必须是上传成功的文件", "0"},
            {"filled", "", "存在时不能为空", "0"},
            {"gt", "field|value", "必须大于给定字段/值", "1"},
            {"gte", "field|value", "必须大于等于给定字段/值", "1"},
            {"hex_color", "", "必须是有效的十六进制颜色值", "0"},
            {"image", "", "必须是图片（jpeg/png/bmp/gif/svg）", "0"},
            {"in", "value,...", "值必须在给定列表中", "1"},
            {"in_array", "anotherfield", "值必须存在于另一字段的数组中", "1"},
            {"int", "", "别名 → integer", "0"},
            {"int:strict", "", "别名 → integer:strict", "0"},
            {"integer", "", "必须是整型（String 和 Integer 类型都可通过）", "0"},
            {"integer:strict", "", "必须是整型（仅 Integer 类型可通过）", "0"},
            {"ip", "", "必须是 IP 地址", "0"},
            {"ipv4", "", "必须是 IPv4 地址", "0"},
            {"ipv6", "", "必须是 IPv6 地址", "0"},
            {"json", "", "必须是有效的 JSON 字符串", "0"},
            {"list", "", "必须是列表数组", "0"},
            {"lowercase", "", "必须是小写", "0"},
            {"lt", "field|value", "必须小于给定字段/值", "1"},
            {"lte", "field|value", "必须小于等于给定字段/值", "1"},
            {"mac_address", "", "必须是 MAC 地址", "0"},
            {"max", "value", "必须小于等于最大值", "1"},
            {"max_digits", "value", "整数位数不能超过 value", "1"},
            {"mimes", "ext,...", "文件 MIME 类型必须是列出的扩展之一", "1"},
            {"mimetypes", "type,...", "文件必须匹配给定 MIME 类型之一", "1"},
            {"min", "value", "必须大于等于最小值", "1"},
            {"min_digits", "value", "整数至少有 value 位数", "1"},
            {"missing", "", "输入数据中必须不存在", "0"},
            {"missing_if", "anotherfield,value,...", "另一字段等于任一值时必须不存在", "1"},
            {"missing_unless", "anotherfield,value", "除非另一字段等于值，否则必须不存在", "1"},
            {"missing_with", "field,...", "任一指定字段存在时必须不存在", "1"},
            {"missing_with_all", "field,...", "所有指定字段都存在时必须不存在", "1"},
            {"multiple_of", "value", "必须是 value 的倍数", "1"},
            {"not_in", "value,...", "值不能在给定列表中", "1"},
            {"not_regex", "pattern", "不能匹配给定正则表达式", "1"},
            {"nullable", "", "字段可以为 null", "0"},
            {"numeric", "", "验证字段必须是数值", "0"},
            {"present", "", "必须出现在输入数据中但可以为空", "0"},
            {"prohibits", "field,...", "禁止指定字段存在", "1"},
            {"regex", "pattern", "必须匹配给定正则表达式", "1"},
            {"required", "", "验证字段值不能为空", "0"},
            {"required_array_keys", "key,...", "必须是数组且至少包含指定的键", "1"},
            {"required_if", "anotherfield,value,...", "另一字段等于指定值时必填", "1"},
            {"required_unless", "anotherfield,value,...", "除非另一字段等于值，否则必填", "1"},
            {"required_with", "field,...", "任一指定字段存在时必填", "1"},
            {"required_with_all", "field,...", "所有指定字段存在时必填", "1"},
            {"required_without", "field,...", "任一指定字段不存在时必填", "1"},
            {"required_without_all", "field,...", "所有指定字段都不存在时必填", "1"},
            {"same", "field", "必须与指定字段的值一致", "1"},
            {"size", "value", "大小必须与给定值匹配", "1"},
            {"sometimes", "", "字段存在时才进行验证", "0"},
            {"starts_with", "value,...", "必须以某个给定值开头", "1"},
            {"string", "", "必须是字符串", "0"},
            {"timezone", "", "必须是有效的时区标识", "0"},
            {"ulid", "", "必须是有效的 ULID", "0"},
            {"unique", "table,column,except,idColumn", "在指定数据表上必须唯一", "1"},
            {"uppercase", "", "必须是大写", "0"},
            {"url", "", "必须是有效的 URL", "0"},
            {"url:http", "", "必须是有效的 URL（限 http 协议）", "0"},
            {"url:https", "", "必须是有效的 URL（限 https 协议）", "0"},
            {"uuid", "", "必须是有效的 UUID", "0"},
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
            // 设置里关闭验证补全则整体短路
            if (!HyperfSettings.getInstance(psiElement.getProject()).validationEnabled) {
                return null;
            }
            // 未安装验证组件则不提供功能（hyperf/dto 的验证注解复用同一套规则，装了 dto 也放行）
            // 组件目录基于 Hyperf 应用根探测，支持应用在子目录的场景
            VirtualFile rootDir = HyperfRootUtil.resolve(psiElement.getProject());
            if (rootDir == null
                    || (VfsUtil.findRelativeFile(rootDir, "vendor", "hyperf", "validation") == null
                        && VfsUtil.findRelativeFile(rootDir, "vendor", "hyperf", "dto") == null)) {
                return null;
            }

            PsiElement parent = psiElement.getParent();
            if (!(parent instanceof StringLiteralExpression)) {
                return null;
            }

            // 三种场景任一命中：make()/validate() 的规则参数、rules() 返回值、$scenes 值
            if (isValidationRuleString((StringLiteralExpression) parent)) {
                return new ValidationRuleProvider(parent);
            }
            return null;
        });
    }

    /**
     * 判断字符串字面量是否为「验证规则字符串」。
     *
     * <p>规则字符串总是某个数组的 value（{@code 'field' => 'required|...'}）。先向上找到
     * 包裹它的 {@link ArrayHashElement}（确认它是 value 而非 key），再拿到所属
     * {@link ArrayCreationExpression}，最后判断该数组处于三种验证上下文之一。
     */
    public static boolean isValidationRuleString(@NotNull StringLiteralExpression literal) {
        // 场景四：DTO 验证注解的参数 #[Validation('required|string')] —— 注解参数不是数组 value，优先判断
        if (isValidationAnnotationArgument(literal)) {
            return true;
        }

        // 向上找最近的 ArrayHashElement（key=>value 之间还包了一层 ARRAY_VALUE 的 PhpPsiElementImpl，
        // 不能直接假设 literal.getParent() 就是 ArrayHashElement）
        ArrayHashElement hashElement = PsiTreeUtil.getParentOfType(literal, ArrayHashElement.class);
        if (hashElement == null) {
            return false;
        }
        // 字面量必须是 value（不是 key）
        if (hashElement.getValue() == null || !PsiTreeUtil.isAncestor(hashElement.getValue(), literal, false)) {
            return false;
        }

        PsiElement arrayExpr = hashElement.getParent();
        if (!(arrayExpr instanceof ArrayCreationExpression)) {
            return false;
        }

        boolean factory = isRulesArgumentOfFactory((ArrayCreationExpression) arrayExpr);
        boolean rulesRet = isRulesMethodReturn((ArrayCreationExpression) arrayExpr);
        boolean scenes = isScenesValue(literal, hashElement);
        return factory || rulesRet || scenes;
    }

    /**
     * 字符串是否是 DTO 验证注解 {@code #[Validation('...')]} 的 $rule 参数值。
     * 向上找最近的 {@link PhpAttribute}，比对注解类 FQN 即可（$rule 是该注解首个/唯一必填参数）。
     */
    private static boolean isValidationAnnotationArgument(@NotNull StringLiteralExpression literal) {
        PhpAttribute attribute = PsiTreeUtil.getParentOfType(literal, PhpAttribute.class);
        if (attribute == null) {
            return false;
        }
        String fqn = attribute.getFQN();
        return fqn != null && fqn.equalsIgnoreCase(DTO_VALIDATION_ANNOTATION);
    }

    /** make()/validate() 的规则数组参数：数组本身是第 2 个直接参数（index=1） */
    private static boolean isRulesArgumentOfFactory(@NotNull ArrayCreationExpression rulesArray) {
        return MethodMatcher.getMatchedSignatureWithDepth(rulesArray, VALIDATE, 1) != null;
    }

    /** 数组是 rules() 方法 return 语句的直接操作数，且所在类继承自 FormRequest */
    private static boolean isRulesMethodReturn(@NotNull ArrayCreationExpression rulesArray) {
        PhpReturn phpReturn = PsiTreeUtil.getParentOfType(rulesArray, PhpReturn.class);
        if (phpReturn == null) {
            return false;
        }
        // 数组必须是 return 的直接返回值，避免命中 getRules() 里 $this->rules() 这类嵌套调用
        if (phpReturn.getFirstPsiChild() != rulesArray) {
            return false;
        }
        Method method = PsiTreeUtil.getParentOfType(phpReturn, Method.class);
        if (method == null || !"rules".equals(method.getName())) {
            return false;
        }
        return isFormRequestClass(method.getContainingClass());
    }

    /** 字符串是 $scenes 属性里字符串键对应的值（'username' => 'string|required'），且所在类继承自 FormRequest */
    private static boolean isScenesValue(@NotNull StringLiteralExpression literal, @NotNull ArrayHashElement hashElement) {
        // 场景规则只出现在字符串键对应的 value 上（'tar' => ['username' => 'string|required']）
        if (!(hashElement.getKey() instanceof StringLiteralExpression)) {
            return false;
        }
        Field field = PsiTreeUtil.getParentOfType(literal, Field.class);
        return field != null && "scenes".equals(field.getName()) && isFormRequestClass(field.getContainingClass());
    }

    /** 所在类是否是 Hyperf 表单请求（FormRequest 的子类） */
    private static boolean isFormRequestClass(@Nullable PhpClass phpClass) {
        return phpClass != null && new Symfony2InterfacesUtil().isInstanceOf(phpClass, FORM_REQUEST);
    }

    /** 验证规则名的补全项提供者（仅补全，无跳转目标） */
    private static class ValidationRuleProvider extends GotoCompletionProvider {

        ValidationRuleProvider(PsiElement element) {
            super(element);
        }

        /**
         * 遍历静态规则表生成补全项。
         *
         * <p>展示：规则名 + 参数提示（tailText，仅基础项）+ 中文说明（typeText）。
         * 带参数规则（rule[3]="1"）选中后通过 InsertHandler 自动补一个 ":"，便于接着填参数；
         * 若字符串里规则名后已有 ":" 则不重复补。
         */
        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {
            final Collection<LookupElement> lookupElements = new ArrayList<>();
            for (String[] rule : RULES) {
                LookupElementBuilder builder = LookupElementBuilder.create(rule[0]).withIcon(HyperfIcons.VALIDATION);
                // 参数占位符紧跟规则名显示（仅当规则名本身不含 ":"，即基础项）；如 max → max:value
                if (StringUtil.isNotEmpty(rule[1]) && !rule[0].contains(":")) {
                    builder = builder.withTailText(":" + rule[1], true);
                }
                // 中文说明作为右侧 typeText，如 numeric → 验证字段必须是数值
                if (StringUtil.isNotEmpty(rule[2])) {
                    builder = builder.withTypeText(rule[2], true);
                }
                // 带参数规则选中后自动补 ":"，光标停在冒号后便于填参
                if ("1".equals(rule[3])) {
                    builder = builder.withInsertHandler((context, item) -> {
                        int tail = context.getTailOffset();
                        CharSequence text = context.getDocument().getCharsSequence();
                        // 已有冒号（如手工输入或规则名自带）则不重复补
                        if (tail < text.length() && text.charAt(tail) == ':') {
                            return;
                        }
                        context.getDocument().insertString(tail, ":");
                        context.getEditor().getCaretModel().moveToOffset(tail + 1);
                    });
                }
                lookupElements.add(builder);
            }
            return lookupElements;
        }

        /**
         * 用「最后一个 "|" 之后的子串」作为前缀补全。
         *
         * <p>规则常写作 {@code required|max:5}，IDE 默认会把 caret 前整段（含 {@code required|}）
         * 当作前缀，导致 {@code |} 后匹配不到。这里从真实文本重算 caret 前、最后一个 {@code |}
         * 之后的前缀，用 {@code withPrefixMatcher} 重新过滤；并依赖 {@code withItemMerged} 去重
         * （基类 {@link GotoCompletionProvider#getLookupElements()} 的无参结果会先被加入一次）。
         */
        @Override
        public void getLookupElements(CompletionContributorParameter parameter) {
            String prefix = getPrefixAfterLastPipe(parameter);
            if (prefix == null) {
                return;
            }
            CompletionResultSet resultSet = parameter.getCompletionResultSet().withPrefixMatcher(prefix);
            resultSet.addAllElements(getLookupElements());
        }

        /**
         * 计算 caret 前、最后一个 "|" 之后的文本前缀。
         *
         * <p>用 {@code getOriginalPosition()}（真实文件里的元素）配合 caret 偏移从真实文本截取，
         * 避免补全虚拟副本中 "IntellijIdeaRulezzz" 占位符的干扰。取不到时返回 null。
         */
        @Nullable
        private String getPrefixAfterLastPipe(@NotNull CompletionContributorParameter parameter) {
            PsiElement original = parameter.getCompletionParameters().getOriginalPosition();
            if (!(original != null && original.getParent() instanceof StringLiteralExpression)) {
                return null;
            }
            StringLiteralExpression literal = (StringLiteralExpression) original.getParent();

            // caret 在字符串内容内的偏移（原文件 offset - 字面量起始 - 前引号宽度）
            int caretInContent = parameter.getCompletionParameters().getOffset()
                    - literal.getTextRange().getStartOffset() - 1;
            String contents = literal.getContents();
            if (caretInContent < 0 || caretInContent > contents.length()) {
                return null;
            }
            String beforeCaret = contents.substring(0, caretInContent);
            int pipe = beforeCaret.lastIndexOf('|');
            return pipe < 0 ? beforeCaret : beforeCaret.substring(pipe + 1);
        }
    }
}
