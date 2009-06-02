// Cheloniidae Turtle Graphics
// Created by Spencer Tipping and licensed under the terms of the MIT source code license

package cheloniidae;

import java.awt.image.BufferedImage;
import java.awt.event.*;

import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;

import java.util.List;
import java.util.ArrayList;

public class TurtleDrawingWindow extends Frame {
  protected class GraphicsThreadRunner implements Runnable {
    boolean refreshAllLines = false;
    boolean antialiased     = true;

    GraphicsThreadRunner (boolean _refreshAllLines, boolean _antialiased) {
      refreshAllLines = _refreshAllLines;
      antialiased = _antialiased;
    }

    public void run () {
      drawLines (refreshAllLines, antialiased);
      drawTurtles (antialiased);
      repaint ();
    }
  }

  public static final double DEFAULT_Z_BASE              = 500.0;
  public static final double TURTLE_RADIUS               = 4.0;
  public static final double TURTLE_HEADING_LENGTH       = 16.0;
  public static final int    TURTLE_ALPHA                = 128;
  public static final int    DRAWING_REFRESH_PER_LINES   = 10000;
  public static final int    INTERMEDIATE_RENDER_CUTOFF  = 10000;
  public static final int    LINE_POINT_MAXIMUM_DISTANCE = 16;

  protected BufferedImage      turtleOutput        = null;
  protected BufferedImage      turtleLayer        = null;
  protected List<LineProvider> providers                = new ArrayList<LineProvider> ();

  protected Vector virtualTranslation  = new Vector ();
  protected Vector viewportTranslation = new Vector ();
  protected Vector viewportPOV         = new Vector (0, 0, -1);

  protected Vector minimumExtent = new Vector ();
  protected Vector maximumExtent = new Vector ();

  protected int          mouseDownX          = 0;
  protected int          mouseDownY          = 0;
  protected boolean        mouseDown          = false;

  protected Thread        graphicsRequestRunner  = null;

  protected boolean        graphicsRequestCancelFlag  = false;

  protected boolean        fisheye3D          = false;
  
  public TurtleDrawingWindow () {}

  protected void regenerateImages () {                // {{{
    //
    // Reallocate both the turtle output layer and the turtle layer.
    //

    imgTurtleOutput = new BufferedImage (super.getWidth (), super.getHeight (), BufferedImage.TYPE_3BYTE_BGR);
    imgTurtleLayer  = new BufferedImage (super.getWidth (), super.getHeight (), BufferedImage.TYPE_3BYTE_BGR);
  }                                  // }}}

  protected void handleResize () {                  // {{{
    //
    // Set up the viewport dimensions. I know that we snap back to
    // center on a resize, and while this may not be desirable, I
    // would consider it to be so because if the user for some reason
    // pans too far off center and loses the image, this is a convenient
    // way to restore it.
    // 
    viewportX = super.getWidth () / 2;
    viewportY = super.getHeight () / 2;

    //
    // Regenerate the images and repaint the screen.
    //
    regenerateImages ();
    enqueueGraphicsRefreshRequest (true, true);
  }                                  // }}}

