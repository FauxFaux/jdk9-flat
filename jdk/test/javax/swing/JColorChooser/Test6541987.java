/*
 * Copyright 2005-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6541987
 * @summary Tests closing by ESC
 * @author Sergey Malenkov
 */

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;

import javax.swing.JColorChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import sun.awt.SunToolkit;

public class Test6541987 implements Runnable {
    private static final SunToolkit toolkit = (SunToolkit) Toolkit.getDefaultToolkit();
    private static Robot robot;

    public static void main(String[] args) throws AWTException {
        robot = new Robot();
        // test escape after selection
        start();
        click(KeyEvent.VK_ESCAPE);
        toolkit.realSync();
        // test double escape after editing
        start();
        click(KeyEvent.VK_1);
        click(KeyEvent.VK_0);
        click(KeyEvent.VK_ESCAPE);
        click(KeyEvent.VK_ESCAPE);
        toolkit.realSync();
        // all windows should be closed
        for (Window window : Window.getWindows()) {
            if (window.isVisible()) {
                throw new Error("found visible window: " + window.getName());
            }
        }
    }

    private static void start() {
        SwingUtilities.invokeLater(new Test6541987());
        click(KeyEvent.VK_ALT, KeyEvent.VK_H);
        click(KeyEvent.VK_TAB);
        click(KeyEvent.VK_TAB);
        click(KeyEvent.VK_TAB);
        click(KeyEvent.VK_TAB);
    }

    private static void click(int...keys) {
        toolkit.realSync();
        for (int key : keys) {
            robot.keyPress(key);
        }
        for (int key : keys) {
            robot.keyRelease(key);
        }
    }

    public void run() {
        String title = getClass().getName();
        JFrame frame = new JFrame(title);
        frame.setVisible(true);

        Color color = JColorChooser.showDialog(frame, title, Color.BLACK);
        if (color != null) {
            throw new Error("unexpected color: " + color);
        }
        frame.setVisible(false);
        frame.dispose();
    }
}
