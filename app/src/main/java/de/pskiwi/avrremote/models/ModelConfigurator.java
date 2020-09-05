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
package de.pskiwi.avrremote.models;

import java.util.List;

import android.content.Context;
import de.pskiwi.avrremote.AVRSettings;
import de.pskiwi.avrremote.core.ConnectionConfiguration;
import de.pskiwi.avrremote.core.IParameterConverter;
import de.pskiwi.avrremote.core.OptionType;
import de.pskiwi.avrremote.core.RenameService;
import de.pskiwi.avrremote.core.Selection;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.ZoneState.OptionGroup;
import de.pskiwi.avrremote.core.display.DisplayManager.DisplayType;
import de.pskiwi.avrremote.http.AVRXMLInfo;
import de.pskiwi.avrremote.log.Logger;

public final class ModelConfigurator {

	public ModelConfigurator(Context ctx, RenameService renameService) {
		this.ctx = ctx;
		this.renameService = renameService;
		currentReceiver = AVRSettings.getAVRIndex(ctx);
		update();
	}

	public void update() {
		// "AVR-3310" -> "AVR3310"
		final String am = AVRSettings.getAVRModel(ctx, currentReceiver)
				.replace("-", "").trim();
		final String avrModel;
		// "(experimental)" weg
		int p = am.indexOf("(");
		if (p != -1) {
			avrModel = am.substring(0, p).trim();
		} else {
			avrModel = am;
		}

		// "North America" -> "NorthAmerica"
		final String avrModelArea = AVRSettings.getAVRModelArea(ctx)
				.replace(" ", "").trim();
		if (avrModelArea.length() == 0) {
			area = ModelArea.autoDetect();
		} else {
			area = ModelArea.valueOf(avrModelArea);
		}
		Logger.info("Area set to " + area);
		if (avrModel.length() == 0) {
			model = new AVRGeneric();
		} else {
			try {
				model = (IAVRModel) Class.forName(
						"de.pskiwi.avrremote.models." + avrModel).newInstance();
			} catch (Exception x) {
				Logger.error("create " + avrModel + " failed", x);
				model = new AVRGeneric();
			}
		}
		Logger.info("model is " + model.getClass().getName() + " area is "
				+ area);

		updateZoneState();

	}

	private void updateZoneState() {
		Logger.info("update zone state ...");
		if (xmlState != null && xmlState.isDefined()
				&& AVRSettings.isUseReceiverSettings(ctx)) {
			Logger.info("update zone state from XML");
			Logger.info("inputs:" + xmlState.getInputSelection());

			inputSelection.update(xmlState.getInputSelection());
			Logger.info("input selection : " + inputSelection);
			int zone = 0;
			for (String s : xmlState.getZoneRenames()) {
				if (zone < model.getZoneCount()) {
					if (s.trim().length() > 0) {
						renameService.setZoneName(Zone.fromNumber(zone), s);
					}
				}
				zone++;
			}
			for (Zone z : Zone.values()) {
				final List<String> quickNames = xmlState.getQuickNames(z);
				if (!quickNames.isEmpty()) {
					renameService.setQuickName(z, quickNames);
				}
			}
			renameService.save();
		} else {
			Logger.info("update zone state from model");
			inputSelection.update(model.getInputSelection(area));
		}

		surroundSelection.update(model.getSurroundSelection(area));
		videoSelection.update(model.getVideoSelection(area));
	}

	public IAVRModel getModel() {
		return model;
	}

	public ModelArea getArea() {
		return area;
	}

	public ConnectionConfiguration getConnectionConfig() {
		return new ConnectionConfiguration(AVRSettings.getAVRIP(ctx,
				currentReceiver));
	}

	public int getZoneCount() {
		int max = model.getZoneCount();
		String avrZoneCount = AVRSettings.getAVRZoneCount(ctx).toUpperCase()
				.trim();
		if (avrZoneCount.length() == 0 || avrZoneCount.equals("DEFAULT")) {
			return max;
		}
		int c = Integer.parseInt(avrZoneCount);
		if (c <= max) {
			return c;
		}
		return max;
	}

	public void setXMLInfol(AVRXMLInfo state) {
		Logger.info("got XML State");
		this.xmlState = state;
		updateZoneState();
	}

	public String getXMLInfo() {
		if (xmlState == null) {
			return "(No XML-Info)";
		}
		return xmlState.getInfo();
	}

	public Selection getInputSelection() {
		return inputSelection;
	}

	public Selection getSurroundSelection() {
		return surroundSelection;
	}

	public Selection getVideoSelection() {
		return videoSelection;
	}

	public boolean isInput(String s) {
		for (String i : inputSelection.getValues()) {
			if (i.equals(s)) {
				return true;
			}
		}
		for (String i : AVRGeneric.DEFAULT_INPUTS) {
			if (i.equals(s)) {
				return true;
			}
		}

		return false;
	}

	public boolean isNapsterEnabled() {
		return AVRSettings.isNapsterEnabled(ctx);
	}

	public void selectReceiver(int i) {
		currentReceiver = i;
		AVRSettings.setAVRIndex(ctx, currentReceiver);
	}

	public int getCurrentReceiver() {
		return currentReceiver;
	}

	public int getIPodDisplayRows() {
		return model.getIPodDisplayRows();
	}

	private boolean isIpodInput(String input) {
		if (input.contains("IPOD") || "IPD".equals(input)) {
			return true;
		}
		final String iPodInput = AVRSettings.getIPodInput(ctx);
		if (iPodInput.length() > 0) {
			return input.equalsIgnoreCase(iPodInput);
		}
		return false;
	}

	public DisplayType getDisplayTypeForInput(String input) {
		if (isIpodInput(input)) {
			return DisplayType.IPOD;
		}
		return getModel().getDisplayTypeForInput(input);
	}

	public boolean hasOptionGroup(OptionGroup group) {
		for (OptionType type : getModel().getSupportedOptions()) {
			if (type.getOptionGroup() == group) {
				return true;
			}
		}
		return false;
	}

	public boolean hasOption(OptionType optionType) {
		return getModel().getSupportedOptions().contains(optionType);
	}

	public IParameterConverter getSleepTransformer() {
		return new IParameterConverter() {
			public String convert(String s) {
				return getModel().getSleepTransformer().convert(s);
			}
		};
	}

	public boolean supportsDAB() {
		return area == ModelArea.Europe && getModel().supportsDAB();
	}

	private int currentReceiver = 0;
	private final Selection inputSelection = new Selection(
			AVRGeneric.DEFAULT_INPUTS);
	private final Selection surroundSelection = new Selection(
			AVRGeneric.SURROUND_MODES);
	private final Selection videoSelection = new Selection(
			AVRGeneric.DEFAULT_VIDEO_SELECT);

	private AVRXMLInfo xmlState;
	private final Context ctx;
	private ModelArea area = ModelArea.Other;
	private IAVRModel model = new AVRGeneric();
	private final RenameService renameService;

}
