package breezeclient.breezeclient.core.command;

import java.util.function.Consumer;

public interface Command {
	String name();

	String usage();

	void run(String[] args, Consumer<String> reply);
}
