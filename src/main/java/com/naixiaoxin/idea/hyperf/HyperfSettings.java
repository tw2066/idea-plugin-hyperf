package com.naixiaoxin.idea.hyperf;

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

    /** 翻译默认语言（跳转翻译时优先匹配的语言目录名） */
    public String translationLang = "zh_CN";
    /** 翻译文件根目录（相对项目根目录，Hyperf 默认为 /storage/languages） */
    public String translationPath = "/storage/languages";


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