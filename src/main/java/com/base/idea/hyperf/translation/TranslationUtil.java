package com.base.idea.hyperf.translation;

import com.intellij.openapi.project.Project;
import com.base.idea.hyperf.HyperfSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 翻译文件路径解析工具。
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class TranslationUtil {

    /**
     * 从翻译文件路径解析命名空间（即文件名，不含扩展名）。
     *
     * <p>路径形如 {@code <翻译根目录>/<语言>/<命名空间>.php}，
     * 例如 {@code storage/languages/zh_CN/message.php}，返回 "message"。
     * 语言段支持两位（zh）或 语言_地区（zh_CN / zh-CN）两种格式。
     *
     * @param path    文件绝对路径
     * @param project 当前项目（读取配置的翻译根目录）
     * @return 命名空间；不在翻译目录或层级过深（疑似误判）时返回 null
     */
    @Nullable
    public static String getNamespaceFromFilePath(@NotNull String path, Project project) {
        Pattern pattern = Pattern.compile(".*" + HyperfSettings.getInstance(project).translationPath + "/(\\w{2}|\\w{2}[_|-]\\w{2})/(.*)\\.php$");
        Matcher matcher = pattern.matcher(path);
        if (!matcher.find()) {
            return null;
        }

        String namespace = matcher.group(2);

        // invalid nested translation secure check
        // eg project name conflicts with pattern
        // 层级过深则可能是路径误匹配（如项目名与规则冲突），放弃
        if (namespace.split("/").length > 3) {
            return null;
        }

        return namespace;

    }
}
