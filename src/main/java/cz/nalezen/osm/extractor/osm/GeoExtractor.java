package cz.nalezen.osm.extractor.osm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
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
import com.vividsolutions.jts.operation.buffer.BufferOp;
import com.vividsolutions.jts.operation.linemerge.LineSequencer;

import cz.nalezen.osm.extractor.osm.OsmEntities.AddressData;

public class GeoExtractor {
	
	private EntitiesLookup lookup = new EntitiesLookup();
	
	private ArrayList<OsmEntities.DistrictData> districts = new ArrayList<>();
	private ArrayList<OsmEntities.CityData> cities = new ArrayList<>();
	private ArrayList<OsmEntities.StreetData> streets = new ArrayList<>();
	private ArrayList<OsmEntities.AddressData> addresses = new ArrayList<>();
	
	private int passNumber = 0;
	
	private GeometryFactory gf = new GeometryFactory();

	private static final Logger logger = Logger.getLogger(GeoExtractor.class);
	
	public GeoExtractor() {
		
		//when way is loaded, request load of its points
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
	
	public ArrayList<OsmEntities.DistrictData> getDistricts() {
		return districts;
	}

	public ArrayList<OsmEntities.CityData> getCities() {
		return cities;
	}

	public ArrayList<OsmEntities.StreetData> getStreets() {
		return streets;
	}

	public ArrayList<OsmEntities.AddressData> getAddresses() {
		return addresses;
	}
	
	public ArrayList<OsmEntities.CityData> getExtractedCities() {
		return cities;
	}
	
	public void reset() {
		lookup.reset();
		
		districts.clear();
		cities.clear();
		streets.clear();
		addresses.clear();
		
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
		logger.info("------------- Starting OSM pass #"+passNumber+" -------------");
	}
	
	public void passDone() {
		if (!needsAnotherPass()) {
			logger.info("------------- Build districts boundaries -------------");
			buildDistrictBoundaries();
			
			logger.info("------------- Build cities boundaries -------------");
			buildCityBoundaries();
			
			logger.info("------------- Build streets paths -------------");
			buildStreetsPaths();
			
			logger.info("------------- Build addresses positions -------------");
			buildAddressesPositions();
		}
		
		++passNumber;
	}
	
	public void handle(Entity entity) {
		if (passNumber==0) {
			handleAdministrativeBoundary(entity);
			handleStreet(entity);
			handleAddress(entity);
		}
		
		lookup.addIfRequested(entity);
	}

	private void handleAdministrativeBoundary(Entity entity) {
		if (!(entity instanceof Relation)) {
			return;
		}
		
		Relation relation = (Relation) entity;
		
		HashMap<String, String> tags = extractMap(relation.getTags(), false);
		
		if ("administrative".equals(tags.get("boundary"))) {
			int adminLevel = Integer.parseInt(tags.get("admin_level"));
			
			if (adminLevel==8) {
				extractCity(relation, tags);
			} else if (adminLevel==7) {
				extractDistrict(relation, tags);
			}
		}
	}

	private void extractDistrict(Relation relation, HashMap<String, String> tags) {
		OsmEntities.DistrictData dd = new OsmEntities.DistrictData();
		dd.name = tags.get("name").replaceAll("okres ", "").toLowerCase().trim();

		for (RelationMember mem : relation.getMembers()) {
			
			//only outer region (http://wiki.openstreetmap.org/wiki/Relation#Roles)
			if (!"outer".equals(mem.getMemberRole())) {
				continue;
			}
			
			dd.osmShape.add(mem);
			lookup.requestLookup(mem.getMemberId());
		}
		
		districts.add(dd);
	}

	private void extractCity(Relation relation, HashMap<String, String> tags) {
		if (StringUtils.isBlank(tags.get("name"))) {
			return;
		}
		
		OsmEntities.CityData cd = new OsmEntities.CityData();
		cd.name = tags.get("name").toLowerCase().trim();

		for (RelationMember mem : relation.getMembers()) {
			
			//only outer region (http://wiki.openstreetmap.org/wiki/Relation#Roles)
			if (!"outer".equals(mem.getMemberRole())) {
				continue;
			}
			
			cd.osmShape.add(mem);
			lookup.requestLookup(mem.getMemberId());
		}
		
		cities.add(cd);
	}
	
	private void handleStreet(Entity entity) {
		if (!(entity instanceof Way)) {
			return;
		}
		
		Way way = (Way) entity;
		
		HashMap<String, String> tags = extractMap(way.getTags(), false);
		
		if (tags.containsKey("highway")) {
			
			String name = tags.get("name");
			
			if (StringUtils.isBlank(name)) {
				return;
			}
			
			OsmEntities.StreetData sd = new OsmEntities.StreetData();
			sd.name = name.toLowerCase().trim();

			for (WayNode wn : way.getWayNodes()) {
				lookup.requestLookup(wn.getNodeId());
				sd.osmNodes.add(wn);
			}
			
			streets.add(sd);
		}
	}

	private void handleAddress(Entity entity) {
		HashMap<String, String> tags = extractMap(entity.getTags(), false);
		
		if (tags.containsKey("addr:conscriptionnumber") 
			|| tags.containsKey("addr:streetnumber") 
			|| tags.containsKey("addr:provisionalnumber")
			|| tags.containsKey("addr:housenumber")) {
			
			OsmEntities.AddressData ad = new OsmEntities.AddressData();
			
			fillAddress(ad, 
					tags.get("addr:conscriptionnumber"), 
					tags.get("addr:provisionalnumber"), 
					tags.get("addr:streetnumber"), 
					tags.get("addr:housenumber"));
			
			String streetName = StringUtils.defaultIfBlank(tags.get("addr:street"), "");

			ad.streetName = streetName.trim().toLowerCase();
		
			if (entity instanceof Node) {
				Node node = (Node) entity;
				
				//load position directly
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
	
	//http://wiki.openstreetmap.org/wiki/Cs:WikiProject_Czech_Republic/Address_system
	private void fillAddress(AddressData ad, String conscription, String provisional, String street, String housenumber) {
		if (!StringUtils.isBlank(conscription)) {
			ad.mainNumber = conscription.toLowerCase().trim();
		}

		if (StringUtils.isBlank(ad.mainNumber) && !StringUtils.isBlank(provisional)) {
			ad.mainNumber = provisional.toLowerCase().trim();
		}
		
		if (!StringUtils.isBlank(street)) {
			ad.auxNumber = street.toLowerCase().trim();
		}
		
		if (!StringUtils.isBlank(housenumber) && (StringUtils.isBlank(ad.mainNumber) || StringUtils.isBlank(ad.auxNumber))) {
			Pattern p1 = Pattern.compile("([0-9]+)/([0-9]+[a-z]{0,1})");
			Pattern p2 = Pattern.compile("([0-9]+[a-z]{0,1})");
			Pattern p3 = Pattern.compile("ev ?\\. ?([0-9]+) ?/ ?([0-9]+[a-z]{0,1})");
			Pattern p4 = Pattern.compile("ev ?\\. ?([0-9]+)");
			
			Matcher m1 = p1.matcher(housenumber);
			Matcher m2 = p2.matcher(housenumber);
			Matcher m3 = p3.matcher(housenumber);
			Matcher m4 = p4.matcher(housenumber);
			
			if (m1.matches()) {
				ad.mainNumber = m1.group(1).toLowerCase().trim();
				ad.auxNumber = m1.group(2).toLowerCase().trim();
			} else if (m2.matches() && StringUtils.isBlank(ad.mainNumber)) {
				ad.mainNumber = m2.group(1).toLowerCase().trim();
			} else if (m3.matches() && StringUtils.isBlank(ad.mainNumber)) {
				ad.mainNumber = m3.group(1).toLowerCase().trim();
				ad.auxNumber = m3.group(2).toLowerCase().trim();
			} else if (m4.matches() && StringUtils.isBlank(ad.mainNumber)) {
				ad.mainNumber = m4.group(1).toLowerCase().trim();
			}
		}
	}

	private void buildDistrictBoundaries() {
		for (OsmEntities.DistrictData dd : districts) {
			dd.boundary = extractBoundary(dd.name, dd.osmShape);
			dd.osmShape = null;
		}
	}

	private void buildCityBoundaries() {
		for (OsmEntities.CityData cd : cities) {
			cd.boundary = extractBoundary(cd.name, cd.osmShape);
			cd.osmShape = null;
		}
	}
	
	private void buildStreetsPaths() {
		for (OsmEntities.StreetData sd : streets) {
			sd.path = extractLineString(sd.name, sd.osmNodes);
			sd.osmNodes = null;
		}
	}
	
	private void buildAddressesPositions() {
		for (OsmEntities.AddressData ad : addresses) {
			
			//address already defined (maybe in handleAddess?)
			if (ad.position!=null) {
				continue;
			}
						
			LineString path = extractLineString(ad.streetName, ad.osmNodes);
			
			if (path!=null) {
				ad.position = path.getCentroid();
			}
			
			ad.osmNodes = null;
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
				logger.warn("Not way type ("+other.getClass().getSimpleName()+") boundary in "+name);
				continue;
			}
			
			Way way = (Way) other;
			
			LineString ls = extractLineString(name, way.getWayNodes());
			
			if (ls!=null) {
				seq.add(ls);
			}
		}
		
		Geometry geom = null;
		
		try {
			geom = seq.getSequencedLineStrings();
		} catch (Exception e) {
			logger.warn("Geom exception '"+e.getMessage()+"' for: "+name);
		}
		
		if (geom!=null) {			
			CoordinateList list = new CoordinateList(geom.getCoordinates());
			list.closeRing();
			
			LinearRing ring = gf.createLinearRing(list.toCoordinateArray());

			//cleanup geometry (for sure http://lists.refractions.net/pipermail/jts-devel/2008-May/002466.html)
			Geometry res = BufferOp.bufferOp(gf.createPolygon(ring), 0);
			
			if (res.getArea()<=0.0) {
				logger.warn("Empty geom for: "+name);
				return null;
			}
			
			return res;
		} else {
			logger.warn("No geom for: "+name);
			return null;
		}
	}
	
	private LineString extractLineString(String name, List<WayNode> wayNodes) {
		ArrayList<Coordinate> coords = new ArrayList<>();

		for (WayNode wn : wayNodes) {
			Entity wnp = lookup.lookup(wn.getNodeId());
			
			if (wnp==null) {
				continue;
			}
			
			Node node = (Node) wnp;
			
			coords.add(new Coordinate(node.getLongitude(), node.getLatitude()));
		}
		
		if (coords.size()<2) {
			logger.warn("Empty path for: "+name);
			return null;
		}
		
		LineString ls = gf.createLineString(coords.toArray(new Coordinate[coords.size()]));
		return ls;
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