  protected void initialize () {                    // {{{
    //
    // Step 1. Add listeners to the frame.
    //

    //
    // Provide a backreference for all of the anonymous classes.
    // Java's rules say that it must be final.
    //
    final TurtleDrawingWindow t = this;

    super.addWindowListener (new WindowListener () {        // {{{
        //
        // Methods
        //

        public void windowClosing    (WindowEvent e) {
        t.dispose ();
        }

        //
        // Empty methods
        //

        public void windowActivated    (WindowEvent e) {}
        public void windowClosed    (WindowEvent e) {}
        public void windowDeactivated  (WindowEvent e) {}
        public void windowDeiconified  (WindowEvent e) {}
        public void windowIconified    (WindowEvent e) {}
        public void windowOpened    (WindowEvent e) {}

        });                                // }}}

    super.addComponentListener (new ComponentListener () {      // {{{
        //
        // Methods
        //

        public void componentResized  (ComponentEvent e) {
        t.handleResize ();
        }

        //
        // Empty methods
        // 

        public void componentMoved    (ComponentEvent e) {}
        public void componentHidden    (ComponentEvent e) {}
        public void componentShown    (ComponentEvent e) {}

        });                                // }}}

    super.addMouseListener (new MouseListener () {          // {{{
        //
        // Methods
        //

        public void mouseReleased (MouseEvent e) {
        mouseDown = false;
        t.enqueueGraphicsRefreshRequest (true, true);
        }

        public void mousePressed (MouseEvent e) {
        mouseDown = true;
        mouseDownX = e.getX ();
        mouseDownY = e.getY ();
        }

        //
        // Empty methods
        //

        public void mouseClicked (MouseEvent e) {}
        public void mouseEntered (MouseEvent e) {}
        public void mouseExited (MouseEvent e) {}

    });                                // }}}

    super.addMouseMotionListener (new MouseMotionListener () {    // {{{
        //
        // Methods
        //

        public void mouseDragged (MouseEvent e) {
        if (mouseDown) {
        if (e.isShiftDown () && e.isControlDown ()) {
        //
        // Zoom in and out and rotate the view.
        //
        viewportZoom += e.getY () - mouseDownY;

        if (viewportZoom > DEFAULT_Z_BASE)
        viewportZoom = (int) DEFAULT_Z_BASE;
        } else if (e.isShiftDown ()) {
        //
        // Shift the rotation coefficients.
        //
        viewportTheta += e.getX () - mouseDownX;
        viewportPhi += e.getY () - mouseDownY;
        } else if (e.isControlDown ()) {
          //
          // Shift the 3D viewport plane.
          //
          viewportPreProjectionX += e.getX () - mouseDownX;
          viewportPreProjectionY += e.getY () - mouseDownY;
        } else {
          //
          // Shift the viewport over.
          // 
          viewportX += e.getX () - mouseDownX;
          viewportY += e.getY () - mouseDownY;
        }

        //
        // Now, move the effective click location. This way,
        // we can move the viewport again by clicking and dragging
        // and it won't start back at the center of the window.
        // 
        mouseDownX = e.getX ();
        mouseDownY = e.getY ();

        //
        // Show the user a snapshot of the image.
        //

        t.enqueueGraphicsRefreshRequest (true, false);
        }
        }

        //
        // Empty methods
        //

        public void mouseMoved (MouseEvent e) {}

    });                                // }}}

    //
    // Step 2. Make the frame visible and set a default size. Also, set the
    //        default background color.
    //

    super.setSize (600, 372);
    super.setTitle ("Cheloniidae");
    super.setVisible (true);
    super.setBackground (Color.WHITE);

    //
    // Step 3. Generate the initial images and paint the screen.
    //

    regenerateImages ();
    enqueueGraphicsRefreshRequest (true, true);
  }                                  // }}}

  protected void initializeAntialiasing (Graphics2D g) {        // {{{
    //
    // Due to the architecture of rendering hints, we must first
    // load them into a variable, then change them, and finally
    // put them back into the graphics context in order to commit
    // changes. Otherwise, this would be a one-liner.
    //
    RenderingHints rh = g.getRenderingHints ();
    rh.put (rh.KEY_ANTIALIASING, rh.VALUE_ANTIALIAS_ON);
    g.setRenderingHints (rh);
  }                                  // }}}
  // }}}

  //
  // Graphical methods (available to user)
  //
  // {{{
  public void clear () {                        // {{{
    //
    // Clear off the background image and the list of lines.
    //

    lstLines.clear ();
    zMinimumExtent = zMaximumExtent = DEFAULT_Z_BASE;

    //
    // Once the list is clear, ask the window to refresh the graphics.
    // Since we've told it to redraw all lines, the screen will be clear.
    //
    enqueueGraphicsRefreshRequest (true, false);
  }                                  // }}}

  public void update (Graphics g) {                  // {{{
    paint (g);
  }                                  // }}}

  public void paint (Graphics g) {                  // {{{
    g.drawImage (imgTurtleLayer, 0, 0, null);
  }                                  // }}}

