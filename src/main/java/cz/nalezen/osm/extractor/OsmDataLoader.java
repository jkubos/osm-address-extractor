package cz.nalezen.osm.extractor;

import cz.nalezen.osm.extractor.data.CzechRepublicAddresses;

public class OsmDataLoader {

	private CzechRepublicAddresses root;
	private String path;

	public OsmDataLoader(CzechRepublicAddresses root, String path) {
		this.root = root;
		this.path = path;
	}
	
	public void load() {
	}
}
