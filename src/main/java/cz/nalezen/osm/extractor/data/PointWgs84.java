package cz.nalezen.osm.extractor.data;

import com.vividsolutions.jts.geom.Point;

public class PointWgs84 {
	private double longitude;
	private double latitude;
	
	public PointWgs84(Point centroid) {
		longitude = centroid.getX();
		latitude = centroid.getY();
	}
	
	public PointWgs84() {
		longitude = 0;
		latitude = 0;
	}

	public double getLongitude() {
		return longitude;
	}
	
	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}
	
	public double getLatitude() {
		return latitude;
	}
	
	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}
}