  public void enqueueGraphicsRefreshRequest (              // {{{
      boolean drawAllLines, boolean antialiased) {
    //
    // If the images are not yet initialized, then don't do anything.
    //

    if (imgTurtleOutput != null && imgTurtleLayer != null) {
      //
      // If we currently have a graphics request going, then
      // kill that thread and start over.
      //

      if (thrGraphicsRequestRunner != null && thrGraphicsRequestRunner.isAlive ()) {
        graphicsRequestCancelFlag = true;

        //
        // Wait for the graphics request runner to die.
        //
        try {
          thrGraphicsRequestRunner.join ();
        } catch (InterruptedException e) {}
      }

      graphicsRequestCancelFlag = false;
      thrGraphicsRequestRunner = 
        new Thread (new GraphicsThreadRunner (this, drawAllLines, antialiased));

      thrGraphicsRequestRunner.start ();
    }
  }                                  // }}}
  // }}}

  //
  // 3D methods (hidden from user)
  //
  // {{{
  protected double lineDistance (Line l) {              // {{{
    Point3D transformed = new Point3D ((l.x1 + l.x2) / 2.0,
        (l.y1 + l.y2) / 2.0,
        (l.z1 + l.z2) / 2.0);

    transformPoint (transformed);

    return Math.sqrt (transformed.X * transformed.X +
        transformed.Y * transformed.Y +
        transformed.Z * transformed.Z);
  }                                  // }}}

  protected final void swap (java.util.ArrayList l, int a, int b) {  // {{{
    Object ltmp = l.get (a);
    l.set (a, l.get (b));
    l.set (b, ltmp);
  }                                  // }}}

  protected void quickSort (java.util.ArrayList l, int a, int b) {  // {{{
    //
    // This quicksort algorithm keys on the cached distance
    // of each line in l. It sorts in reverse, so greater
    // distances are sorted before lesser distances.
    //

    if (a < b && !graphicsRequestCancelFlag) {
      int ia = a + 1;
      int ib = b + 1;
      int pivot = (a + b) / 2;

      swap (l, a, pivot);

      while (ia < ib && !graphicsRequestCancelFlag) {
        //
        // Bug fix: Sometimes the view takes a while to
        // respond. To fix this, we can break out of the
        // while loops as soon as the graphics cancel
        // flag is set.
        //
        while (ia < b &&
            ((Line) lstLines.get (ia)).cachedDistance > ((Line) lstLines.get (a)).cachedDistance &&
            !graphicsRequestCancelFlag)
          ia++;

        while (((Line) lstLines.get (--ib)).cachedDistance < ((Line) lstLines.get (a)).cachedDistance &&
            !graphicsRequestCancelFlag);

        if (ia < ib)
          swap (l, ia, ib);
      }

      swap (l, a, ib);

      quickSort (l, a, ib - 1);
      quickSort (l, ib + 1, b);
    }
  }                                  // }}}

  protected void resortLines () {                    // {{{
    //
    // Sorts the lines in order of distance from the viewport.
    // We do this so that when they are rendered, the lines don't overlap
    // each other unnaturally.
    //
    // This isn't a foolproof algorithm; there are cases where it will
    // make mistakes. However, it uses a decent heuristic that the center
    // of the line is what determines its distance.
    //
    // This algorithm does not allocate memory, which should
    // noticeably save time on large drawings.
    //

    synchronized (lstLines) {
      for (int i = 0; i < lstLines.size () && !graphicsRequestCancelFlag; i++)
        ((Line) lstLines.get (i)).cachedDistance = lineDistance ((Line) lstLines.get (i));

      quickSort (lstLines, 0, lstLines.size () - 1);
    }
  }                                  // }}}

