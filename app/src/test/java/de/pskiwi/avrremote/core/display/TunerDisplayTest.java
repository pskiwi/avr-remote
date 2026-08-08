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
package de.pskiwi.avrremote.core.display;

import static org.junit.Assert.assertEquals;

import java.text.DecimalFormatSymbols;

import org.junit.Test;

import de.pskiwi.avrremote.core.InData;

/**
 * Die Frequenzanzeige des analogen Tuners. Das Format steht im DENON AVR
 * control protocol Ver. 5.2 (AVR-3808CI, Juli 2007):
 *
 * <pre>
 * TF  AN****** (6 digits)  --- ****.** kHz at AM band   (&gt;050000 is AM.)
 *                              ****.** MHz at FM band
 * </pre>
 *
 * Sechs Ziffern, hundertstelgenau, und die Bandgrenze liegt bei 050000 - was
 * darüber liegt ist AM, alles bis dahin FM.
 *
 * Nur die Umrechnung ist hier abgedeckt. Der Rest von TunerDisplay - Vorwahlen,
 * HD-Radio, DAB, die Statuszeilen - hat weiterhin keine Tests.
 */
public final class TunerDisplayTest {

	@Test
	public void frequencyBelowTheBoundaryIsFM() {
		assertEquals("FM : 104" + sep() + "30 MHz", frequency("10430"));
	}

	@Test
	public void frequencyAboveTheBoundaryIsAM() {
		assertEquals("AM : 1050" + sep() + "00 kHz", frequency("105000"));
	}

	/**
	 * Der Grenzwert selbst gehört noch zu FM: das Papier schreibt
	 * "&gt;050000 is AM", nicht "&gt;=". Ein echter Tuner steht nie dort - UKW
	 * endet bei 10800, MW beginnt bei 52200 -, aber die Grenze soll da liegen,
	 * wo sie beschrieben ist.
	 */
	@Test
	public void theBoundaryItselfIsStillFM() {
		assertEquals("FM : 500" + sep() + "00 MHz", frequency("050000"));
	}

	/** Ohne verwertbare Zahl bleibt die Anzeige leer statt falsch. */
	@Test
	public void unparsableFrequencyIsIgnored() {
		assertEquals("", frequency("CMP"));
	}

	/**
	 * Baut einen UKW-Tuner und schiebt ihm eine TF-Nutzlast unter.
	 * ModelConfigurator und ISender werden dabei nicht angefasst, und der
	 * Anzeige-Zuhörer steht auf dem Null-Objekt.
	 */
	private static String frequency(String payload) {
		final TunerDisplay td = TunerDisplay.createFM(null, null);
		final TunerDisplay.TunerFrequency f = td.new TunerFrequency();
		f.update(new InData(payload));
		return f.getFrequency();
	}

	/** String.format richtet sich nach der Standardsprache, der Test auch. */
	private static char sep() {
		return new DecimalFormatSymbols().getDecimalSeparator();
	}
}
