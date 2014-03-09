package cz.nalezen.osm.extractor;

import java.io.File;
import java.util.ArrayList;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import cz.nalezen.osm.extractor.GeoExtractor.CityData;

public class JsonPersistor {
	
	private static class Root {
		private ArrayList<CityData> cities;

		public ArrayList<CityData> getCities() {
			return cities;
		}

		public void setCities(ArrayList<CityData> cities) {
			this.cities = cities;
		}
	}
	
	public static void save(String path, ArrayList<CityData> data) {
		try {
			Root root = new Root();
			root.setCities(data);
			
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
			
			mapper.writeValue(new File(path), root);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