  protected synchronized void transformPoint (Point3D p) {      // {{{
    //
    // This method considers the values of viewportTheta, viewportPhi,
    // and viewportX and viewportY and transforms a point
    // in space (p) to a point in the displayable 3D viewspace.
    //

    p.X -= (xMinimumExtent + xMaximumExtent) / 2.0;
    p.Y -= (yMinimumExtent + yMaximumExtent) / 2.0;
    p.Z -= (zMinimumExtent + zMaximumExtent) / 2.0;

    double ny = p.Y * Math.cos (Math.toRadians (viewportPhi)) -
      p.Z * Math.sin (Math.toRadians (viewportPhi));
    double nz = p.Y * Math.sin (Math.toRadians (viewportPhi)) +
      p.Z * Math.cos (Math.toRadians (viewportPhi));

    p.Y = ny;
    p.Z = nz;

    double nx = p.X * Math.cos (Math.toRadians (viewportTheta)) -
      p.Z * Math.sin (Math.toRadians (viewportTheta));
    nz = p.X * Math.sin (Math.toRadians (viewportTheta)) +
      p.Z * Math.cos (Math.toRadians (viewportTheta));

    p.X = nx;
    p.Z = nz;

    p.X += (xMinimumExtent + xMaximumExtent) / 2.0 + viewportPreProjectionX;
    p.Y += (yMinimumExtent + yMaximumExtent) / 2.0 + viewportPreProjectionY;
    p.Z += (zMinimumExtent + zMaximumExtent) / 2.0 + viewportZoom;
  }                                  // }}}

  protected void renderLine (                      // {{{
      BufferedImage target, Color c, int x1, int y1, int x2, int y2) {
    //
    // This method renders a single line sloppily onto an image.
    // Basically, it keeps filling in points until no two points
    // are more than 4 pixels apart. It uses binary splitting.
    //

    if ((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2) > LINE_POINT_MAXIMUM_DISTANCE) {
      renderLine (target, c, x1, y1, (x1 + x2) / 2, (y1 + y2) / 2);
      renderLine (target, c, (x1 + x2) / 2, (y1 + y2) / 2, x2, y2);
    } else
      target.setRGB ((x1 + x2) / 2, (y1 + y2)  / 2, c.getRGB ());
  }                                  // }}}

  protected double projectPoint (Point3D p) {              // {{{
    //
    // Performs an in-place projection of the point p into
    // 2D space. Returns the effective distance of the point
    // from the viewer, given the assumptions we're making
    // about the 3D perspective projection.
    //

    if (fisheye3D) {
      double l = p.length ();

      p.X *= DEFAULT_Z_BASE / l;
      p.Y *= DEFAULT_Z_BASE / l;

      return l;
    } else {
      p.X *= DEFAULT_Z_BASE / p.Z;
      p.Y *= DEFAULT_Z_BASE / p.Z;

      return p.Z;
    }
  }                                  // }}}

  protected void renderLine (                      // {{{
      Line l, Graphics2D g, BufferedImage target, boolean antialiasing) {
    //
    // This method renders a single line onto the screen.
    // I'm assuming that the graphics context has already been
    // initialized as far as antialiasing is concerned.
    //

    Point3D newPoint1 = new Point3D (l.x1, l.y1, l.z1);
    Point3D newPoint2 = new Point3D (l.x2, l.y2, l.z2);

    transformPoint (newPoint1);
    transformPoint (newPoint2);

    //
    // If either point is behind the camera (but not both), then solve
    // for the point at Z = 1. We then use that point for the end of the
    // line.
    //

    if (newPoint1.Z < 0.0 && newPoint2.Z < 0.0)
      return;
    else if (newPoint1.Z < 0.0 || newPoint2.Z < 0.0) {
      double difference_in_z = newPoint1.Z - newPoint2.Z;

      if (newPoint1.Z < 0.0) {
        double factor = Math.abs ((1.0 - newPoint1.Z) / difference_in_z);
        newPoint1.multiply (1.0 - factor);
        newPoint1.addScaled (newPoint2, factor);
      } else {
        double factor = Math.abs ((1.0 - newPoint2.Z) / difference_in_z);
        newPoint2.multiply (1.0 - factor);
        newPoint2.addScaled (newPoint1, factor);
      }
    }

    //
    // Perform perspective transformation.
    //

    double dp1 = projectPoint (newPoint1);
    double dp2 = projectPoint (newPoint2);

    if (antialiasing) {
      //
      // Do nice rendering with strokes and such.
      //
      g.setStroke (new BasicStroke ((float) Math.abs (l.width * DEFAULT_Z_BASE * 2.0 / 
              (dp1 + dp2))));
      g.setColor (l.lineColor);

      g.drawLine ((int) newPoint1.X + viewportX, (int) newPoint1.Y + viewportY,
          (int) newPoint2.X + viewportX, (int) newPoint2.Y + viewportY);
    } else
      //
      // Do a sloppy line.
      //
      // The check for in-boundedness is here for optimization reasons. If
      // we put it at the lowest level pixel-render, then every pixel would
      // have potentially four comparisons added to it. But here, we can get
      // away with eight for an entire line.
      //

      if (newPoint1.X + viewportX > 0 && newPoint1.X + viewportX < target.getWidth () &&
          newPoint1.Y + viewportY > 0 && newPoint1.Y + viewportY < target.getHeight () &&
          newPoint2.X + viewportX > 0 && newPoint2.X + viewportX < target.getWidth () &&
          newPoint2.Y + viewportY > 0 && newPoint2.Y + viewportY < target.getHeight ())

        renderLine (target, l.lineColor, 
            (int) newPoint1.X + viewportX, (int) newPoint1.Y + viewportY,
            (int) newPoint2.X + viewportX, (int) newPoint2.Y + viewportY);
  }                                  // }}}

