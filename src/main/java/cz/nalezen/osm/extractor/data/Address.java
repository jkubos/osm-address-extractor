package cz.nalezen.osm.extractor.data;

public class Address {
	private String mainNumber;
	private String auxNumber;
	private PointWgs84 position;
	
	public PointWgs84 getPosition() {
		return position;
	}
	
	public void setPosition(PointWgs84 position) {
		this.position = position;
	}

	public String getMainNumber() {
		return mainNumber;
	}

	public void setMainNumber(String mainNumber) {
		this.mainNumber = mainNumber;
	}

	public String getAuxNumber() {
		return auxNumber;
	}

	public void setAuxNumber(String auxNumber) {
		this.auxNumber = auxNumber;
	}	
}
