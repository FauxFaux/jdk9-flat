/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
  @test
  @bug 6480024
  @library ../../../regtesthelpers
  @build Util Sysout AbstractTest
  @summary stack overflow on mouse wheel rotation
  @author Andrei Dmitriev: area=awt.event
  @run main InfiniteRecursion
*/

/**
 * InfiniteRecursion.java
 *
 * summary: put a JButton into JFrame.
 * Add MouseWheelListener to JFrame.
 * Add MouseListener to JButton.
 * Rotating a wheel over the JButton would result in stack overflow.
 */

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import test.java.awt.regtesthelpers.Util;
import test.java.awt.regtesthelpers.AbstractTest;
import test.java.awt.regtesthelpers.Sysout;

public class InfiniteRecursion {
    final static Robot robot = Util.createRobot();
    final static int MOVE_COUNT = 5;
    static int actualEvents = 0;

    public static void main(String []s)
    {
        JFrame frame = new JFrame("A test frame");
        frame.setSize(200, 200);
        frame.addMouseWheelListener(new MouseWheelListener() {
                public void mouseWheelMoved(MouseWheelEvent e) {
                    System.out.println("Wheel moved on FRAME : "+e);
                    actualEvents++;
                }
            });

        JButton jButton = new JButton();
        /*
          outputBox.addMouseWheelListener(new MouseWheelListener() {
          public void mouseWheelMoved(MouseWheelEvent e){}
          });
        */
        jButton.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    System.out.println("MousePressed on jButton : "+e);
                }

            });
        frame.add(jButton);

        frame.setVisible(true);

        Util.waitForIdle(robot);

        Util.pointOnComp(jButton, robot);
        Util.waitForIdle(robot);

        for (int i = 0; i < MOVE_COUNT; i++){
            robot.mouseWheel(1);
            robot.delay(10);
        }

        for (int i = 0; i < MOVE_COUNT; i++){
            robot.mouseWheel(-1);
            robot.delay(10);
        }


        Util.waitForIdle(robot);
        if (actualEvents != MOVE_COUNT * 2) {
            AbstractTest.fail("Expected events count: "+ MOVE_COUNT+" Actual events count: "+ actualEvents);
        }
    }
}
