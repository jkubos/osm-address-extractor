package cz.nalezen.osm.extractor.osm;

import java.io.File;
import java.io.FileInputStream;

import org.openstreetmap.osmosis.core.task.v0_6.Sink;

import crosby.binary.osmosis.OsmosisReader;
import cz.nalezen.osm.extractor.data.CzechRepublicAddresses;

public class OsmDataLoader {

	private String path;
	private GeoExtractor geoExtractor;
	private CustomSink sink;
	private CzechRepublicAddresses root;

	public OsmDataLoader(CzechRepublicAddresses root, String path) {
		this.root = root;
		this.path = path;
		
		geoExtractor = new GeoExtractor();
		sink = new CustomSink(geoExtractor);
	}
	
	public void load() {
		geoExtractor.reset();

		while (geoExtractor.needsAnotherPass()) {			
			geoExtractor.passStart();
			
			readFile(geoExtractor, sink, path);
			
			geoExtractor.passDone();
		}
		
		AddressTreeLinker atl = new AddressTreeLinker(root, geoExtractor);
		atl.link();
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
}
