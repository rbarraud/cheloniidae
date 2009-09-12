package cheloniidae.commands;

import cheloniidae.Turtle;
import cheloniidae.TurtleCommand;
import cheloniidae.Transformation;

public class CommandSequence implements TurtleCommand {
  protected TurtleCommand intersperse = null;

  public final TurtleCommand[] commands;
  public CommandSequence (final TurtleCommand ... _commands) {commands = _commands;}

  public CommandSequence applyTo (final Turtle t) {if (intersperse != null) t.run (intersperse);
                                                   for (TurtleCommand c : commands) {
                                                     t.run (c);
                                                     if (intersperse != null) t.run (intersperse);
                                                   }
                                                   return this;}

  public CommandSequence interspersing (final TurtleCommand c) {
    intersperse = intersperse == null || c == null ? c : new CommandSequence (c, intersperse);
    return this;
  }

  public TurtleCommand map (final Transformation<TurtleCommand> t) {
    final TurtleCommand newCommand = t.transform (this);
    if (newCommand == this) {
      final TurtleCommand[] cs = new TurtleCommand[commands.length];
      for (int i = 0; i < commands.length; ++i) cs[i] = commands[i].map (t);
      return new CommandSequence (cs).interspersing (intersperse != null ? intersperse.map (t) : null);
    } else return newCommand;
  }
}
