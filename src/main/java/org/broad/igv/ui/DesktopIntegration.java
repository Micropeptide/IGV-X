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
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.ui.action.SmartOpenMenuAction;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
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

    private static final Logger log = LogManager.getLogger(DesktopIntegration.class);

    /**
     * Debug-log helper that writes to a dedicated file (independent of the main
     * log, which may not be initialized during the earliest startup phase) plus
     * the normal logger.  Used to diagnose macOS open-file event delivery.
     */
    private static void debugLog(String msg) {
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("user.home"), ".igvx", "open-file-debug.log"),
                    (java.time.Instant.now() + " " + msg + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignore) {
            // debug logging must never break startup
        }
        log.info(msg);
    }

    /**
     * Files delivered by macOS open-file events that arrived before IGV was
     * fully initialized (cold launch: Finder double-click / Open With).  The
     * handler is installed very early in {@code Main.main}; any files received
     * before {@link #setIgvReady(org.broad.igv.ui.IGV)} are buffered here and
     * drained immediately once the IGV instance exists.  Without this buffer,
     * macOS Apple Events that arrive during the (long) IGV startup are silently
     * dropped because {@code java.awt.Desktop} only delivers events to the
     * currently-registered handler.
     */
    private static final List<File> pendingOpenFiles = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean openFileHandlerInstalled = false;
    private static volatile IGV igvReady = null;

    /**
     * IGV-X: set the instant a Finder/AppleEvents open-file request is received
     * (cold-launch buffered or live), and never cleared. {@code StartupRunnable}
     * checks this before deciding to show the Welcome panel: the open-file
     * request and normal startup both run as unordered async tasks on the same
     * thread pool, and without this check {@code StartupRunnable} could show the
     * Welcome panel *after* the Finder-requested session/tracks already loaded
     * and hid it, leaving it stuck on top of the loaded data (Runtian bug
     * report, 2026-09-07: "Open With" loads the session but shows the welcome
     * page requiring a manual Dismiss).
     */
    private static volatile boolean sawOpenFileEvent = false;

    /**
     * Whether a Finder/AppleEvents open-file request has been received (or is
     * still buffered) at any point in this process's lifetime.
     */
    public static boolean hasSeenOpenFileEvent() {
        return sawOpenFileEvent;
    }

    /**
     * Install the macOS open-file handler as early as possible.  Safe to call
     * on any platform and multiple times (idempotent).  Call from Main.main
     * before heavy initialization so a cold-launch Finder event is not lost.
     */
    public static void installEarlyOpenFileHandler() {
        if (openFileHandlerInstalled) {
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                debugLog("installEarlyOpenFileHandler: Desktop not supported");
                return;
            }
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
                desktop.setOpenFileHandler(e -> {
                    List<File> files = e.getFiles();
                    debugLog("macOS open-file event received: " + (files == null ? "null" : files.toString()));
                    if (files == null || files.isEmpty()) {
                        return;
                    }
                    sawOpenFileEvent = true;
                    IGV igv = igvReady;
                    if (igv != null) {
                        SmartOpenMenuAction.openFiles(igv, files.toArray(new File[0]));
                    } else {
                        // IGV not ready yet (cold launch) -- buffer and drain later
                        pendingOpenFiles.addAll(files);
                        debugLog("IGV not ready, buffering " + files.size() + " open-file event(s)");
                    }
                });
                openFileHandlerInstalled = true;
                debugLog("Installed macOS open-file handler (APP_OPEN_FILE supported)");
            } else {
                debugLog("APP_OPEN_FILE not supported on this platform");
            }
        } catch (Exception ex) {
            debugLog("Error installing macOS open-file handler: " + ex);
        }
    }

    /**
     * Mark the IGV instance as ready and drain any buffered open-file events.
     * Called once after IGV.createInstance completes.
     */
    public static void setIgvReady(IGV igv) {
        igvReady = igv;
        if (!pendingOpenFiles.isEmpty()) {
            List<File> files;
            synchronized (pendingOpenFiles) {
                files = new ArrayList<>(pendingOpenFiles);
                pendingOpenFiles.clear();
            }
            log.info("Draining " + files.size() + " buffered open-file event(s) after IGV ready");
            SmartOpenMenuAction.openFiles(igv, files.toArray(new File[0]));
        }
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

        // NOTE: the open-file handler (Finder double-click / Open With / drag
        // & drop) is installed early in Main.main via installEarlyOpenFileHandler()
        // so cold-launch Apple Events are not lost during IGV startup.  It is
        // intentionally NOT re-registered here.
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
