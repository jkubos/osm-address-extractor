package cz.nalezen.osm.extractor.mvcr;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import cz.nalezen.osm.extractor.data.CzechRepublicAddresses;

public class MvcrDataLoader {
	
	private CzechRepublicAddresses root;
	private String path;
	
	public MvcrDataLoader(CzechRepublicAddresses root, String path) {
		this.root = root;
		this.path = path;
	}

	public void load() {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser saxParser = factory.newSAXParser();
			saxParser.parse(path, new MvcrSaxHandler(root));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
