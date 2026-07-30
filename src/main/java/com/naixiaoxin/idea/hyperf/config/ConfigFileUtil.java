package com.naixiaoxin.idea.hyperf.config;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hyperf 配置文件路径匹配工具。
 *
 * <p>根据文件相对项目根目录的路径，判断是否为 Hyperf 配置文件，
 * 并计算该文件内数组键在索引中应携带的前缀：
 * <ul>
 *   <li>{@code config/autoload/app.php} → 前缀 "app"（键形如 "app.xxx"）；</li>
 *   <li>{@code config/config.php}      → 无前缀（键即为顶层键）。</li>
 * </ul>
 */
public class ConfigFileUtil {
    /** 匹配 config/autoload/ 下的配置文件，捕获相对路径作为前缀（多级目录与文件名中的 "." 均转为 "." 分隔）。
     *  捕获组需包含 "."：自 Hyperf 3.1 起支持文件名含 "." 的配置（如 a.b.php → 前缀 a.b）。 */
    private static final Pattern configFilePattern = Pattern.compile(".*/config/autoload/([\\w./-]+)\\.php$");

    /** 匹配根配置文件 config/config.php */
    private static final Pattern configFile = Pattern.compile(".*/config/config.php$");


    /**
     * 判断给定文件是否为 Hyperf 配置文件。
     *
     * @param project     当前项目（用于取项目根路径）
     * @param virtualFile 待匹配的文件
     * @return 匹配结果；非配置文件返回 {@link ConfigFileMatchResult#NO_MATCH}
     */
    public static ConfigFileMatchResult matchConfigFile(Project project, VirtualFile virtualFile) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return ConfigFileMatchResult.NO_MATCH;
        }
        String projectPath = baseDir.getPath();
        // 去掉项目根路径前缀，得到文件相对路径用于匹配
        String path = StringUtil.trimStart(virtualFile.getPath(), projectPath);

        Matcher m = configFilePattern.matcher(path);

        // config/config.php
        // config/autoload/app.php
        if (m.matches()) {
            return new ConfigFileMatchResult(true, m.group(1).replace('/', '.'));
        }
        m = configFile.matcher(path);

        if (m.matches()) {
            return new ConfigFileMatchResult(true, "");
        }

        return ConfigFileMatchResult.NO_MATCH;
    }

    /** 配置文件匹配结果：是否匹配 + 键前缀 */
    public static class ConfigFileMatchResult {
        static final ConfigFileMatchResult NO_MATCH = new ConfigFileMatchResult(false, "");

        private boolean matches;

        private String keyPrefix;

        ConfigFileMatchResult(boolean matches, @NotNull String keyPrefix) {
            this.matches = matches;
            this.keyPrefix = keyPrefix;
        }

        public boolean matches() {
            return matches;
        }

        /** 该配置文件的键前缀（如 "app"），根配置文件返回空串 */
        @NotNull
        public String getKeyPrefix() {
            return keyPrefix;
        }
    }
}
