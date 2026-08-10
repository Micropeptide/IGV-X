/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
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

/*
 * MenuAction.java
 *
 * Created on November 7, 2007, 2:07 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.broad.igv.ui.action;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * @author eflakes
 */
public class MenuAction extends AbstractAction {

    private String toolTipText;

    /**
     * Creates a new instance of MenuAction
     */
    public MenuAction(String name, Icon icon, int mnemonic) {
        super(name, icon);
        if(mnemonic >= 0) {
            putValue(MNEMONIC_KEY, mnemonic);
        }
    }

    /**
     * Creates a new instance of MenuAction with a keyboard accelerator.
     * The accelerator uses the platform menu shortcut key (Cmd on macOS,
     * Ctrl on Windows/Linux), so the same key code works across platforms.
     *
     * @param acceleratorKey KeyEvent.VK_* code, or -1 for none
     */
    public MenuAction(String name, Icon icon, int mnemonic, int acceleratorKey) {
        super(name, icon);
        if (mnemonic >= 0) {
            putValue(MNEMONIC_KEY, mnemonic);
        }
        if (acceleratorKey >= 0) {
            int shortcutMask = getMenuShortcutMask();
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(acceleratorKey, shortcutMask));
        }
    }

    /**
     * Platform menu shortcut mask (Cmd on macOS, Ctrl elsewhere).  Falls back
     * to Ctrl in headless environments (e.g. unit tests) where Toolkit is
     * not available.
     */
    private static int getMenuShortcutMask() {
        try {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (Exception e) {
            return InputEvent.CTRL_DOWN_MASK;
        }
    }

    public MenuAction(String name, Icon icon) {
        super(name, icon);
    }

    public MenuAction(String name) {
        super(name, null);
    }

    public void actionPerformed(ActionEvent event) {
        JOptionPane.showMessageDialog(null, "Functionality not implemented!");
    }

    public String getToolTipText() {
        return toolTipText;
    }

    public void setToolTipText(String toolTipText) {
        this.toolTipText = toolTipText;
    }

}
