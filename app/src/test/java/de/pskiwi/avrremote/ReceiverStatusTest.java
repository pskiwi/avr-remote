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
 * das nicht: die iteriert nach Hash, und der ist bei Enum-Konstanten der
 * Identity-Hash und damit von Lauf zu Lauf verschieden. Im Feld-Log vom
 * 03.08.2026 erscheinen 3 von 11 Flag-Mengen in mehr als einer Sortierung.
 *
 * <p>
 * Deshalb pinnt {@link #orderFollowsTheEnum()} die Reihenfolge gegen den
 * erwarteten String und nicht zwei Instanzen gegeneinander: ein Vergleich
 * zweier Instanzen faellt gegen die alte Implementierung nur, wenn die
 * Identity-Hashes im jeweiligen Lauf gerade unguenstig liegen.
 */
public final class ReceiverStatusTest {

	@Test
	public void emptyStatusPrintsEmptyBrackets() {
		assertEquals("[]", new ReceiverStatus().toString());
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
