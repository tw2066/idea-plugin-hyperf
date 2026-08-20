package com.base.idea.hyperf.console;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link HyperfConsoleRunner} 路径处理的单测。
 * 重点覆盖 WSL 两类项目布局（Windows 盘 / WSL 文件系统 UNC）的路径转换。
 */
public class HyperfConsoleRunnerTest {

    @Test
    public void testToUnixPathDriveLetter() {
        assertEquals("/mnt/d/java/hyperf-skeleton-3.2/bin/hyperf.php",
                HyperfConsoleRunner.toUnixPath("D:/java/hyperf-skeleton-3.2/bin/hyperf.php"));
        // 反斜杠与大写盘符
        assertEquals("/mnt/d/java/a", HyperfConsoleRunner.toUnixPath("D:\\java\\a"));
        assertEquals("/mnt/c/x", HyperfConsoleRunner.toUnixPath("C:/x"));
    }

    @Test
    public void testToUnixPathWslUnc() {
        // 项目在 WSL 文件系统：UNC 前缀整体剥掉
        assertEquals("/home/tw/hyperf/bin/hyperf.php",
                HyperfConsoleRunner.toUnixPath("//wsl.localhost/Ubuntu-24.04/home/tw/hyperf/bin/hyperf.php"));
        assertEquals("/home/tw/x",
                HyperfConsoleRunner.toUnixPath("//wsl$/Ubuntu/home/tw/x"));
        // 发行版名后无更多路径 → 根
        assertEquals("/", HyperfConsoleRunner.toUnixPath("//wsl.localhost/Ubuntu-24.04"));
    }

    @Test
    public void testToUnixPathAlreadyUnix() {
        assertEquals("/usr/bin/php", HyperfConsoleRunner.toUnixPath("/usr/bin/php"));
        assertEquals("/home/tw/hyperf", HyperfConsoleRunner.toUnixPath("/home/tw/hyperf"));
    }

    @Test
    public void testIsUnixLike() {
        assertTrue(HyperfConsoleRunner.isUnixLike("/usr/bin/php8.4"));
        assertFalse(HyperfConsoleRunner.isUnixLike("D:\\php\\php.exe"));
        assertFalse(HyperfConsoleRunner.isUnixLike("php"));
    }

    @Test
    public void testQuote() {
        assertEquals("php", HyperfConsoleRunner.quote("php"));
        assertEquals("D:\\php\\php.exe", HyperfConsoleRunner.quote("D:\\php\\php.exe"));
        assertEquals("\"D:\\Program Files\\php\\php.exe\"",
                HyperfConsoleRunner.quote("D:\\Program Files\\php\\php.exe"));
        assertEquals("\"/mnt/d/a b/bin/hyperf.php\"",
                HyperfConsoleRunner.quote("/mnt/d/a b/bin/hyperf.php"));
    }
}
