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
/**
 * 
 */
package de.pskiwi.avrremote.core;

import de.pskiwi.avrremote.EnableManager;
import de.pskiwi.avrremote.EnableManager.StatusFlag;

public enum Zone {
	Main("Z", StatusFlag.Zone1), Z2("Z2", StatusFlag.Zone2), Z3("Z3",
			StatusFlag.Zone3), Z4("Z4", StatusFlag.Zone4);

	private Zone(String prefix, EnableManager.StatusFlag flag) {
		this.prefix = prefix;
		this.flag = flag;
	}

	public String getPrefix() {
		return prefix;
	}

	public String getCommandPrefix(IAVRState s) {
		if (this == Main) {
			return s.getCommandPrefix();
		}

		if (!s.isCommandSecondaryZoneEncoded()) {
			return prefix + s.getCommandPrefix();
		}

		return prefix;
	}

	public StatusFlag getFlag() {
		return flag;
	}

	public String getRenameKey() {
		switch (this) {
		case Main:
			return "zone1";
		case Z2:
			return "zone2";
		case Z3:
			return "zone3";
		case Z4:
			return "zone4";
		default:
			throw new IllegalArgumentException("" + this);
		}
	}

	public String getTabTag() {
		return getRenameKey();
	}

	public int getRessource() {
		switch (this) {
		case Main:
			return de.pskiwi.avrremote.R.string.Zone1;
		case Z2:
			return de.pskiwi.avrremote.R.string.Zone2;
		case Z3:
			return de.pskiwi.avrremote.R.string.Zone3;
		case Z4:
			return de.pskiwi.avrremote.R.string.Zone4;
		default:
			throw new IllegalArgumentException("" + this);
		}
	}

	public int getLayoutId() {
		switch (this) {
		case Main:
			return de.pskiwi.avrremote.R.id.zone1;
		case Z2:
			return de.pskiwi.avrremote.R.id.zone2;
		case Z3:
			return de.pskiwi.avrremote.R.id.zone3;
		case Z4:
			return de.pskiwi.avrremote.R.id.zone4;
		default:
			throw new IllegalArgumentException("" + this);
		}
	}

	public int getZoneNumber() {
		switch (this) {
		case Main:
			return 0;
		case Z2:
			return 1;
		case Z3:
			return 2;
		case Z4:
			return 3;
		default:
			throw new IllegalArgumentException("" + this);
		}
	}

	/** 0=MAIN,1=Z2,... */
	public static Zone fromNumber(int i) {
		switch (i) {
		case 0:
			return Main;
		case 1:
			return Z2;
		case 2:
			return Z3;
		case 3:
			return Z4;
		}
		throw new IllegalArgumentException("" + i);
	}

	public String getQuickPrefix() {
		switch (this) {
		case Main:
			return "MS";
		case Z2:
			return "Z2";
		case Z3:
			return "Z3";
		case Z4:
			return "Z4";
		default:
			throw new IllegalArgumentException("" + this);
		}
	}

	public static Zone fromId(String id) {
		if (id == null || id.length() == 0) {
			return null;
		}
		return valueOf(id);
	}

	public String getId() {
		return super.toString();
	}

	private final String prefix;
	private final StatusFlag flag;

	public final static String INTENT_KEY = "zone";

}