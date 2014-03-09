package cz.nalezen.osm.extractor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.operation.buffer.BufferOp;
import com.vividsolutions.jts.operation.linemerge.LineSequencer;

public class GeoExtractor {
	
	public static class CityData implements Comparable<CityData> {
		public String name;
		public int administrativeLevel;
		public Geometry boundary;
		public ArrayList<StreetData> streets = new ArrayList<>();
		
		public ArrayList<AddressData> addresses = new ArrayList<>();
		
		ArrayList<RelationMember> osmShape = new ArrayList<>();

		@Override
		public int compareTo(CityData o) {
			return name.compareTo(o.name);
		}
	}
	
	public static class StreetData {
		public String name;
		public Geometry path;
		public ArrayList<AddressData> addresses = new ArrayList<>();
		
		ArrayList<WayNode> osmNodes = new ArrayList<>();
	}
	
	public static class AddressData {
		public String conscriptionNumber;
		public String postCode;
		public String streetName;
		
		public Point position;
		
		ArrayList<WayNode> osmNodes = new ArrayList<>();
	}
	
	private EntitiesLookup lookup = new EntitiesLookup();
	
	private ArrayList<CityData> cities = new ArrayList<>();
	private ArrayList<StreetData> streets = new ArrayList<>();
	private ArrayList<AddressData> addresses = new ArrayList<>();
	
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
			buildAddressesPositions();
			linkCitiesAndStreets();
			linkStreetsAndAddresses();
			
			/*removeCityDuplicates();
			removeStreetDuplicates();*/
		}
		
		++passNumber;
	}

	public void handle(Entity entity) {
		if (passNumber==0) {
			handleCity(entity);
			handleStreet(entity);
			handleAddress(entity);
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

	private void handleAddress(Entity entity) {
		HashMap<String, String> tags = extractMap(entity.getTags(), false);
		
		if (tags.containsKey("addr:conscriptionnumber")) {
			
			AddressData ad = new AddressData();
			ad.conscriptionNumber = tags.get("addr:conscriptionnumber");
			ad.postCode = tags.get("addr:postcode");
			ad.streetName = tags.get("addr:street");
		
			if (entity instanceof Node) {
				Node node = (Node) entity;
				
				ad.position = gf.createPoint(new Coordinate(node.getLongitude(), node.getLatitude()));
			} else if (entity instanceof Way) {
				Way way = (Way) entity;
				
				for (WayNode wn : way.getWayNodes()) {
					lookup.requestLookup(wn.getNodeId());
					ad.osmNodes.add(wn);
				}
			}
			
			addresses.add(ad);
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
	
	private void buildAddressesPositions() {
		for (AddressData ad : addresses) {
			if (ad.position!=null) {
				continue;
			}
						
			LineString path = extractLineString(ad.osmNodes);
			
			if (path!=null) {
				ad.position = path.getCentroid();
			}
			
			ad.osmNodes = null;
		}
	}
	
	private void linkCitiesAndStreets() {
		
		int addQuadTree;
		
		long last = System.currentTimeMillis();
	    int i = 0;
		
		for (CityData cd : cities) {
			
			if (System.currentTimeMillis()-last>1000) {
	    		last = System.currentTimeMillis();
	    		
	    		System.out.println(((i/(double)cities.size())*100)+"% city-street linked");
	    	}
			
			for (StreetData sd : streets) {
				if (sd.path!=null && cd.boundary!=null && cd.boundary.contains(sd.path)) {
					cd.streets.add(sd);
				}
			}
			
			++i;
		}
	}
	
	private void linkStreetsAndAddresses() {
		
		//50 meters
		double bulgarianRangeBase = 50;
		double bulgarianRange = bulgarianRangeBase * 360 / (2*Math.PI * 6400000);
		
		HashSet<AddressData> usedAddress = new HashSet<>();
		
		Quadtree tree = new Quadtree();
		
		for (AddressData ad : addresses) {
			if (ad.position!=null) {
				
				Coordinate[] coords = new Coordinate[] {
					new Coordinate(ad.position.getX()-bulgarianRange, ad.position.getY()-bulgarianRange),
					new Coordinate(ad.position.getX()+bulgarianRange, ad.position.getY()-bulgarianRange),
					new Coordinate(ad.position.getX()+bulgarianRange, ad.position.getY()+bulgarianRange),
					new Coordinate(ad.position.getX()-bulgarianRange, ad.position.getY()+bulgarianRange),
					
					//close
					new Coordinate(ad.position.getX()-bulgarianRange, ad.position.getY()-bulgarianRange)
				};
				
				tree.insert(gf.createPolygon(coords).getEnvelopeInternal(), ad);
			}
		}
		
		long last = System.currentTimeMillis();
	    int i = 0;
		
		for (StreetData sd : streets) {
			
			if (System.currentTimeMillis()-last>1000) {
	    		last = System.currentTimeMillis();
	    		
	    		System.out.println(((i/(double)streets.size())*100)+"% street-address linked");
	    	}
			
			if (sd.path!=null) {
				
				List coll = tree.query(BufferOp.bufferOp(sd.path, bulgarianRange).getEnvelopeInternal());
				
//				System.out.println(coll.size()+" of "+addresses.size());
				
				for (Object object : coll) {
					AddressData ad = (AddressData) object;
					
					if (sd.path.distance(ad.position)<bulgarianRange) {
						if (ad.streetName!=null && !ad.streetName.toLowerCase().equals(sd.name.toLowerCase())) {
//							System.out.println(ad.streetName+" != "+sd.name);
						} else {
							usedAddress.add(ad);
							sd.addresses.add(ad);
//							System.out.println(sd.name);
						}
					}
				}
				
			}
			
			++i;
		}
		
		for (CityData cd : cities) {
			
			if (cd.boundary==null) {
				continue;
			}
			
			for (Object object : tree.query(cd.boundary.getEnvelopeInternal())) {
				AddressData ad = (AddressData) object;
				
				if (usedAddress.contains(ad)) {
					continue;
				}
				
				if (ad.position!=null && cd.boundary.contains(ad.position)) {
					usedAddress.add(ad);
					cd.addresses.add(ad);
				}
			}
		}
	}
	
	/*private void removeCityDuplicates() {
		for (int i=1;i<cities.size();) {
			if (cities.get(i-1).name.equals(cities.get(i).name)) {
				System.out.println("DELETE CITY "+cities.get(i-1).name);
				
				if (cities.get(i-1).administrativeLevel>cities.get(i).administrativeLevel) {
					cities.remove(i-1);
				} else {
					cities.remove(i);
				}
			} else {
				++i;
			}
		}
	}
	
	private void removeStreetDuplicates() {
		for (CityData cd : cities) {
			for (int i=1;i<cd.streets.size();) {
				if (cd.streets.get(i-1).name.equals(cd.streets.get(i).name)) {
					System.out.println("DELETE STREET "+cd.streets.get(i-1).name);
					
					if (cd.streets.get(i-1).path==null) {
						cd.streets.remove(i-1);
					} else {
						cd.streets.remove(i);
					}
				} else {
					++i;
				}
			}
		}
	}*/
	
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