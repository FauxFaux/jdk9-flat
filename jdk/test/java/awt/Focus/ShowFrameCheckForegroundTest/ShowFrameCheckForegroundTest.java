/*
 * Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
  @test
  @key headful
  @bug       6492970
  @summary   Tests that showing a toplvel in a not foreground Java process activates it.
  @library   ../../regtesthelpers
  @build     Util
  @author    Anton Tarasov: area=awt-focus
  @run       main ShowFrameCheckForegroundTest
 */

import java.awt.*;
import java.awt.event.*;
import java.applet.Applet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.InvocationTargetException;
import test.java.awt.regtesthelpers.Util;

public class ShowFrameCheckForegroundTest extends Applet {
    Robot robot;
    Frame nofocusFrame = new Frame("Non-focusable");
    Frame frame = new Frame("Frame");
    Dialog dialog1 = new Dialog(nofocusFrame, "Owned Dialog", false);
    Dialog dialog2 = new Dialog((Frame)null, "Owned Dialog", false);
    Window testToplevel = null;
    Button testButton = new Button("button");
    Button showButton = new Button("show");
    Runnable action = new Runnable() {
        public void run() {
            robot.keyPress(KeyEvent.VK_SPACE);
            robot.delay(50);
            robot.keyRelease(KeyEvent.VK_SPACE);
        }
    };


    public static void main(String[] args) {
        ShowFrameCheckForegroundTest app = new ShowFrameCheckForegroundTest();
        app.init();
        app.start();
    }

    public void init() {
        robot = Util.createRobot();
    }

    public void start() {
        showButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                testToplevel.setVisible(true);
            }
        });
        nofocusFrame.add(showButton);
        nofocusFrame.pack();
        nofocusFrame.setFocusableWindowState(false);
        nofocusFrame.setVisible(true);
        Util.waitForIdle(robot);

        robot.delay(3000);

        // 1. Show the toplvel without clicking into the non-focusable frame.
        test(frame, 1);
        test(dialog1, 1);
        test(dialog2, 1);

        // 2. Showing the toplvel via clicking into the non-focusable frame.
        test(frame, 2);
        test(dialog1, 2);
        test(dialog2, 2);

        System.out.println("Test passed.");
    }

    private void test(Window toplevel, int stage) {
        toplevel.add(testButton);
        toplevel.pack();
        toplevel.setLocation(200, 0);

        switch (stage) {
            case 1:
                toplevel.setVisible(true);
                break;
            case 2:
                testToplevel = toplevel;
                Util.clickOnComp(showButton, robot);
                break;
        }
        Util.waitForIdle(robot);

        if (!Util.trackActionPerformed(testButton, action, 2000, false)) {
            throw new TestFailedException("Stage " + stage + ". The toplevel " + toplevel + " wasn't made foreground on showing");
        }
        System.out.println("Stage " + stage + ". Toplevel " + toplevel + " - passed");
        toplevel.dispose();
    }
}

class TestFailedException extends RuntimeException {
    TestFailedException(String msg) {
        super("Test failed: " + msg);
    }
}

/****************************************************
 * Standard Test Machinery
 * DO NOT modify anything below -- it's a standard
 * chunk of code whose purpose is to make user
 * interaction uniform, and thereby make it simpler
 * to read and understand someone else's test.
 ****************************************************/

/**
 * This is part of the standard test machinery.
 * It creates a dialog (with the instructions), and is the interface
 * for sending text messages to the user.
 * To print the instructions, send an array of strings to Sysout.createDialog
 * WithInstructions method.  Put one line of instructions per array entry.
 * To display a message for the tester to see, simply call Sysout.println
 * with the string to be displayed.
 * This mimics System.out.println but works within the test harness as well
 * as standalone.
 */

class Sysout {
    static TestDialog dialog;

    public static void createDialogWithInstructions( String[] instructions ) {
        dialog = new TestDialog( new Frame(), "Instructions" );
        dialog.printInstructions( instructions );
        dialog.setVisible(true);
        println( "Any messages for the tester will display here." );
    }

    public static void createDialog( ) {
        dialog = new TestDialog( new Frame(), "Instructions" );
        String[] defInstr = { "Instructions will appear here. ", "" } ;
        dialog.printInstructions( defInstr );
        dialog.setVisible(true);
        println( "Any messages for the tester will display here." );
    }


    public static void printInstructions( String[] instructions ) {
        dialog.printInstructions( instructions );
    }


    public static void println( String messageIn ) {
        dialog.displayMessage( messageIn );
    }

}// Sysout  class

/**
 * This is part of the standard test machinery.  It provides a place for the
 * test instructions to be displayed, and a place for interactive messages
 * to the user to be displayed.
 * To have the test instructions displayed, see Sysout.
 * To have a message to the user be displayed, see Sysout.
 * Do not call anything in this dialog directly.
 */
class TestDialog extends Dialog {

    TextArea instructionsText;
    TextArea messageText;
    int maxStringLength = 80;

    //DO NOT call this directly, go through Sysout
    public TestDialog( Frame frame, String name ) {
        super( frame, name );
        int scrollBoth = TextArea.SCROLLBARS_BOTH;
        instructionsText = new TextArea( "", 15, maxStringLength, scrollBoth );
        add( "North", instructionsText );

        messageText = new TextArea( "", 5, maxStringLength, scrollBoth );
        add("Center", messageText);

        pack();

        setVisible(true);
    }// TestDialog()

    //DO NOT call this directly, go through Sysout
    public void printInstructions( String[] instructions ) {
        //Clear out any current instructions
        instructionsText.setText( "" );

        //Go down array of instruction strings

        String printStr, remainingStr;
        for( int i=0; i < instructions.length; i++ ) {
            //chop up each into pieces maxSringLength long
            remainingStr = instructions[ i ];
            while( remainingStr.length() > 0 ) {
                //if longer than max then chop off first max chars to print
                if( remainingStr.length() >= maxStringLength ) {
                    //Try to chop on a word boundary
                    int posOfSpace = remainingStr.
                            lastIndexOf( ' ', maxStringLength - 1 );

                    if( posOfSpace <= 0 ) posOfSpace = maxStringLength - 1;

                    printStr = remainingStr.substring( 0, posOfSpace + 1 );
                    remainingStr = remainingStr.substring( posOfSpace + 1 );
                }
                //else just print
                else {
                    printStr = remainingStr;
                    remainingStr = "";
                }

                instructionsText.append( printStr + "\n" );

            }// while

        }// for

    }//printInstructions()

    //DO NOT call this directly, go through Sysout
    public void displayMessage( String messageIn ) {
        messageText.append( messageIn + "\n" );
        System.out.println(messageIn);
    }

}// TestDialog  class
