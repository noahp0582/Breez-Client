package breezeclient.breezeclient.client.debug;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/** Forwards WARN and ERROR lines from Minecraft's log (vanilla and other mods) to {@link DebugLog} while enabled. */
final class GameAppender extends AbstractAppender {
	private GameAppender() {
		super("BreezDebug", null, null, true, Property.EMPTY_ARRAY);
	}

	static Object attach() {
		GameAppender a = new GameAppender();
		a.start();
		((Logger) LogManager.getRootLogger()).addAppender(a);
		return a;
	}

	@Override
	public void append(LogEvent event) {
		if (!DebugLog.gameWarnings() || !event.getLevel().isMoreSpecificThan(Level.WARN)) return;
		String logger = event.getLoggerName() == null ? "" : event.getLoggerName();
		if (logger.equals("breez-client")) return; // already recorded through the java.util.logging handler
		String source = logger.isEmpty() ? "game" : logger.substring(logger.lastIndexOf('.') + 1);
		DebugLog.Kind kind = event.getLevel().isMoreSpecificThan(Level.ERROR) ? DebugLog.Kind.ERROR : DebugLog.Kind.WARN;
		DebugLog.record(kind, source, event.getMessage().getFormattedMessage(), event.getThrown());
	}
}
