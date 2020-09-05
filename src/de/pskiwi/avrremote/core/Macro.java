/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.pskiwi.avrremote.core;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import de.pskiwi.avrremote.log.Logger;

public final class Macro {

	private interface IMacroCommand {
		void run(ResilentConnector connector);

		void save(PrintWriter out);
	}

	private class SendCommand implements IMacroCommand {

		public SendCommand(String cmd) {
			this.cmd = cmd;
		}

		public void run(ResilentConnector connector) {
			connector.send(cmd);
		}

		public void save(PrintWriter out) {
			out.println("CMD:" + cmd);
		}

		@Override
		public String toString() {
			return cmd;
		}

		private final String cmd;
	}

	private class DelayCommand implements IMacroCommand {

		public DelayCommand(String param) {
			this.delay = Integer.parseInt(param);
		}

		public void run(ResilentConnector connector) {
			try {
				Thread.sleep(delay * 100);
			} catch (InterruptedException e) {
				Logger.error("sleep failed", e);
			}
		}

		public void save(PrintWriter out) {
			out.println("DELAY:" + delay);
		}

		private final int delay;
	}

	public Macro(String name) {
		this.name = name;
	}

	public void add(String cmd, String param) {
		try {
			if (cmd.equals("CMD")) {
				cmds.add(new SendCommand(param));
			}
			if (cmd.equals("DELAY")) {
				cmds.add(new DelayCommand(param));
			}
		} catch (Throwable x) {
			Logger.error("parse [" + cmd + "] [" + param + "]failed", x);
		}
	}

	public void run(final ResilentConnector connector) {
		final Thread thread = new Thread("Macro-Runner-" + name) {
			public void run() {
				try {
					Logger.info("run macro [" + name + "]");
					for (IMacroCommand s : cmds) {
						Logger.info("macro send [" + s + "]");
						s.run(connector);
					}
				} catch (Throwable x) {
					Logger.error("run macro " + name + " failed", x);
				}
			};
		};
		thread.start();
	}

	@Override
	public String toString() {
		return "Macro " + name;
	}

	public String getName() {
		return name;
	}

	public void save(PrintWriter out) {
		out.println("NAME:" + name);
		for (IMacroCommand c : cmds) {
			c.save(out);
		}
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isEmpty() {
		return cmds.isEmpty();
	}

	private String name;
	private final List<IMacroCommand> cmds = new ArrayList<IMacroCommand>();

}
