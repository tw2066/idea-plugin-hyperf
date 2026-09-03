package com.base.idea.hyperf;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;


/**
 * 插件项目级设置（持久化到 hyperf-plugin.xml）。
 *
 * <p>通过 {@code project.getService(HyperfSettings.class)} 获取，
 * 保存插件启用状态、翻译语言与翻译目录路径等配置。
 *
 * @author NaiXiaoXin(SeanWang) <i@naixiaoxin.com>
 */
@State(
        name = "HyperfPluginSettings",
        storages = {
                @Storage("hyperf-plugin.xml")
        }
)
public class HyperfSettings implements PersistentStateComponent<HyperfSettings> {

    /** 是否为当前项目启用 Hyperf 插件 */
    public boolean pluginEnabled = false;


    /** 用户是否已关闭"启用插件"的提示通知（不再重复弹出） */
    public boolean dismissEnableNotification = false;

    /** 是否启用 env() 环境变量键的补全与跳转（默认开启） */
    public boolean envEnabled = true;

    /** 是否启用验证器规则名的补全（默认开启） */
    public boolean validationEnabled = true;

    /** 是否显示主菜单栏的 Hyperf 菜单（代码生成与快捷命令，默认开启） */
    public boolean menuEnabled = true;

    /** 翻译默认语言（跳转翻译时优先匹配的语言目录名） */
    public String translationLang = "zh_CN";
    /** 翻译文件根目录（相对项目根目录，Hyperf 默认为 /storage/languages） */
    public String translationPath = "/storage/languages";

    /** PHP 可执行文件路径（留空则依次回退：项目 CLI 解释器 → PATH 中的 php） */
    public String phpBinaryPath = "";

    /** apidocs 生成的 http.json 路径（留空 = 应用根 runtime/container/http.json；支持绝对路径或相对应用根） */
    public String httpJsonPath = "";


    public static HyperfSettings getInstance(@NotNull Project project) {
        return project.getService(HyperfSettings.class);
    }


    @Override
    public HyperfSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull HyperfSettings settings) {
        XmlSerializerUtil.copyBean(settings, this);
    }

}