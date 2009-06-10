package cheloniidae.commands;

import cheloniidae.Turtle;

import java.awt.Color;

public class BodyColor extends UnaryCommand<Color> {
  public BodyColor (Color value) {super (value);}
  public BodyColor applyTo (Turtle t) {
    if (t instanceof SupportsBodyColor) ((SupportsBodyColor) t).bodyColor (value);
    return this;
  }
}
