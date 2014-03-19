package cz.nalezen.osm.extractor;

import java.io.File;
import java.util.ArrayList;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import cz.nalezen.osm.extractor.GeoExtractor.CityData;

public class JsonPersistor {
	
	static class Root {
		public ArrayList<CityData> cities;
	}
	
	@SuppressWarnings("deprecation")
	public static void save(String path, ArrayList<CityData> data) {
		try {
			Root root = new Root();
			root.cities = data;
			
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
			mapper.configure(SerializationConfig.Feature.WRITE_NULL_PROPERTIES, false);
			
			mapper.writeValue(new File(path), root);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	public static ArrayList<CityData> load(String path) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			Root root = mapper.readValue(new File(path), Root.class);
			
			return root.cities;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
