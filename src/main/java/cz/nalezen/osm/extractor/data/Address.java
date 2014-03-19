package cz.nalezen.osm.extractor.data;

public class Address {
	private int conscriptionNumber;
	private int streetNumber;
	private PointWgs84 position;
	
	public int getConscriptionNumber() {
		return conscriptionNumber;
	}
	
	public void setConscriptionNumber(int conscriptionNumber) {
		this.conscriptionNumber = conscriptionNumber;
	}
	
	public int getStreetNumber() {
		return streetNumber;
	}
	
	public void setStreetNumber(int streetNumber) {
		this.streetNumber = streetNumber;
	}
	
	public PointWgs84 getPosition() {
		return position;
	}
	
	public void setPosition(PointWgs84 position) {
		this.position = position;
	}	
}
