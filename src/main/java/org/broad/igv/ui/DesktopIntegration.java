/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2018 Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.broad.igv.ui;

import java.awt.Desktop;
import org.broad.igv.Globals;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

import javax.swing.JOptionPane;

/**
  * Java version-specific integration with the platform Desktop and particularly 
  * for OS X (macOS) specific items. * @author eby
 */
public class DesktopIntegration {
    public static final void verifyJavaPlatform() {
        String javaVersion = System.getProperty("java.version");
        if (javaVersion == null || javaVersion.startsWith("1.8")) {
            try {
                System.out.println("Detected an unsupported Java version.  Java 8 is not supported by this release.");

                if (!GraphicsEnvironment.isHeadless()) {
                    JOptionPane.showMessageDialog(null, "Detected an unsupported Java version.  Java 8 is not supported by this release.");
                }
            } finally {
                System.exit(1);
            }
        }
    }
    
    public static void setDockIcon(Image image) {
        Taskbar.getTaskbar().setIconImage(image);
    }

    public static void setAboutHandler(IGVMenuBar igvMenuBar) {
        Desktop.getDesktop().setAboutHandler(e -> igvMenuBar.showAboutDialog());
    }

    /**
     * Install the standard macOS application menu handlers: Preferences
     * (Cmd+,), Quit (Cmd+Q), and Open File (Finder drag-and-drop / Dock).
     * Safe to call on any platform; each handler is installed only if the
     * Desktop supports it.
     */
    public static void installMacAppHandlers(IGVMenuBar igvMenuBar) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        Desktop desktop = Desktop.getDesktop();

        try {
            if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
                desktop.setPreferencesHandler(e -> igvMenuBar.getIgv().doViewPreferences());
            }
        } catch (Exception e) {
            // ignore - handler optional
        }

        try {
            if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                desktop.setQuitHandler((e, response) -> igvMenuBar.doExitApplication());
            }
        } catch (Exception e) {
            // ignore - handler optional
        }

        try {
            if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
                desktop.setOpenFileHandler(e -> {
                    List<File> files = e.getFiles();
                    if (files != null && !files.isEmpty()) {
                        org.broad.igv.ui.action.SmartOpenMenuAction.openFiles(
                                igvMenuBar.getIgv(), files.toArray(new File[0]));
                    }
                });
            }
        } catch (Exception e) {
            // ignore - handler optional
        }
    }

    /**
     * Enable the macOS green-button fullscreen (Zoom -> Full Screen) for the
     * main frame. Uses reflection so the build stays portable; on non-macOS
     * platforms this is a no-op.
     */
    public static void enableFullscreen(Window window) {
        if (window == null || !Globals.IS_MAC) {
            return;
        }
        try {
            Class<?> fsUtils = Class.forName("com.apple.eawt.FullScreenUtilities");
            Method setCanFullScreen = fsUtils.getMethod("setCanFullScreen", Window.class, boolean.class);
            setCanFullScreen.invoke(null, window, true);
        } catch (Exception e) {
            // Not fatal - fullscreen button simply stays unavailable
        }
    }
}
