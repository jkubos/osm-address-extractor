package cz.nalezen.osm.extractor.osm;

import java.util.ArrayList;

import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

public class OsmEntities {

	public static class DistrictData {
		public String name;
		Geometry boundary;
		ArrayList<RelationMember> osmShape = new ArrayList<>();
	}

	public static class CityData {
		public String name;
		public Geometry boundary;
		public ArrayList<RelationMember> osmShape = new ArrayList<>();
	}

	public static class StreetData {
		public String name;
		public Geometry path;
		public ArrayList<WayNode> osmNodes = new ArrayList<>();
	}

	public static class AddressData {
		public String mainNumber;
		public String auxNumber;
		
		public Point position;
		public ArrayList<WayNode> osmNodes = new ArrayList<>();
		public String streetName;		
	}

}
