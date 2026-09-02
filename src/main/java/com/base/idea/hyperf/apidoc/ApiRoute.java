package com.base.idea.hyperf.apidoc;

import org.jetbrains.annotations.NotNull;

/**
 * http.json 中的一条路由:HTTP 方法 + 路径 + x-code-path(FQN::method)。
 */
public class ApiRoute {

    /** HTTP 方法(大写,如 POST) */
    public final String httpMethod;
    /** 路由路径(如 /rpc/user) */
    public final String path;
    /** x-code-path 原值(如 App\Controller\IndexController::index) */
    public final String codePath;

    public ApiRoute(@NotNull String httpMethod, @NotNull String path, @NotNull String codePath) {
        this.httpMethod = httpMethod;
        this.path = path;
        this.codePath = codePath;
    }

    /** 控制器 FQN(不带前导反斜杠) */
    public @NotNull String getClassFqn() {
        int idx = codePath.indexOf("::");
        return idx > 0 ? codePath.substring(0, idx) : codePath;
    }

    /** 方法名 */
    public @NotNull String getMethodName() {
        int idx = codePath.indexOf("::");
        return idx >= 0 ? codePath.substring(idx + 2) : "";
    }

    /** 生成 HTTP Client 请求行,如 POST http://127.0.0.1:9501/rpc/user */
    public @NotNull String getRequestLine(@NotNull String baseUrl) {
        return httpMethod + " " + baseUrl + path;
    }
}
