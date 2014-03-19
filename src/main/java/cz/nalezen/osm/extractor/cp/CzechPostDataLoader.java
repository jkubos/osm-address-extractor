package cz.nalezen.osm.extractor.cp;

import java.io.BufferedReader;
import java.io.FileReader;

import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;

import cz.nalezen.osm.extractor.data.City;
import cz.nalezen.osm.extractor.data.City.AuxiliaryData;
import cz.nalezen.osm.extractor.data.CzechRepublicAddresses;
import cz.nalezen.osm.extractor.data.District;

public class CzechPostDataLoader {
	
	private CzechRepublicAddresses root;
	private String path;
	
	private static final Logger logger = Logger.getLogger(CzechPostDataLoader.class);

	public CzechPostDataLoader(CzechRepublicAddresses root, String path) {
		this.root = root;
		this.path = path;
	}
	
	public void load() {
		boolean first = true;
		
		try(BufferedReader br = new BufferedReader(new FileReader(path))) {
		    for(String line; (line = br.readLine()) != null; ) {
		    
		    	//skip header
		    	if (first) {
		    		first = false;
		    		continue;
		    	}
		    	
		      parsePostCode(line);
		    }
		   
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private void parsePostCode(String line) {
		String[] items = line.split("\\|");
		
		if (items.length!=6) {
			throw new RuntimeException();
		}
		
		int postCode = NumberUtils.toInt(items[1], -1);
		String districtName = items[4];
		String cityName = items[5];
		
		District district = root.lookupDistrict(districtName);
		
		if (district==null) {
			logger.warn(String.format("Cannot lookup district '%s'!", districtName));
			return;
		}
		
		City city = district.lookupCity(cityName);
		
		if (city==null) {
			logger.warn(String.format("Cannot lookup city '%s' within district '%s'!", cityName, districtName));
			return;
		}
		
		AuxiliaryData aux = city.assureAuxiliaryData();
		
		if (postCode<aux.minPostcodeMvcr || postCode>aux.maxPostcodeMvcr) {
			logger.warn(String.format("City '%s' within district '%s' is out of postcode range - %d! Allowed range <%d, %d>", 
					cityName, districtName, postCode, aux.minPostcodeMvcr, aux.maxPostcodeMvcr));
		}
		
		city.getPostcodes().add(postCode);
	}
}
