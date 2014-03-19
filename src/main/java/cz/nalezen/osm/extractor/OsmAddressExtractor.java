package cz.nalezen.osm.extractor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.ArrayList;

import org.openstreetmap.osmosis.core.task.v0_6.Sink;

import com.vividsolutions.jts.geom.Envelope;

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

	public void renderResult(String imgPath, int width, int height, Envelope envelope) {
		MapPrinter printer = new MapPrinter(width, height, envelope);
		printer.renderCities(imgPath, geoExtractor.getExtractedCities());
	}
	
	public ArrayList<CityData> getExtractedData() {
		return geoExtractor.getExtractedCities();
	}

	public void loadPostCodes(String path) {
		
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
		
		geoExtractor.definePostCode(items[5], items[1], items[4]);
	}

	public void extractMvcrAddresses(String string) {
		// TODO Auto-generated method stub
		
	}

	public void extractPostCodes(String string) {
		// TODO Auto-generated method stub
		
	}
}