  void updateExtents (                        // {{{
      double x1, double x2,
      double y1, double y2,
      double z1, double z2) {

    //
    // We use extents to figure out the center of rotation. The camera should
    // rotate around the center of the bounding box of the figure.
    //

    //
    // X
    //

    if (x1 < xMinimumExtent) xMinimumExtent = x1;
    if (x1 > xMaximumExtent) xMaximumExtent = x1;
    if (x2 < xMinimumExtent) xMinimumExtent = x2;
    if (x2 > xMaximumExtent) xMaximumExtent = x2;

    //
    // Y
    // 

    if (y1 < yMinimumExtent) yMinimumExtent = y1;
    if (y1 > yMaximumExtent) yMaximumExtent = y1;
    if (y2 < yMinimumExtent) yMinimumExtent = y2;
    if (y2 > yMaximumExtent) yMaximumExtent = y2;

    //
    // Z
    //

    if (z1 < zMinimumExtent) zMinimumExtent = z1;
    if (z1 > zMaximumExtent) zMaximumExtent = z1;
    if (z2 < zMinimumExtent) zMinimumExtent = z2;
    if (z2 > zMaximumExtent) zMaximumExtent = z2;
  }                                  // }}}
  // }}}

  //
  // Graphical methods (hidden from user)
  //
  // {{{
  protected void drawTurtles (boolean antialiased) {          // {{{
    //
    // Step 1: Set up antialiasing.
    //

    Graphics2D g = (Graphics2D) imgTurtleLayer.getGraphics ();

    if (antialiased)
      initializeAntialiasing (g);

    //
    // Step 2: Draw the turtle output on the image.
    //

    g.drawImage (imgTurtleOutput, 0, 0, null);

    //
    // Step 3: Draw each turtle.
    //

    ListIterator i = lstTurtles.listIterator ();
    while (i.hasNext () && !graphicsRequestCancelFlag) {
      Turtle t = (Turtle) i.next ();

      //
      // Draw only the turtles that are visible.
      //
      if (t.getVisible ()) {
        //
        // I want to draw turtles in their body colors. So I'm taking
        // the RGB components from the turtle and putting in an alpha of 127
        // (50% transparent) so that the user can see what's going on but also
        // see where the turtle is.
        //
        g.setColor (new Color (
              t.getBodyColor ().getRed (),
              t.getBodyColor ().getGreen (),
              t.getBodyColor ().getBlue (),
              TURTLE_ALPHA));

        //
        // Turtles are right now represented by 8x8-unit circles.
        //
        Point3D turtleLocation = new Point3D (t.getX (), t.getY (), t.getZ ());
        transformPoint (turtleLocation);
        double turtleSize = DEFAULT_Z_BASE / projectPoint (turtleLocation);

        g.setStroke (new BasicStroke ((float) Math.abs (turtleSize)));
        g.drawOval ((int) (turtleLocation.X - TURTLE_RADIUS * turtleSize) + viewportX,
            (int) (turtleLocation.Y - TURTLE_RADIUS * turtleSize) + viewportY,
            (int) (TURTLE_RADIUS * 2.0 * turtleSize), (int) (TURTLE_RADIUS * 2.0 * turtleSize));

        //
        // Show the turtle's heading with a 15-unit line.
        //

        Point3D turtleVector = t.computeTurtleVector (TURTLE_HEADING_LENGTH);

        renderLine (new Line (
              t.getX (), t.getY (), t.getZ (),
              t.getX () + turtleVector.X, t.getY () + turtleVector.Y, t.getZ () + turtleVector.Z,
              1.0, g.getColor ()), g, imgTurtleLayer, antialiased);
      }
    }
  }                                  // }}}

