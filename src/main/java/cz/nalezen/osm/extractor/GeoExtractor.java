package cz.nalezen.osm.extractor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateList;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.operation.linemerge.LineSequencer;

public class GeoExtractor {
	
	public static class CityData implements Comparable<CityData> {
		public String name;
		public int administrativeLevel;
		public Geometry boundary;
		public ArrayList<StreetData> streets = new ArrayList<>();
		ArrayList<RelationMember> osmShape = new ArrayList<>();

		@Override
		public int compareTo(CityData o) {
			return name.compareTo(o.name);
		}
	}
	
	public static class StreetData {
		public String name;
		public Geometry path;
		ArrayList<WayNode> osmNodes = new ArrayList<>();
	}
	
	private EntitiesLookup lookup = new EntitiesLookup();
	
	private ArrayList<CityData> cities = new ArrayList<>();
	private ArrayList<StreetData> streets = new ArrayList<>();
	
	private int passNumber = 0;
	
	private GeometryFactory gf = new GeometryFactory();
	
	public GeoExtractor() {
		
		lookup.addHandler(new EntitiesLookup.EntityHandler() {
			@Override
			public void handle(Entity entity) {
				if (entity instanceof Way) {
					Way way = (Way) entity;
					
					for (WayNode wn : way.getWayNodes()) {
						lookup.requestLookup(wn.getNodeId());
					}
				}
			}
		});
		
	}
	
	public ArrayList<CityData> getExtractedCities() {
		return cities;
	}
	
	public void reset() {
		lookup.reset();
		
		cities.clear();
		streets.clear();
		
		passNumber = 0;
	}
	
	public int getPassNumber() {
		return passNumber;
	}
	
	public boolean needsAnotherPass() {
		return passNumber==0 || lookup.havePendingRequests();
	}
	
	public void passStart() {
		lookup.newRound();
	}
	
	public void passDone() {
		if (!needsAnotherPass()) {
			buildCityBoundaries();
			buildStreetsPaths();
			linkCitiesAndStreets();
		}
		
		++passNumber;
	}

	public void handle(Entity entity) {
		if (passNumber==0) {
			handleCity(entity);
			handleStreet(entity);
		}
		
		lookup.addIfRequested(entity);
	}

	private void handleCity(Entity entity) {
		if (!(entity instanceof Relation)) {
			return;
		}
		
		Relation relation = (Relation) entity;
		
		HashMap<String, String> tags = extractMap(relation.getTags(), false);
		
		if ("administrative".equals(tags.get("boundary"))) {
			int adminLevel = Integer.parseInt(tags.get("admin_level"));
			
			if (adminLevel==8) {
				
				CityData cd = new CityData();
				cd.name = tags.get("name");
				cd.administrativeLevel = adminLevel;
	
				for (RelationMember mem : relation.getMembers()) {
					if (!"outer".equals(mem.getMemberRole())) {
						continue;
					}
					
					cd.osmShape.add(mem);
					lookup.requestLookup(mem.getMemberId());
				}
				
				cities.add(cd);
			}
		}
	}
	
	private void handleStreet(Entity entity) {
		if (!(entity instanceof Way)) {
			return;
		}
		
		Way way = (Way) entity;
		
		HashMap<String, String> tags = extractMap(way.getTags(), false);
		
		if ("residential".equals(tags.get("highway"))) {
			
			String name = tags.get("name");
			
			if (StringUtils.isBlank(name)) {
				return;
			}
			
			StreetData sd = new StreetData();
			sd.name = name;

			for (WayNode wn : way.getWayNodes()) {
				lookup.requestLookup(wn.getNodeId());
				sd.osmNodes.add(wn);
			}
			
			streets.add(sd);
		}
	}

	
	private void buildCityBoundaries() {
		for (CityData cd : cities) {
			LineSequencer seq = new LineSequencer();

			for (RelationMember mem : cd.osmShape) {
				Entity other = lookup.lookup(mem.getMemberId());
				
				if (other==null) {
					continue;
				}
				
				if (!(other instanceof Way)) {
					System.out.println("Not way type ("+other.getClass().getSimpleName()+") boundary in "+cd.name);
					continue;
				}
				
				Way way = (Way) other;
				
				LineString ls = extractLineString(way);
				
				if (ls!=null) {
					seq.add(ls);
				}
			}
			
			Geometry geom = null;
			
			try {
				geom = seq.getSequencedLineStrings();
			} catch (Exception e) {
				System.out.println("Geom exception '"+e.getMessage()+"' for: "+cd.name);
			}
			
			if (geom!=null) {
				CoordinateList list = new CoordinateList(geom.getCoordinates());
				list.closeRing();
				
				LinearRing ring = gf.createLinearRing(list.toCoordinateArray());
	
				cd.boundary = gf.createPolygon(ring);
			} else {
				System.out.println("No geom for: "+cd.name);
			}
			
			cd.osmShape = null;
		}
	}
	
	private void buildStreetsPaths() {
		for (StreetData sd : streets) {
			sd.path = extractLineString(sd.osmNodes);
			sd.osmNodes = null;
		}
	}
	
	private void linkCitiesAndStreets() {
		
		long last = System.currentTimeMillis();
	    int i = 0;
		
		for (CityData cd : cities) {
			
			if (System.currentTimeMillis()-last>1000) {
	    		last = System.currentTimeMillis();
	    		
	    		System.out.println(((i/(double)cities.size())*100)+"% linked");
	    	}
			
			for (StreetData sd : streets) {
				if (sd.path!=null && cd.boundary!=null && cd.boundary.contains(sd.path)) {
					cd.streets.add(sd);
				}
			}
			
			++i;
		}
	}
	
	private LineString extractLineString(List<WayNode> wayNodes) {
		ArrayList<Coordinate> coords = new ArrayList<>();

		for (WayNode wn : wayNodes) {
			Entity wnp = lookup.lookup(wn.getNodeId());
			
			if (wnp==null) {
				continue;
			}
			
			Node node = (Node) wnp;
			
			coords.add(new Coordinate(node.getLongitude(), node.getLatitude()));
		}
		
		if (coords.size()>=2) {
			LineString ls = gf.createLineString(coords.toArray(new Coordinate[coords.size()]));
			return ls;
		}
		
		return null;
	}
	
	private LineString extractLineString(Way way) {
		return extractLineString(way.getWayNodes());
	}
	
	public static HashMap<String, String> extractMap(Collection<Tag> tags, boolean printLog) {
		HashMap<String, String> res = new HashMap<>();
		
		if (printLog) {
			System.out.println("----------");
		}
		
		for (Tag tag : tags) {
			
			if (printLog) {
				System.out.println(tag.getKey()+" -> "+tag.getValue());
			}
			
			res.put(tag.getKey(), tag.getValue());
		}
		
		return res;
	}

	

	
}

/*

private void removeDuplicates(ArrayList<CityData> data) {
	for (int i=1;i<data.size();) {
		if (data.get(i-1).name.equals(data.get(i).name)) {
			if (data.get(i-1).administrativeLevel>data.get(i).administrativeLevel) {
				data.remove(i-1);
			} else {
				data.remove(i);
			}
		} else {
			++i;
		}
	}
}

private void sortCities(ArrayList<CityData> data) {
	Collections.sort(data);
}

@SuppressWarnings("unused")
private void printCities(ArrayList<CityData> data) {
	for (CityData cd : data) {
		System.out.println("-------------------");
		System.out.printf("%s (%d)\n", cd.name, cd.administrativeLevel);
	}
}
*/