package org.broad.igv.ui.action;

import org.junit.Test;

import javax.swing.*;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;

import static org.junit.Assert.*;

public class MenuActionTest {

    @Test
    public void testAcceleratorConstructor() {
        MenuAction action = new MenuAction("Open", null, KeyEvent.VK_O, KeyEvent.VK_O);
        KeyStroke accelerator = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
        assertNotNull("accelerator must be set", accelerator);
        assertEquals(KeyEvent.VK_O, accelerator.getKeyCode());
        int expectedMask;
        try {
            expectedMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        } catch (Exception e) {
            expectedMask = java.awt.event.InputEvent.CTRL_MASK;
        }
        assertTrue("accelerator must include the platform menu shortcut mask",
                (accelerator.getModifiers() & expectedMask) != 0);
        // mnemonic still honored
        assertEquals((Integer) KeyEvent.VK_O, (Integer) action.getValue(Action.MNEMONIC_KEY));
    }

    @Test
    public void testNoAcceleratorWhenDisabled() {
        MenuAction action = new MenuAction("Plain", null, KeyEvent.VK_P, -1);
        assertNull("no accelerator when -1", action.getValue(Action.ACCELERATOR_KEY));
        assertEquals((Integer) KeyEvent.VK_P, (Integer) action.getValue(Action.MNEMONIC_KEY));
    }
}
