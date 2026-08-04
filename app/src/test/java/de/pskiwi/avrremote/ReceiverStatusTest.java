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
package de.pskiwi.avrremote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.pskiwi.avrremote.EnableManager.StatusFlag;

/**
 * {@link ReceiverStatus#toString()} landet bei jedem Statuswechsel im Log und
 * ist damit das, woran sich eine Auswertung entlanghangelt. Zwei solche Zeilen
 * lassen sich nur vergleichen, wenn die Flags immer in derselben Reihenfolge
 * stehen - über die {@link java.util.concurrent.ConcurrentHashMap} darunter gilt
 * das nicht, im Feld-Log vom 03.08.2026 erscheint dieselbe Sechsermenge in drei
 * verschiedenen Sortierungen.
 */
public final class ReceiverStatusTest {

	@Test
	public void orderDoesNotDependOnInsertion() {
		final StatusFlag[] flags = { StatusFlag.Logging, StatusFlag.WLAN,
				StatusFlag.Reachable, StatusFlag.Connected, StatusFlag.Power,
				StatusFlag.Zone1, StatusFlag.Zone2 };

		final ReceiverStatus forward = new ReceiverStatus();
		for (int i = 0; i < flags.length; i++) {
			forward.set(flags[i], i % 2 == 0);
		}

		final ReceiverStatus backward = new ReceiverStatus();
		for (int i = flags.length - 1; i >= 0; i--) {
			backward.set(flags[i], i % 2 == 0);
		}

		assertEquals(forward.toString(), backward.toString());
	}

	@Test
	public void orderFollowsTheEnum() {
		final ReceiverStatus s = new ReceiverStatus();
		s.set(StatusFlag.Zone2);
		s.set(StatusFlag.Connected);
		s.set(StatusFlag.WLAN);

		assertEquals("[WLAN:true, Connected:true, Zone2:true]", s.toString());
	}

	/**
	 * {@code reset()} nimmt das Flag heraus, {@code unset()} setzt es auf false -
	 * "unbekannt" und "aus" sind im Log zwei verschiedene Aussagen.
	 */
	@Test
	public void resetRemovesWhileUnsetShowsFalse() {
		final ReceiverStatus s = new ReceiverStatus();
		s.set(StatusFlag.Power);
		s.set(StatusFlag.Zone1);

		s.unset(StatusFlag.Power);
		s.reset(StatusFlag.Zone1);

		assertTrue(s.toString().contains("Power:false"));
		assertFalse(s.toString().contains("Zone1"));
	}
}
