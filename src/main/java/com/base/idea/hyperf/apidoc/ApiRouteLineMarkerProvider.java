package com.base.idea.hyperf.apidoc;

import com.base.idea.hyperf.HyperfIcons;
import com.base.idea.hyperf.HyperfStartupActivity;
import com.base.idea.hyperf.util.IdeHelper;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;
import com.jetbrains.php.lang.lexer.PhpTokenTypes;
import com.jetbrains.php.lang.psi.elements.Method;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.List;

/**
 * 路由方法(x-code-path 指向的 PHP 方法)名旁的 "API" gutter 图标。
 *
 * <p>点击:单路由直接生成 .http 请求;多路由弹列表选择。
 * 命中判断只用 http.json 缓存数据,不依赖索引,故可实现 DumbAware。
 */
public class ApiRouteLineMarkerProvider implements LineMarkerProvider, DumbAware {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // 只处理方法名标识符本身(每个叶子元素都会回调,先廉价过滤)
        if (element.getNode() == null || element.getNode().getElementType() != PhpTokenTypes.IDENTIFIER) {
            return null;
        }
        PsiElement parent = element.getParent();
        if (!(parent instanceof Method) || ((Method) parent).getNameIdentifier() != element) {
            return null;
        }
        Project project = element.getProject();
        if (!HyperfStartupActivity.isEnabled(project)) {
            return null;
        }
        List<ApiRoute> routes = ApiDocService.getInstance(project).findRoutesForMethod((Method) parent);
        if (routes.isEmpty()) {
            return null;
        }
        String tooltip = routes.size() == 1
                ? routes.get(0).httpMethod + " " + routes.get(0).path
                : routes.size() + " API routes";
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                HyperfIcons.API,
                e -> tooltip,
                (GutterIconNavigationHandler<PsiElement>) (event, elt) -> onGutterClick(project, routes, event),
                GutterIconRenderer.Alignment.RIGHT,
                () -> tooltip
        );
    }

    private static void onGutterClick(@NotNull Project project, @NotNull List<ApiRoute> routes, @NotNull MouseEvent event) {
        String baseUrl = ApiDocService.getInstance(project).getBaseUrl(project);
        if (baseUrl == null) {
            IdeHelper.notifyWarning(project, "No servers url found in http.json");
            return;
        }
        if (routes.size() == 1) {
            ApiRequestScratchWriter.appendAndOpen(project, routes.get(0), baseUrl);
            return;
        }
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<ApiRoute>("Select Route", routes) {
            @Override
            public @NotNull String getTextFor(ApiRoute value) {
                return value.httpMethod + " " + value.path;
            }

            @Override
            public PopupStep<?> onChosen(ApiRoute selectedValue, boolean finalChoice) {
                ApiRequestScratchWriter.appendAndOpen(project, selectedValue, baseUrl);
                return FINAL_CHOICE;
            }
        }).show(new RelativePoint(event));
    }
}
