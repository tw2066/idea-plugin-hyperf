package com.base.idea.hyperf.apidoc;

import com.base.idea.hyperf.HyperfStartupActivity;
import com.base.idea.hyperf.util.IdeHelper;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.Processor;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/**
 * Search Everywhere "Routes" 独立 tab:按路由路径/方法名过滤 http.json 中的路由,
 * 回车跳转到 x-code-path 指向的 PHP 方法。
 *
 * <p>兼容性:getDataForItem 在 233 平台是 abstract(262 才变 default),必须显式实现;
 * 不覆写 isDumbAware()(默认 false),索引期间整个 tab 随平台 Class/Symbol tab 一起隐藏。
 */
public class ApiRouteSearchEverywhereContributor implements SearchEverywhereContributor<ApiRoute> {

    private static final int MAX_RESULTS = 200;

    private final @Nullable Project project;

    public ApiRouteSearchEverywhereContributor(@NotNull AnActionEvent initEvent) {
        this.project = initEvent.getProject();
    }

    @Override
    public @NotNull String getSearchProviderId() {
        return "HyperfApiRoutes";
    }

    @Override
    public @NotNull String getGroupName() {
        return "Routes";
    }

    @Override
    public int getSortWeight() {
        return 800;
    }

    @Override
    public boolean showInFindResults() {
        return false;
    }

    @Override
    public boolean isEmptyPatternSupported() {
        return true;
    }

    @Override
    public void fetchElements(@NotNull String pattern, @NotNull ProgressIndicator progressIndicator,
                              @NotNull Processor<? super ApiRoute> consumer) {
        if (project == null) {
            return;
        }
        String needle = pattern.trim().toLowerCase(Locale.ROOT);
        int count = 0;
        for (ApiRoute route : ApiDocService.getInstance(project).getModel(project).routes) {
            progressIndicator.checkCanceled();
            if (!needle.isEmpty()) {
                String haystack = (route.httpMethod + " " + route.path + " " + route.codePath).toLowerCase(Locale.ROOT);
                if (!haystack.contains(needle)) {
                    continue;
                }
            }
            if (!consumer.process(route) || ++count >= MAX_RESULTS) {
                return;
            }
        }
    }

    @Override
    public boolean processSelectedItem(@NotNull ApiRoute selected, int modifiers, @NotNull String searchText) {
        if (project == null) {
            return false;
        }
        if (DumbService.isDumb(project)) {
            IdeHelper.notifyWarning(project, "Index is not ready, please try again later");
            return false;
        }
        Method method = null;
        for (PhpClass phpClass : PhpIndex.getInstance(project).getClassesByFQN("\\" + selected.getClassFqn())) {
            method = phpClass.findMethodByName(selected.getMethodName());
            if (method != null) {
                break;
            }
        }
        if (method == null) {
            IdeHelper.notifyWarning(project, "Cannot resolve " + selected.codePath);
            return true;
        }
        OpenSourceUtil.navigate(true, method);
        return true;
    }

    @Override
    public @NotNull ListCellRenderer<? super ApiRoute> getElementsRenderer() {
        return new Renderer();
    }

    /** 233 平台该方法是 abstract(262 才变 default),必须显式实现 */
    @Override
    public @Nullable Object getDataForItem(@NotNull ApiRoute element, @NotNull String dataId) {
        return null;
    }

    /** 左:粗体 METHOD + 路径;右:灰字 FQN::method */
    private static class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ApiRoute) {
                ApiRoute route = (ApiRoute) value;
                setText("<html><b>" + route.httpMethod + "</b> " + route.path
                        + " <font color='#888888'>" + route.codePath + "</font></html>");
            }
            return this;
        }
    }

    public static class Factory implements SearchEverywhereContributorFactory<ApiRoute> {
        @Override
        public @NotNull SearchEverywhereContributor<ApiRoute> createContributor(@NotNull AnActionEvent initEvent) {
            return new ApiRouteSearchEverywhereContributor(initEvent);
        }

        @Override
        public boolean isAvailable(@NotNull Project project) {
            return HyperfStartupActivity.isEnabled(project);
        }
    }
}
