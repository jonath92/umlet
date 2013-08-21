package com.umlet.element.experimental.facets.defaults;

import com.baselet.diagram.draw.BaseDrawHandler;
import com.umlet.element.experimental.PropertiesConfig;
import com.umlet.element.experimental.facets.KeyValueGlobalFacet;

public class FontSizeFacet extends KeyValueGlobalFacet {
	
	public static FontSizeFacet INSTANCE = new FontSizeFacet();
	private FontSizeFacet() {}

	@Override
	public KeyValue getKeyValue() {
		return new KeyValue("fontsize", "12", "font size (12.5, 10.3,...)");
	}

	@Override
	public void handleValue(String value, BaseDrawHandler drawer, PropertiesConfig propConfig) {
		drawer.setFontSize(value);
	}

}
