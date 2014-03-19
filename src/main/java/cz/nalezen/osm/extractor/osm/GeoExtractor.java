package cz.nalezen.osm.extractor.osm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.annotate.JsonIgnore;
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
	
	public static class Geopoint {
		public double longitude;
		public double latitude;
	}
	
	public static class DistrictData {
		public String name;
		public Geometry boundary;
		
		ArrayList<RelationMember> osmShape = new ArrayList<>();
	}
	
	public static class CityData implements Comparable<CityData> {
		public String name;
		
		public ArrayList<String> postCodes = new ArrayList<>();
		
		@JsonIgnore
		public Geopoint wgs84;
		
		@JsonIgnore
		public Geometry boundary;
		
		public ArrayList<StreetData> streets = new ArrayList<>();
		
		public ArrayList<AddressData> addresses = new ArrayList<>();
		
		@JsonIgnore
		ArrayList<RelationMember> osmShape = new ArrayList<>();

		@Override
		@JsonIgnore
		public int compareTo(CityData o) {
			return name.compareTo(o.name);
		}
	}
	
	public static class StreetData {
		public String name;
		
		@JsonIgnore
		public Geopoint wgs84;
		
		@JsonIgnore
		public Geometry path;
		
		public ArrayList<AddressData> addresses = new ArrayList<>();
		
		@JsonIgnore
		ArrayList<WayNode> osmNodes = new ArrayList<>();
	}
	
	public static class AddressData {
		public String conscriptionNumber;
		
		public String streetNumber;
		
		@JsonIgnore
		public Geopoint wgs84;
		
		@JsonIgnore
		public String postCode;
		
		@JsonIgnore
		public String streetName;
		
		@JsonIgnore
		public Point position;
		
		@JsonIgnore
		ArrayList<WayNode> osmNodes = new ArrayList<>();		
	}
	
	private EntitiesLookup lookup = new EntitiesLookup();
	
	private ArrayList<DistrictData> districts = new ArrayList<>();
	private ArrayList<CityData> cities = new ArrayList<>();
	private ArrayList<StreetData> streets = new ArrayList<>();
	private ArrayList<AddressData> addresses = new ArrayList<>();
	
	private HashMap<String, HashSet<String>> postcodeMap = new HashMap<>();
	private HashMap<String, String> postCodeToDistrict = new HashMap<>();
	
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
	
	public void definePostCode(String name, String postCode, String district) {
		
		if (!postcodeMap.containsKey(name)) {
			postcodeMap.put(name, new HashSet<String>());
		}
		
		postcodeMap.get(name).add(postCode);
		
		postCodeToDistrict.put(postCode, district);
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
			buildDistrictBoundaries();
			buildCityBoundaries();
			buildStreetsPaths();
			buildAddressesPositions();
	
			linkCitiesAndStreets();
			linkStreetsAndAddresses();
			
			removeCityDuplicates();
			removeStreetDuplicates();
			
			localizeCities();
			localizeStreets();
			
			extractPostNumber();
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
	
				for (RelationMember mem : relation.getMembers()) {
					if (!"outer".equals(mem.getMemberRole())) {
						continue;
					}
					
					cd.osmShape.add(mem);
					lookup.requestLookup(mem.getMemberId());
				}
				
				cities.add(cd);
			} else if (adminLevel==7) {
				DistrictData dd = new DistrictData();
				dd.name = tags.get("name").replaceAll("okres ", "");
	
				for (RelationMember mem : relation.getMembers()) {
					if (!"outer".equals(mem.getMemberRole())) {
						continue;
					}
					
					dd.osmShape.add(mem);
					lookup.requestLookup(mem.getMemberId());
				}
				
				districts.add(dd);
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
			ad.streetNumber = tags.get("addr:streetnumber");
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
	
	private void buildDistrictBoundaries() {
		for (DistrictData dd : districts) {
			dd.boundary = extractBoundary(dd.name, dd.osmShape);
			dd.osmShape = null;
		}
	}

	private void buildCityBoundaries() {
		for (CityData cd : cities) {
			cd.boundary = extractBoundary(cd.name, cd.osmShape);
			cd.osmShape = null;
		}
	}
	
	private Geometry extractBoundary(String name, ArrayList<RelationMember> osmShape) {
		LineSequencer seq = new LineSequencer();

		for (RelationMember mem : osmShape) {
			Entity other = lookup.lookup(mem.getMemberId());
			
			if (other==null) {
				continue;
			}
			
			if (!(other instanceof Way)) {
				System.out.println("Not way type ("+other.getClass().getSimpleName()+") boundary in "+name);
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
			System.out.println("Geom exception '"+e.getMessage()+"' for: "+name);
		}
		
		if (geom!=null) {
			CoordinateList list = new CoordinateList(geom.getCoordinates());
			list.closeRing();
			
			LinearRing ring = gf.createLinearRing(list.toCoordinateArray());

			//cleanup geometry (for sure http://lists.refractions.net/pipermail/jts-devel/2008-May/002466.html)
			return BufferOp.bufferOp(gf.createPolygon(ring), 0);
		} else {
			System.out.println("No geom for: "+name);
			return null;
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
				
				Geopoint gp = new Geopoint();
				gp.longitude = ad.position.getX();
				gp.latitude = ad.position.getY();
				
				ad.wgs84 = gp;
				
				continue;
			}
						
			LineString path = extractLineString(ad.osmNodes);
			
			if (path!=null) {
				ad.position = path.getCentroid();
				
				Geopoint gp = new Geopoint();
				gp.longitude = ad.position.getX();
				gp.latitude = ad.position.getY();
				
				ad.wgs84 = gp;
			}
			
			ad.osmNodes = null;
		}
	}
	
	private void linkCitiesAndStreets() {
		
//		int addQuadTree;
		
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
				
				List<?> coll = tree.query(BufferOp.bufferOp(sd.path, bulgarianRange).getEnvelopeInternal());
				
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
	
	private void removeCityDuplicates() {
		MultiValueMap dedup = new MultiValueMap();
		
		for (CityData cd : cities) {
			if (cd.name==null) {
				continue;
			}
			
			dedup.put(cd.name.trim(), cd);
		}
		
		cities.clear();
		
		for (Object keyObject : dedup.keySet()) {
			
			Collection<?> coll = dedup.getCollection(keyObject);
			
			if (coll.size()==1) {
				cities.add((CityData) coll.iterator().next());
			} else {
				CityData res = null;
				
				for (Object obj : coll) {
					CityData cd = (CityData) obj;
					
					if (res==null) {
						res = new CityData();
						res.name = cd.name;
						
						cities.add(res);
					}

					res.addresses.addAll(cd.addresses);
					res.streets.addAll(cd.streets);
					
					if (res.boundary==null || (cd.boundary!=null && res.boundary.getArea()<cd.boundary.getArea())) {
						res.boundary = cd.boundary;
					}
				}
			}	
		}
	}
	
	private void removeStreetDuplicates() {
		for (CityData cd : cities) {
			removeStreetDuplicates(cd);
		}
	}
	
	private void removeStreetDuplicates(CityData cd) {
		MultiValueMap dedup = new MultiValueMap();

		for (StreetData sd : cd.streets) {
			if (sd.name==null) {
				continue;
			}
			
			dedup.put(sd.name.trim(), sd);
		}
		
		cd.streets.clear();
		
		for (Object keyObject : dedup.keySet()) {
			
			Collection<?> coll = dedup.getCollection(keyObject);
			
			if (coll.size()==1) {
				cd.streets.add((StreetData) coll.iterator().next());
			} else {
				StreetData res = null;
				
				ArrayList<LineString> ls = new ArrayList<>();
				
				for (Object obj : coll) {
					StreetData sd = (StreetData) obj;
					
					if (res==null) {
						res = new StreetData();
						res.name = sd.name;
						
						cd.streets.add(res);
					}
					
					res.addresses.addAll(sd.addresses);
					
					if (sd.path!=null) {
						ls.add((LineString) sd.path);
					}
				}
				
				res.path = gf.createMultiLineString(ls.toArray(new LineString[ls.size()]));
			}	
		}
	}
	
	private void localizeCities() {
		for (CityData cd : cities) {
			if (cd.boundary!=null) {
				Point centroid = cd.boundary.getCentroid();
				
				if (!centroid.isEmpty()) {
					Geopoint gp = new Geopoint();
					gp.longitude = centroid.getX();
					gp.latitude = centroid.getY();
					
					cd.wgs84 = gp;
				}
			}
		}
	}
	
	private void localizeStreets() {
		for (StreetData sd : streets) {
			if (sd.path!=null) {
				Point centroid = sd.path.getCentroid();
				
				Geopoint gp = new Geopoint();
				gp.longitude = centroid.getX();
				gp.latitude = centroid.getY();
				
				sd.wgs84 = gp;
			}
		}
	}

	private void extractPostNumber() {
		
		long last = System.currentTimeMillis();
	    int i = 0;
	    
		for (CityData cd : cities) {
			if (System.currentTimeMillis()-last>1000) {
	    		last = System.currentTimeMillis();
	    		
	    		System.out.println(((i/(double)cities.size())*100)+"% post numbers extracted");
	    	}
			
			if (postcodeMap.containsKey(cd.name)) {
				for (String postCode : postcodeMap.get(cd.name)) {
					if (correctDistrict(cd.boundary, postCodeToDistrict.get(postCode))) {
						cd.postCodes.add(postCode);
					}
				}				
			}
			
			++i;
			
//			System.out.println(cd.name+" -> "+StringUtils.join(cd.postCodes, ", "));
		}
	}

	private boolean correctDistrict(Geometry boundary, String districtName) {
		for (DistrictData dd : districts) {			
			if (boundary!=null && dd.boundary!=null && dd.boundary.getArea()>0) {
				if (districtName.equals(dd.name) && dd.boundary.contains(boundary)) {
					return true;
				}
			}
		}
				
		return false;
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