package cz.nalezen.osm.extractor;

import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import cz.nalezen.osm.extractor.cp.CzechPostDataLoader;
import cz.nalezen.osm.extractor.data.CzechRepublicAddresses;
import cz.nalezen.osm.extractor.mvcr.MvcrDataLoader;
import cz.nalezen.osm.extractor.osm.OsmDataLoader;

public class CzAddressExtractor {
	CzechRepublicAddresses root = new CzechRepublicAddresses();
	
	public void importMvcrData(String path) {
		MvcrDataLoader loader = new MvcrDataLoader(root, path);
		loader.load();
	}
	
	public void importCzechPostData(String path) {
		CzechPostDataLoader loader = new CzechPostDataLoader(root, path);
		loader.load();
	}
	
	public void importOsmData(String path) {
		OsmDataLoader loader = new OsmDataLoader(root, path);
		loader.load();
	}
	
	public CzechRepublicAddresses getRoot() {
		return root;
	}
	
	public void save(String path) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
			mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
			
			mapper.writeValue(new File(path), root);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public static CzechRepublicAddresses load(String path) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			
			CzechRepublicAddresses res = mapper.readValue(new File(path), CzechRepublicAddresses.class);

			return res;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
