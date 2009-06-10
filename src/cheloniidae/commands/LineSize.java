package cheloniidae.commands;

import cheloniidae.Turtle;

public class LineSize extends UnaryCommand<LineSize, Double> {
  public LineSize applyTo (Turtle t) {
    if (t instanceof SupportsLineSize) ((SupportsLineSize) t).lineSize (value);
    return this;
  }
}