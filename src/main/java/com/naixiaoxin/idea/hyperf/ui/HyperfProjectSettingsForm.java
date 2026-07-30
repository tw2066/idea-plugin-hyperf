package com.naixiaoxin.idea.hyperf.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.naixiaoxin.idea.hyperf.HyperfSettings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Hyperf 插件的项目设置页（Settings → Hyperf Plugin）。
 *
 * <p>UI 用纯 Swing 代码构建：原 .form 文件依赖 instrumentCode 字节码织入，
 * 而当前构建已禁用该任务（非 JBR JDK 不支持），.form 会导致面板为 null、
 * 设置页一直停在 "正在加载..."。
 *
 * <p>提供三项配置：是否启用、翻译文件目录（translationPath）、
 * 优先跳转的翻译语言（translationLang）。
 *
 * @author NaiXiaoXin(SeanWang) <i@naixiaoxin.com>
 */
public class HyperfProjectSettingsForm implements Configurable {

    private final Project project;

    private JCheckBox enabled;
    private JTextField textTranslationLang;
    private JTextField textTranslationPath;

    public HyperfProjectSettingsForm(@NotNull final Project project) {
        this.project = project;
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "Hyperf Plugin";
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    /**
     * 构建设置面板：顶部 "启用" 复选框，下方 翻译路径 / 翻译语言 两个输入框。
     * 返回前先从持久化设置回填一次，避免打开时显示空值。
     */
    @Nullable
    @Override
    public JComponent createComponent() {
        enabled = new JCheckBox("Enable plugin for this project");
        textTranslationPath = new JTextField();
        textTranslationLang = new JTextField();
        textTranslationLang.setToolTipText("If you have more than one language in project, this one will be first in \"Go to\" translation options");

        // 字段区：GridBagLayout，标签列 + 输入列
        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints label = new GridBagConstraints();
        label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(2, 0, 2, 8);
        label.gridx = 0;

        GridBagConstraints field = new GridBagConstraints();
        field.anchor = GridBagConstraints.WEST;
        field.fill = GridBagConstraints.HORIZONTAL;
        field.weightx = 1.0;
        field.insets = new Insets(2, 0, 2, 0);
        field.gridx = 1;

        label.gridy = 0;
        field.gridy = 0;
        fields.add(new JLabel("Translation Path"), label);
        fields.add(textTranslationPath, field);

        label.gridy = 1;
        field.gridy = 1;
        fields.add(new JLabel("Translation Lang"), label);
        fields.add(textTranslationLang, field);

        // 垂直弹簧，把内容顶到面板上方
        GridBagConstraints glue = new GridBagConstraints();
        glue.gridx = 0;
        glue.gridy = 2;
        glue.gridwidth = 2;
        glue.weighty = 1.0;
        glue.fill = GridBagConstraints.VERTICAL;
        fields.add(Box.createVerticalGlue(), glue);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(enabled, BorderLayout.NORTH);
        panel.add(fields, BorderLayout.CENTER);

        updateUIFromSettings();
        return panel;
    }

    /** 任一字段与持久化值不同即视为已修改 */
    @Override
    public boolean isModified() {
        return enabled.isSelected() != getSettings().pluginEnabled
                || !textTranslationLang.getText().equals(getSettings().translationLang)
                || !textTranslationPath.getText().equals(getSettings().translationPath);
    }

    /** Apply 按钮：把界面值写回持久化设置 */
    @Override
    public void apply() throws ConfigurationException {
        getSettings().pluginEnabled = enabled.isSelected();
        getSettings().translationLang = textTranslationLang.getText();
        getSettings().translationPath = textTranslationPath.getText();
    }

    /** Reset 按钮：用持久化值刷新界面 */
    @Override
    public void reset() {
        updateUIFromSettings();
    }

    /** 从设置读取并回填到控件；控件未创建时直接返回 */
    private void updateUIFromSettings() {
        if (enabled == null) {
            return;
        }
        enabled.setSelected(getSettings().pluginEnabled);
        textTranslationLang.setText(getSettings().translationLang);
        textTranslationPath.setText(getSettings().translationPath);
    }

    @Override
    public void disposeUIResources() {
    }

    private HyperfSettings getSettings() {
        return HyperfSettings.getInstance(this.project);
    }

    /** 打开 Settings 并定位到本设置页（用于通知气泡里的 "打开设置" 链接） */
    public static void show(@NotNull Project project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "Hyperf.SettingsForm");
    }
}
