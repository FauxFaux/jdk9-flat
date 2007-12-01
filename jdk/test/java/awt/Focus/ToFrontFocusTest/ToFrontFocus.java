/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
  test
  @bug 4092033 4529626
  @summary Tests that toFront makes window focused unless it is non-focusable
  @author  area=awt.focus
  @run applet ToFrontFocus.html
*/

/**
 * ToFrontFocus.java
 *
 * summary:
 */

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import test.java.awt.regtesthelpers.Util;

public class ToFrontFocus extends Applet
 {
   //Declare things used in the test, like buttons and labels here

     Frame cover, focus_frame, nonfocus_frame;
     Button focus_button, nonfocus_button;
     volatile boolean focus_gained = false, nonfocus_gained = false;
   public void init()
    {
      //Create instructions for the user here, as well as set up
      // the environment -- set the layout manager, add buttons,
      // etc.

      this.setLayout (new BorderLayout ());

      String[] instructions =
       {
           "This is an AUTOMATIC test",
           "simply wait until it is done"
       };
      Sysout.createDialog( );
      Sysout.printInstructions( instructions );
      cover = new Frame("Cover frame");
      cover.setBounds(100, 100, 200, 200);
      focus_frame = new Frame("Focusable frame");
      focus_frame.setBounds(150, 100, 250, 150);
      nonfocus_frame = new Frame("Non-focusable frame");
      nonfocus_frame.setFocusableWindowState(false);
      nonfocus_frame.setBounds(150, 150, 250, 200);
      focus_button = new Button("Button in focusable frame");
      focus_button.addFocusListener(new FocusAdapter() {
              public void focusGained(FocusEvent e) {
                  focus_gained = true;
              }
          });
      nonfocus_button = new Button("Button in non-focusable frame");
      nonfocus_button.addFocusListener(new FocusAdapter() {
              public void focusGained(FocusEvent e) {
                  nonfocus_gained = true;
              }
          });
    }//End  init()

   public void start ()
    {
      //Get things going.  Request focus, set size, et cetera
      setSize (200,200);
      show();
      Util.waitForIdle(null);

      focus_frame.setFocusTraversalPolicy(new DefaultFocusTraversalPolicy() {
              public Component getInitialComponent(Window w) {
                  return null;
              }
          });
      focus_frame.setVisible(true);
      nonfocus_frame.setFocusTraversalPolicy(new DefaultFocusTraversalPolicy() {
              public Component getInitialComponent(Window w) {
                  return null;
              }
          });
      nonfocus_frame.setVisible(true);
      cover.setVisible(true);

      Util.waitForIdle(null);

      // So events are no generated at the creation add buttons here.
      focus_frame.add(focus_button);
      focus_frame.pack();
      focus_frame.setFocusTraversalPolicy(new DefaultFocusTraversalPolicy() {
              public Component getInitialComponent(Window w) {
                  return focus_button;
              }
          });
      nonfocus_frame.add(nonfocus_button);
      nonfocus_frame.pack();
      nonfocus_frame.setFocusTraversalPolicy(new DefaultFocusTraversalPolicy() {
              public Component getInitialComponent(Window w) {
                  return nonfocus_button;
              }
          });

      System.err.println("------------ Starting test ------------");
      // Make frame focused - focus_gained will be genereated for button.
      focus_frame.toFront();
      // focus_gained should not be generated
      nonfocus_frame.toFront();

      // Wait for events.
      Util.waitForIdle(null);

      if (!focus_gained || nonfocus_gained) {
          throw new RuntimeException("ToFront doesn't work as expected");
      }
    }// start()

 }// class ToFrontFocus


/****************************************************
 Standard Test Machinery
 DO NOT modify anything below -- it's a standard
  chunk of code whose purpose is to make user
  interaction uniform, and thereby make it simpler
  to read and understand someone else's test.
 ****************************************************/

/**
 This is part of the standard test machinery.
 It creates a dialog (with the instructions), and is the interface
  for sending text messages to the user.
 To print the instructions, send an array of strings to Sysout.createDialog
  WithInstructions method.  Put one line of instructions per array entry.
 To display a message for the tester to see, simply call Sysout.println
  with the string to be displayed.
 This mimics System.out.println but works within the test harness as well
  as standalone.
 */

class Sysout
 {
   private static TestDialog dialog;

   public static void createDialogWithInstructions( String[] instructions )
    {
      dialog = new TestDialog( new Frame(), "Instructions" );
      dialog.printInstructions( instructions );
      dialog.show();
      println( "Any messages for the tester will display here." );
    }

   public static void createDialog( )
    {
      dialog = new TestDialog( new Frame(), "Instructions" );
      String[] defInstr = { "Instructions will appear here. ", "" } ;
      dialog.printInstructions( defInstr );
      dialog.show();
      println( "Any messages for the tester will display here." );
    }


   public static void printInstructions( String[] instructions )
    {
      dialog.printInstructions( instructions );
    }


   public static void println( String messageIn )
    {
      dialog.displayMessage( messageIn );
    }

 }// Sysout  class

/**
  This is part of the standard test machinery.  It provides a place for the
   test instructions to be displayed, and a place for interactive messages
   to the user to be displayed.
  To have the test instructions displayed, see Sysout.
  To have a message to the user be displayed, see Sysout.
  Do not call anything in this dialog directly.
  */
class TestDialog extends Dialog
 {

   TextArea instructionsText;
   TextArea messageText;
   int maxStringLength = 80;

   //DO NOT call this directly, go through Sysout
   public TestDialog( Frame frame, String name )
    {
      super( frame, name );
      int scrollBoth = TextArea.SCROLLBARS_BOTH;
      instructionsText = new TextArea( "", 15, maxStringLength, scrollBoth );
      add( "North", instructionsText );

      messageText = new TextArea( "", 5, maxStringLength, scrollBoth );
      add("South", messageText);

      pack();

      show();
    }// TestDialog()

   //DO NOT call this directly, go through Sysout
   public void printInstructions( String[] instructions )
    {
      //Clear out any current instructions
      instructionsText.setText( "" );

      //Go down array of instruction strings

      String printStr, remainingStr;
      for( int i=0; i < instructions.length; i++ )
       {
         //chop up each into pieces maxSringLength long
         remainingStr = instructions[ i ];
         while( remainingStr.length() > 0 )
          {
            //if longer than max then chop off first max chars to print
            if( remainingStr.length() >= maxStringLength )
             {
               //Try to chop on a word boundary
               int posOfSpace = remainingStr.
                  lastIndexOf( ' ', maxStringLength - 1 );

               if( posOfSpace <= 0 ) posOfSpace = maxStringLength - 1;

               printStr = remainingStr.substring( 0, posOfSpace + 1 );
               remainingStr = remainingStr.substring( posOfSpace + 1 );
             }
            //else just print
            else
             {
               printStr = remainingStr;
               remainingStr = "";
             }

            instructionsText.append( printStr + "\n" );

          }// while

       }// for

    }//printInstructions()

   //DO NOT call this directly, go through Sysout
   public void displayMessage( String messageIn )
    {
      messageText.append( messageIn + "\n" );
    }

 }// TestDialog  class