  protected void drawBackgroundObjects (boolean antialiased) {    // {{{
    //
    // Step 1. Set up antialiasing.
    //

    Graphics2D g = (Graphics2D) imgTurtleOutput.getGraphics ();

    if (antialiased)
      initializeAntialiasing (g);

    //
    // Step 2. Clear off the image.
    //

    g.setColor (super.getBackground ());
    g.fillRect (0, 0, imgTurtleOutput.getWidth (), imgTurtleOutput.getHeight ());

    //
    // Step 3. Iterate through the background objects and
    // draw each one of them.
    //

    Iterator i = lstBackgrounds.listIterator ();
    while (i.hasNext ()) {
      Iterator j = ((BackgroundObject) i.next ()).getLines ().listIterator ();
      while (j.hasNext ())
        renderLine ((Line) j.next (), g, imgTurtleOutput, antialiased);
    }
  }                                  // }}}

  protected void drawLines (boolean startOver, boolean antialiased) {  // {{{
    //
    // Step 1: Set up antialiasing.
    //

    Graphics2D g = (Graphics2D) imgTurtleOutput.getGraphics ();
    int skip = 1;

    if (antialiased)
      initializeAntialiasing (g);
    else
      //
      // Cap the number of lines drawn during an intermediate render.
      //

      if (lstLines.size () > INTERMEDIATE_RENDER_CUTOFF)
        skip = lstLines.size () / INTERMEDIATE_RENDER_CUTOFF;

    //
    // Step 2: If we are starting over, then redraw the background image
    // and set the linesDrawn counter to 0.
    //

    if (startOver) {
      //
      // Clear off the background image.
      //

      drawBackgroundObjects (antialiased);
      linesDrawn = 0;

      if (antialiased)
        resortLines ();
    }

    //
    // Step 3: Draw the lines onto the image.
    //

    synchronized (lstLines) {
      if (lstLines.size () > linesDrawn) {
        if (linesDrawn > 0)
          while (linesDrawn < lstLines.size () && !graphicsRequestCancelFlag)
            renderLine ((Line) lstLines.get (linesDrawn++), g, imgTurtleOutput, antialiased);
        else {
          for (int i = 0; i < lstLines.size () && !graphicsRequestCancelFlag; i += skip) {
            renderLine ((Line) lstLines.get (i), g, imgTurtleOutput, antialiased);

            if (antialiased && i % DRAWING_REFRESH_PER_LINES == 0) {
              imgTurtleLayer.getGraphics ().drawImage (imgTurtleOutput, 0, 0, null);
              repaint ();
            }
          }

          if (!graphicsRequestCancelFlag)
            linesDrawn = lstLines.size ();
        }
      }
    }
  }                                  // }}}
  // }}}

  //
  // Turtle management (available to user)
  //
  // {{{
  public void add (Turtle t) {                    // {{{
    t.setWindow (this);
    t.setLines (lstLines);
    lstTurtles.add (t);
  }                                  // }}}
  // }}}

  //
  // Background management (available to user)
  //
  // {{{
  public void add (BackgroundObject b) {                // {{{
    lstBackgrounds.add (b);
  }                                  // }}}
  // }}}
  // }}}

}                                        // }}}
