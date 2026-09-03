package com.base.idea.hyperf.apidoc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * http.json 的解析结果:base url(servers[0].url)+ 路由列表。
 * byCodePath 以 小写 codePath 为键(PHP 类名不区分大小写)。
 */
public final class ApiDocModel {

    public static final ApiDocModel EMPTY = new ApiDocModel(null, Collections.emptyList());

    public final @Nullable String baseUrl;
    public final @NotNull List<ApiRoute> routes;

    private final Map<String, List<ApiRoute>> byCodePath = new HashMap<>();

    public ApiDocModel(@Nullable String baseUrl, @NotNull List<ApiRoute> routes) {
        this.baseUrl = baseUrl;
        this.routes = routes;
        for (ApiRoute route : routes) {
            byCodePath.computeIfAbsent(route.codePath.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(route);
        }
    }

    public @NotNull List<ApiRoute> findByCodePath(@NotNull String codePath) {
        List<ApiRoute> hit = byCodePath.get(codePath.toLowerCase(Locale.ROOT));
        return hit == null ? Collections.emptyList() : hit;
    }
}
