package cz.nalezen.osm.extractor;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

import org.openstreetmap.osmosis.core.task.v0_6.Sink;

import crosby.binary.osmosis.OsmosisReader;
import cz.nalezen.osm.extractor.GeoExtractor.CityData;

public class OsmAddressExtractor {

	private GeoExtractor geoExtractor = new GeoExtractor();
	private CustomSink sink = new CustomSink(geoExtractor);

	public void extract(String path) {
		geoExtractor.reset();

		while (geoExtractor.needsAnotherPass()) {
			System.out.println("===========================PASS "+geoExtractor.getPassNumber()+"==============================");
			
			geoExtractor.passStart();
			
			readFile(geoExtractor, sink, path);
			
			geoExtractor.passDone();
		}
	}
	
	private void readFile(GeoExtractor geoExtractor, Sink sink, String path) {
		File f = new File(path);

		try {
			OsmosisReader reader = new OsmosisReader(new FileInputStream(f));
			reader.setSink(sink);
			reader.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void renderResult(String imgPath) {
		MapPrinter.renderCities(imgPath, geoExtractor.getExtractedCities());
	}
	
	public ArrayList<CityData> getExtractedData() {
		return geoExtractor.getExtractedCities();
	}
}
