package cz.nalezen.osm.extractor.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import cz.nalezen.osm.extractor.MapUtils;


public class CzechRepublicAddresses {
	private ArrayList<District> districts = new ArrayList<>();
	private HashSet<String> missedOsmStreetNames = new HashSet<>();

	public ArrayList<District> getDistricts() {
		return districts;
	}

	public void setDistricts(ArrayList<District> districts) {
		this.districts = districts;
	}

	public District assureDistrict(String name) {
		String cname = name.trim().toLowerCase();

		for (District district : districts) {
			if (district.getName().equals(cname)) {
				return district;
			}
		}

		District district = new District();
		district.setName(cname);
		districts.add(district);
		
		return district;
	}

	public District lookupDistrict(String name) {
		String cname = name.toLowerCase().trim();
		
		for (District district : districts) {
			if (district.getName().equals(cname)) {
				return district;
			}
		}
		
		return null;
	}

	public HashSet<String> getMissedOsmStreetNames() {
		return missedOsmStreetNames;
	}

	public void setMissedOsmStreetNames(HashSet<String> missedOsmStreetNames) {
		this.missedOsmStreetNames = missedOsmStreetNames;
	}

	public void updateCityUniqeNames() {
		HashMap<String, ArrayList<City>> names = new HashMap<>();
		
		List<District> orderedDistricts = districts.stream().sorted((d1, d2)->d1.getName().compareTo(d2.getName())).collect(Collectors.toList());
		
		districts.forEach(district->{
			district.getCities().forEach(city->{
				String id = MapUtils.stringToId(city.getName());
				names.computeIfAbsent(id, k->new ArrayList<>());
				names.get(id).add(city);
			});
		});
		
		districts.forEach(district->{
			district.getCities().forEach(city->{
				String id = MapUtils.stringToId(city.getName());
				
				if (names.get(id).size()>1) {
					id = id+"-"+orderedDistricts.indexOf(city.getDistrict());
//					System.out.println(id);
				}
				
				city.setUniqeName(id);
			});
		});
	}
}
