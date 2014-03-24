package cz.nalezen.osm.extractor.osm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.operation.buffer.BufferOp;

import cz.nalezen.osm.extractor.data.Address;
import cz.nalezen.osm.extractor.data.City;
import cz.nalezen.osm.extractor.data.CzechRepublicAddresses;
import cz.nalezen.osm.extractor.data.District;
import cz.nalezen.osm.extractor.data.PointWgs84;
import cz.nalezen.osm.extractor.data.Street;
import cz.nalezen.osm.extractor.osm.OsmEntities.AddressData;
import cz.nalezen.osm.extractor.osm.OsmEntities.CityData;
import cz.nalezen.osm.extractor.osm.OsmEntities.DistrictData;
import cz.nalezen.osm.extractor.osm.OsmEntities.StreetData;


public class AddressTreeLinker {

	private GeoExtractor geoExtractor;
	private CzechRepublicAddresses root;
	
	private static final Logger logger = Logger.getLogger(AddressTreeLinker.class);
	
	private Quadtree citiesIndex = new Quadtree();
	private Quadtree streetsIndex = new Quadtree();
	private Quadtree addressesIndex = new Quadtree();
	
	private GeometryFactory gf = new GeometryFactory();
	
	public AddressTreeLinker(CzechRepublicAddresses root, GeoExtractor geoExtractor) {
		this.root = root;
		this.geoExtractor = geoExtractor;
	}
	
	private void indexCities() {
		geoExtractor.getCities().stream().filter(city -> city.boundary!=null).forEach(city -> {
			citiesIndex.insert(city.boundary.getEnvelopeInternal(), city);
		});
	}
	
	private void indexStreets() {
		geoExtractor.getStreets().stream().filter(street -> street.path!=null).forEach(street -> {
			streetsIndex.insert(street.path.getEnvelopeInternal(), street);
		});
	}
	
	private void indexAddresses() {
		
		double delta = appoximateMetersToDegrees(5.0);
		
		geoExtractor.getAddresses().stream().filter(address -> address.position!=null).forEach(address -> {
			
			Envelope geom = BufferOp.bufferOp(address.position, delta).getEnvelopeInternal();
		
			addressesIndex.insert(geom, address);
		});
	}

	private double appoximateMetersToDegrees(double delta) {
		//approx. conversion to degrees
		return delta * 360 / (2*Math.PI * 6400000);
	}

	public void link() {
		
		logger.info("------------- Indexing cities -------------");
		indexCities();
		
		logger.info("------------- Indexing streets -------------");
		indexStreets();
		
		logger.info("------------- Indexing addresses -------------");
		indexAddresses();
		
		logger.info("------------- Linking hierarchically -------------");		
		for (District district : root.getDistricts()) {
			Optional<DistrictData> osmDistrict = geoExtractor.getDistricts().stream().filter(d -> d.name.equals(district.getName())).findFirst();
			
			if (!osmDistrict.isPresent()) {
				logger.warn(String.format("District not found in OSM '%s'", district.getName()));
				continue;
			}
			
			linkCities(district, osmDistrict.get());
		}
	}

	private void linkCities(District district, DistrictData osmDistrict) {
		@SuppressWarnings("unchecked")
		List<CityData> allCities = citiesIndex.query(osmDistrict.boundary.getEnvelopeInternal());		
		List<CityData> innerCities = allCities.stream().filter(city -> osmDistrict.boundary.contains(city.boundary)).collect(Collectors.toList());
		
		for (City city : district.getCities()) {
			Optional<CityData> osmCity = innerCities.stream().filter(c -> c.name.equals(city.getName())).findFirst();
			
			if (!osmCity.isPresent()) {
				logger.warn(String.format("City not found in OSM '%s'", city.getName()));
				continue;
			}
			
			city.setPosition(new PointWgs84(osmCity.get().boundary.getCentroid()));
		
			linkStreets(city, osmCity.get());
			linkAddresses(city.getAddresses(), osmCity.get().boundary);
		}
	}

	private void linkStreets(City city, CityData osmCity) {
		@SuppressWarnings("unchecked")
		List<StreetData> allStreets = streetsIndex.query(osmCity.boundary.getEnvelopeInternal());		
		List<StreetData> innerStreets = allStreets.stream().filter(street -> osmCity.boundary.contains(street.path)).collect(Collectors.toList());
		
		for (Street street : city.getStreets()) {
			List<StreetData> pickedStreets = innerStreets.stream().filter(s -> s.name.equals(street.getName())).collect(Collectors.toList());
			
			Geometry way = null;
			
			if (pickedStreets.size()==1) {
				way = pickedStreets.get(0).path;
			} else if (pickedStreets.size()>1) {
				List<LineString> geoms = pickedStreets.stream().map(s -> (LineString)s.path).collect(Collectors.toList());
				
				way = gf.createMultiLineString(geoms.toArray(new LineString[geoms.size()]));
			} else {
				logger.warn(String.format("Street not found in OSM '%s'", street.getName()));
				continue;
			}
			
			street.setPosition(new PointWgs84(way.getCentroid()));

			linkAddresses(street.getAddresses(), way);
		}
	}

	private void linkAddresses(ArrayList<Address> addresses, Geometry geom) {
		double maxDistance = appoximateMetersToDegrees(200.0);
		
		@SuppressWarnings("unchecked")
		List<AddressData> allAddresses = addressesIndex.query(geom.getEnvelopeInternal());		
		List<AddressData> innerAddresses = allAddresses.stream().filter(address -> geom.distance(address.position)<maxDistance).collect(Collectors.toList());
		
		for (Address address : addresses) {
			//match by conscription number
			List<AddressData> matchedAddresses = innerAddresses.stream().filter(a -> numbersMatch(a, address, true)).collect(Collectors.toList());
			
			if (matchedAddresses.size()>0) {
				address.setPosition(new PointWgs84(matchedAddresses.get(0).position));
			} else {
				//match at least street number
				innerAddresses.stream().filter(a -> numbersMatch(a, address, false)).collect(Collectors.toList());
				
				if (matchedAddresses.size()>0) {
					address.setPosition(new PointWgs84(matchedAddresses.get(0).position));
				} else {
					logger.warn(String.format("Address not found in OSM '%d/%d'", address.getStreetNumber(), address.getConscriptionNumber()));
				}
			}
		}
	}

	private boolean numbersMatch(AddressData addrData, Address addr, boolean strict) {
		if (addrData.conscriptionNumber>0 && addrData.conscriptionNumber==addr.getConscriptionNumber()) {
			return true;
		}
		
		if (!strict) {
			if (addrData.streetNumber>0 && addrData.streetNumber==addr.getStreetNumber()) {
				return true;
			}
		}
		
		return false;
	}
}
