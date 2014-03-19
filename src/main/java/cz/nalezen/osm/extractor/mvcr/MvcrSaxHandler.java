package cz.nalezen.osm.extractor.mvcr;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import cz.nalezen.osm.extractor.data.City;
import cz.nalezen.osm.extractor.data.CzechRepublicAddresses;
import cz.nalezen.osm.extractor.data.District;
import cz.nalezen.osm.extractor.data.Street;

class MvcrSaxHandler extends DefaultHandler {

	private CzechRepublicAddresses root;
	
	private District districtCtx = null;
	private City cityCtx = null;
	private Street streetCtx = null;
	
	private static final Logger logger = Logger.getLogger(MvcrSaxHandler.class);
	
	public MvcrSaxHandler(CzechRepublicAddresses root) {
		this.root = root;
	}
	
	@Override
	public void startElement(String uri, String localName,String qName, Attributes attributes) throws SAXException {
		if (qName.equals("adresy")) {
			//root
		} else if (qName.equals("oblast")) {
			handleDistrict(qName, attributes);
		} else if (qName.equals("obec")) {
			handleCity(qName, attributes);
		} else if (qName.equals("cast")) {
			//not used
		} else if (qName.equals("ulice")) {
			handleStreet(qName, attributes);
		} else if (qName.equals("a")) {
			handleAddress(qName, attributes);
		} else {
			logger.error(String.format("Unhandled tag '%s'!", qName));
		}
	}
	
	@Override
	public void endElement (String uri, String localName, String qName) throws SAXException {
		if (qName.equals("adresy")) {
			//root
		} else if (qName.equals("oblast")) {
			districtCtx = null;
		} else if (qName.equals("obec")) {
			cityCtx = null;
		} else if (qName.equals("cast")) {
			//not used
		} else if (qName.equals("ulice")) {
			streetCtx = null;
		} else if (qName.equals("a")) {
			//
		} else {
			logger.error(String.format("Unhandled tag '%s'!", qName));
		}
	}

	private void handleDistrict(String qName, Attributes attributes) {
		String districtName = attributes.getValue("okres");
		
		//ugly hack to define district for capital city Prague compatible with Czech Post data
		if ("praha".equals(attributes.getValue("typ"))) {
			districtName = "Hlavní město Praha";
		}
		
		if (StringUtils.isBlank(districtName)) {
			logger.warn(String.format("Problematic district: name=%s okres=%s kraj=%s\n", 
					attributes.getValue("nazev"),
					districtName,
					attributes.getValue("kraj")));
			return;
		}
		
		districtCtx = root.assureDistrict(districtName);
	}

	private void handleCity(String qName, Attributes attributes) {
		String cityName = attributes.getValue("nazev");
		
		if (!hasDistrictCtx(qName, cityName)) {
			return;
		}
		
		if (StringUtils.isBlank(cityName)) {
			logger.warn(String.format("Problematic city: name=%s\n", 
					cityName));
			return;
		}
		
		cityCtx = districtCtx.assureCity(cityName);
		
		cityCtx.assureAuxiliaryData().minPostcodeMvcr = NumberUtils.toInt(attributes.getValue("MinPSC"), -1);
		cityCtx.assureAuxiliaryData().maxPostcodeMvcr = NumberUtils.toInt(attributes.getValue("MaxPSC"), -1);
	}

	private void handleStreet(String qName, Attributes attributes) {
		
		String streetName = attributes.getValue("nazev");
		
		if (!hasCityCtx(qName, streetName)) {
			return;
		}
		
		streetCtx = cityCtx.assureStreet(streetName);
	}
	
	private void handleAddress(String qName, Attributes attributes) {
		
		if (!hasCityOrStreetCtx(qName, "*address*")) {
			return;
		}
		
		int streetNr = NumberUtils.toInt(attributes.getValue("o"), -1);
		int conscriptionNr  = NumberUtils.toInt(StringUtils.isBlank(attributes.getValue("p")) ? attributes.getValue("e") : attributes.getValue("p"), -1);
		
		if (streetCtx!=null) {
			streetCtx.assureAddress(streetNr, conscriptionNr);
		} else {
			cityCtx.assureAddress(streetNr, conscriptionNr);
		}
	}
	
	private boolean hasCityOrStreetCtx(String qName, String name) {
		if (cityCtx==null && streetCtx==null) {
			logger.error(String.format("Tag '%s' with name '%s' out of city or street!", qName, name));
			return false;
		}
		
		return true;
	}

	private boolean hasDistrictCtx(String qName, String cityName) {
		if (districtCtx==null) {
			logger.error(String.format("Tag '%s' with name '%s' out of district!", qName, cityName));
			return false;
		}
		
		return true;
	}
	
	private boolean hasCityCtx(String qName, String streetName) {
		if (cityCtx==null) {
			logger.error(String.format("Tag '%s' with name '%s' out of city!", qName, streetName));
			return false;
		}
		
		return true;
	}
}