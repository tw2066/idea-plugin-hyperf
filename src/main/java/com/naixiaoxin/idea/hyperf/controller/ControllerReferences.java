package com.naixiaoxin.idea.hyperf.controller;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.PhpPresentationUtil;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.jetbrains.php.lang.psi.elements.impl.PhpPsiElementImpl;
import com.naixiaoxin.idea.hyperf.HyperfIcons;
import com.naixiaoxin.idea.hyperf.HyperfStartupActivity;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionContributor;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionLanguageRegistrar;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionProvider;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrarParameter;
import fr.adrienbrault.idea.symfony2plugin.util.MethodMatcher;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * 路由中 "Controller@method" / "Controller::method" 字符串的补全与跳转。
 *
 * <p>覆盖两类 Router 调用：
 * <ul>
 *   <li>{@code Router::get/post/put/patch/delete/options('/path', 'App\Controller\X@y')} — 第 2 个参数；</li>
 *   <li>{@code Router::addRoute([...], '/path', 'App\Controller\X@y')} — 第 3 个参数。</li>
 * </ul>
 * 补全列表来自 {@link ControllerCollector} 收集的所有控制器 action。
 *
 * @author NaiXiaoXin(SeanWang) <i@naixiaoxin.com>
 */
public class ControllerReferences implements GotoCompletionLanguageRegistrar {

    // 定义路由函数
    /** 匹配 Router 的 REST 方法调用（handler 为第 2 个参数，index=1） */
    private static MethodMatcher.CallToSignature[] ROUTE = new MethodMatcher.CallToSignature[]{
            new MethodMatcher.CallToSignature("\\Hyperf\\HttpServer\\Router\\Router", "get"),
            new MethodMatcher.CallToSignature("\\Hyperf\\HttpServer\\Router\\Router", "post"),
            new MethodMatcher.CallToSignature("\\Hyperf\\HttpServer\\Router\\Router", "put"),
            new MethodMatcher.CallToSignature("\\Hyperf\\HttpServer\\Router\\Router", "patch"),
            new MethodMatcher.CallToSignature("\\Hyperf\\HttpServer\\Router\\Router", "delete"),
            new MethodMatcher.CallToSignature("\\Hyperf\\HttpServer\\Router\\Router", "options"),
    };

    /** 匹配 Router::addRoute（handler 为第 3 个参数，index=2） */
    private static MethodMatcher.CallToSignature[] RouteAddRoute = new MethodMatcher.CallToSignature[]{
            new MethodMatcher.CallToSignature("\\Hyperf\\HttpServer\\Router\\Router", "addRoute"),

    };

    @Override
    public boolean support(@NotNull Language language) {
        return PhpLanguage.INSTANCE == language;
    }

    @Override
    public void register(GotoCompletionRegistrarParameter registrar) {
        registrar.register(PlatformPatterns.psiElement().withParent(StringLiteralExpression.class), new GotoCompletionContributor() {

            @Nullable
            @Override
            public GotoCompletionProvider getProvider(@Nullable PsiElement psiElement) {
                if (!HyperfStartupActivity.isEnabled(psiElement)) {
                    return null;
                }
                PsiElement parent = psiElement.getParent();
                if (!(parent instanceof StringLiteralExpression)) {
                    return null;
                }
                //Router::get('/hello-hyperf', 'App\Controller\IndexController::hello');
                //Router::get('/hello-hyperf', 'App\Controller\IndexController@hello');
                if (MethodMatcher.getMatchedSignatureWithDepth(parent, ROUTE, 1) != null) {
                    return createRouteCompletion(parent);
                }
                //Router::addRoute(['GET', 'POST', 'HEAD'], '/', 'App\Controller\IndexController@index');
                if (MethodMatcher.getMatchedSignatureWithDepth(parent, RouteAddRoute, 2) != null) {
                    return createRouteCompletion(parent);
                }
                return null;

            }
        });
    }

    private ControllerRoute createRouteCompletion(@NotNull PsiElement element) {
        return new ControllerRoute(element);
    }

    /** 路由 handler 字符串的补全项与跳转目标提供者 */
    private static class ControllerRoute extends GotoCompletionProvider {


        ControllerRoute(PsiElement element) {
            super(element);

        }

        /** 遍历所有控制器 action，生成 "FQN@method" 形式的补全项 */
        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {
            // 自动补全
            final Collection<LookupElement> lookupElements = new ArrayList<>();
            ControllerCollector.visitControllerActions(getProject(), (phpClass, method) -> {
                        String controllerFunction = phpClass.getFQN() + "@" + method.getName();
                        // 去掉前导反斜杠，补全项使用不带 \ 的形式
                        if (StringUtil.startsWith(controllerFunction, "\\")) {
                            controllerFunction = StringUtil.trimStart(controllerFunction, "\\");
                        }
                        LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(controllerFunction)
                                .withIcon(HyperfIcons.CONTROLLER);

                        // 方法有参数时在补全项尾部显示参数签名
                        Parameter[] parameters = method.getParameters();
                        if (parameters.length > 0) {
                            lookupElementBuilder = lookupElementBuilder.withTailText(PhpPresentationUtil.formatParameters(null, parameters).toString());
                        }

                        LookupElement lookupElement = lookupElementBuilder;

                        lookupElements.add(lookupElement);
                    }
            );

            return lookupElements;
        }

        /** 解析 "Controller@method" / "Controller::method"，定位到目标方法作为跳转目标 */
        @NotNull
        @Override
        public Collection<PsiElement> getPsiTargets(final StringLiteralExpression element) {

            final String content = element.getContents();
            if (StringUtil.isEmptyOrSpaces(content)) {
                return Collections.emptyList();
            }

            String[] controllerSplit = null;
            final Collection<PsiElement> targets = new ArrayList<>();
            // 判断是否存在:: 或者是@
            if (content.contains("@")) {
                // 存在@
                controllerSplit = content.split("@");

            }
            if (content.contains("::")) {
                // 存在::
                controllerSplit = content.split("::");
            }

            // 必须正好拆成 "类名 + 方法名" 两段，否则无法解析
            if (controllerSplit == null || controllerSplit.length != 2) {
                return targets;
            }
            String controllerName = controllerSplit[0];
            // 补全Controller的类名（PhpIndex 按 FQN 查询需要前导反斜杠）
            if (!StringUtil.startsWith(controllerName, "\\")) {
                controllerName = "\\" + controllerName;
            }
            Collection<PhpClass> controllerClass = PhpIndex.getInstance(getProject()).getClassesByFQN(controllerName);
            for (PhpClass phpClass : controllerClass) {
                Method method = phpClass.findMethodByName(controllerSplit[1]);
                if (method == null) {
                    continue;
                }
                // 静态或非 public 方法不可作为路由 action，跳过
                if (method.isStatic() || !method.getAccess().isPublic()) {
                    continue;
                }
                targets.add(method);
            }
            return targets;
        }
    }

}
