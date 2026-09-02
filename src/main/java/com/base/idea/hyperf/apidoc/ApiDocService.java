package com.base.idea.hyperf.apidoc;

import com.base.idea.hyperf.HyperfSettings;
import com.base.idea.hyperf.util.HyperfRootUtil;
import com.base.idea.hyperf.util.IdeHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * apidocs 组件生成的 http.json(openapi 3.0)读取服务。
 *
 * <p>读文件走 java.nio 而非 VFS:框架运行时刚写的文件 VFS 可能从未见过,
 * Path + mtime 双键缓存彻底规避刷新问题,设置项变更也因路径不同自然失效。
 */
public final class ApiDocService {

    private static final String DEFAULT_PATH = "runtime/container/http.json";
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete", "options", "head");

    private Path cachedPath;
    private FileTime cachedStamp;
    private ApiDocModel cachedModel = ApiDocModel.EMPTY;
    /** 已警告过的 stamp,损坏 JSON 按版本只提示一次 */
    private FileTime warnedStamp;

    public static ApiDocService getInstance(@NotNull Project project) {
        return project.getService(ApiDocService.class);
    }

    /**
     * 解析 http.json 路径:设置项为空 → 应用根 runtime/container/http.json;
     * 绝对路径原样使用;否则相对 Hyperf 应用根解析。
     */
    public @Nullable Path resolveHttpJsonPath(@NotNull Project project) {
        VirtualFile appRoot = HyperfRootUtil.resolve(project);
        if (appRoot == null) {
            return null;
        }
        String configured = HyperfSettings.getInstance(project).httpJsonPath.trim();
        if (configured.isEmpty()) {
            return Paths.get(appRoot.getPath(), DEFAULT_PATH.split("/"));
        }
        Path path = Paths.get(configured);
        if (path.isAbsolute()) {
            return path;
        }
        return Paths.get(appRoot.getPath()).resolve(path).normalize();
    }

    /** 读取并缓存解析结果;文件不存在/损坏时返回 EMPTY(功能静默关闭) */
    public synchronized @NotNull ApiDocModel getModel(@NotNull Project project) {
        Path path = resolveHttpJsonPath(project);
        if (path == null) {
            return ApiDocModel.EMPTY;
        }
        FileTime stamp;
        try {
            stamp = Files.getLastModifiedTime(path);
        } catch (NoSuchFileException e) {
            return cache(path, null, ApiDocModel.EMPTY);
        } catch (IOException e) {
            return cache(path, null, ApiDocModel.EMPTY);
        }
        if (path.equals(cachedPath) && stamp.equals(cachedStamp)) {
            return cachedModel;
        }
        ApiDocModel model;
        try {
            model = parse(Files.readString(path));
        } catch (Exception e) {
            model = ApiDocModel.EMPTY;
            if (!Objects.equals(warnedStamp, stamp)) {
                warnedStamp = stamp;
                IdeHelper.notifyWarning(project, "Failed to parse " + path + ": " + e.getMessage());
            }
        }
        return cache(path, stamp, model);
    }

    public @NotNull List<ApiRoute> findByCodePath(@NotNull Project project, @NotNull String codePath) {
        return getModel(project).findByCodePath(codePath);
    }

    /** 查 PHP 方法对应的所有路由(由 Method 拼 codePath 反查) */
    public @NotNull List<ApiRoute> findRoutesForMethod(@NotNull Method method) {
        PhpClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return List.of();
        }
        String codePath = StringUtil.trimStart(containingClass.getFQN(), "\\") + "::" + method.getName();
        return findByCodePath(method.getProject(), codePath);
    }

    public @Nullable String getBaseUrl(@NotNull Project project) {
        return getModel(project).baseUrl;
    }

    private ApiDocModel cache(@NotNull Path path, @Nullable FileTime stamp, @NotNull ApiDocModel model) {
        cachedPath = path;
        cachedStamp = stamp;
        cachedModel = model;
        return model;
    }

    private static @NotNull ApiDocModel parse(@NotNull String content) {
        JsonObject root = JsonParser.parseString(content).getAsJsonObject();
        String baseUrl = parseBaseUrl(root);
        JsonElement paths = root.get("paths");
        if (paths == null || !paths.isJsonObject()) {
            return new ApiDocModel(baseUrl, List.of());
        }
        List<ApiRoute> routes = new ArrayList<>();
        for (Map.Entry<String, JsonElement> pathEntry : paths.getAsJsonObject().entrySet()) {
            if (!pathEntry.getValue().isJsonObject()) {
                continue;
            }
            String apiPath = pathEntry.getKey();
            for (Map.Entry<String, JsonElement> methodEntry : pathEntry.getValue().getAsJsonObject().entrySet()) {
                String httpMethod = methodEntry.getKey().toLowerCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(httpMethod) || !methodEntry.getValue().isJsonObject()) {
                    continue;
                }
                JsonElement codePath = methodEntry.getValue().getAsJsonObject().get("x-code-path");
                if (codePath == null || !codePath.isJsonPrimitive()) {
                    continue;
                }
                String codePathValue = codePath.getAsString();
                int idx = codePathValue.indexOf("::");
                if (idx <= 0 || idx >= codePathValue.length() - 2) {
                    continue;
                }
                routes.add(new ApiRoute(httpMethod.toUpperCase(Locale.ROOT), apiPath, codePathValue));
            }
        }
        return new ApiDocModel(baseUrl, routes);
    }

    private static @Nullable String parseBaseUrl(@NotNull JsonObject root) {
        JsonElement servers = root.get("servers");
        if (servers == null || !servers.isJsonArray() || servers.getAsJsonArray().size() == 0) {
            return null;
        }
        JsonElement first = servers.getAsJsonArray().get(0);
        if (!first.isJsonObject()) {
            return null;
        }
        JsonElement url = first.getAsJsonObject().get("url");
        if (url == null || !url.isJsonPrimitive()) {
            return null;
        }
        String baseUrl = url.getAsString();
        return StringUtil.trimEnd(baseUrl, "/");
    }
}
