package com.base.idea.hyperf.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.base.idea.hyperf.HyperfSettings;
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
    private JCheckBox envEnabled;
    private JCheckBox validationEnabled;
    private JCheckBox menuEnabled;
    private JTextField textTranslationLang;
    private JTextField textTranslationPath;
    private JTextField textPhpBinaryPath;
    private JTextField textHttpJsonPath;

    public HyperfProjectSettingsForm(@NotNull final Project project) {
        this.project = project;
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "Hyperf Base";
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
        envEnabled = new JCheckBox("Enable env() completion & goto (.env keys)");
        validationEnabled = new JCheckBox("Enable validation rule name completion");
        menuEnabled = new JCheckBox("Enable Hyperf top menu (code generation & commands)");
        textTranslationPath = new JTextField();
        textTranslationLang = new JTextField();
        textPhpBinaryPath = new JTextField();
        textHttpJsonPath = new JTextField();
        textTranslationLang.setToolTipText("If you have more than one language in project, this one will be first in \"Go to\" translation options");
        textPhpBinaryPath.setToolTipText("PHP executable for Hyperf menu commands. Empty = project CLI interpreter, then php from PATH");
        textHttpJsonPath.setToolTipText("apidocs http.json path. Empty = <app root>/runtime/container/http.json; absolute or relative to app root");

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

        label.gridy = 2;
        field.gridy = 2;
        fields.add(new JLabel("PHP Binary Path"), label);
        fields.add(textPhpBinaryPath, field);

        label.gridy = 3;
        field.gridy = 3;
        fields.add(new JLabel("Http Json Path"), label);
        fields.add(textHttpJsonPath, field);

        // 垂直弹簧，把内容顶到面板上方
        GridBagConstraints glue = new GridBagConstraints();
        glue.gridx = 0;
        glue.gridy = 4;
        glue.gridwidth = 2;
        glue.weighty = 1.0;
        glue.fill = GridBagConstraints.VERTICAL;
        fields.add(Box.createVerticalGlue(), glue);

        JPanel panel = new JPanel(new BorderLayout());
        JPanel checks = new JPanel(new GridLayout(4, 1));
        checks.add(enabled);
        checks.add(envEnabled);
        checks.add(validationEnabled);
        checks.add(menuEnabled);
        panel.add(checks, BorderLayout.NORTH);
        panel.add(fields, BorderLayout.CENTER);

        updateUIFromSettings();
        return panel;
    }

    /** 任一字段与持久化值不同即视为已修改 */
    @Override
    public boolean isModified() {
        return enabled.isSelected() != getSettings().pluginEnabled
                || envEnabled.isSelected() != getSettings().envEnabled
                || validationEnabled.isSelected() != getSettings().validationEnabled
                || menuEnabled.isSelected() != getSettings().menuEnabled
                || !textTranslationLang.getText().equals(getSettings().translationLang)
                || !textTranslationPath.getText().equals(getSettings().translationPath)
                || !textPhpBinaryPath.getText().equals(getSettings().phpBinaryPath)
                || !textHttpJsonPath.getText().equals(getSettings().httpJsonPath);
    }

    /** Apply 按钮：把界面值写回持久化设置 */
    @Override
    public void apply() throws ConfigurationException {
        getSettings().pluginEnabled = enabled.isSelected();
        getSettings().envEnabled = envEnabled.isSelected();
        getSettings().validationEnabled = validationEnabled.isSelected();
        getSettings().menuEnabled = menuEnabled.isSelected();
        getSettings().translationLang = textTranslationLang.getText();
        getSettings().translationPath = textTranslationPath.getText();
        getSettings().phpBinaryPath = textPhpBinaryPath.getText().trim();
        getSettings().httpJsonPath = textHttpJsonPath.getText().trim();
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
        envEnabled.setSelected(getSettings().envEnabled);
        validationEnabled.setSelected(getSettings().validationEnabled);
        menuEnabled.setSelected(getSettings().menuEnabled);
        textTranslationLang.setText(getSettings().translationLang);
        textTranslationPath.setText(getSettings().translationPath);
        textPhpBinaryPath.setText(getSettings().phpBinaryPath);
        textHttpJsonPath.setText(getSettings().httpJsonPath);
    }

    @Override
    public void disposeUIResources() {
    }

    private HyperfSettings getSettings() {
        return HyperfSettings.getInstance(this.project);
    }

    /** 打开 Settings 并定位到本设置页（用于通知气泡里的 "打开设置" 链接） */
    public static void show(@NotNull Project project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "hyperf.base.SettingsForm");
    }
}
