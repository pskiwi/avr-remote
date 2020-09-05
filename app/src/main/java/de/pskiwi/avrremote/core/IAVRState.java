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

/** Ein Zustand des AVR (z.B. Lautstärke/Display) */
public interface IAVRState
{
	/** Mit welchem Prefix sollen Stati empfangen werden ? */
	String getReceivePrefix();
	
	/** Prefix/Befehl im Protokoll (z.B. MV) */
	String getCommandPrefix();

	/** Mit neuer Nachricht aktualisieren */
	boolean update(InData v);

	/** Ist der Wert definiert (über AVR-Nachricht) ? */
	boolean isDefined();

	/** Ressourcen Kennung für die Anzeige*/
	int getDisplayId();

	/** Sind Befehle für Zone 2..n ohne Command-Prefix auszuführen. Bsp: Z1: MVUP, Z2: Z2UP */
	boolean isCommandSecondaryZoneEncoded();

	/** Auf Ausgangsstatus zurück */	
	void reset();
	
	/** Automatisch abfragen, nicht nur manuell */
	boolean isAutoUpdate();
}
